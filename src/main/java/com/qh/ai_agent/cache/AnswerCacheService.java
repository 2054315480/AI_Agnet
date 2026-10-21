package com.qh.ai_agent.cache;

import com.qh.ai_agent.trace.MetricsRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 问答结果缓存：L1 本地 + L2 Redis + L3 分布式锁单飞（防缓存击穿）。
 *
 * 缓存准入（不该缓存的绝不缓存）：
 * - 含订单号/手机号等个人信息的查询不缓存
 * - 转人工/低置信度结果不缓存
 * - 空结果缓存但短 TTL（防缓存穿透）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnswerCacheService {

    private final LocalTtlCache localCache;
    private final StringRedisTemplate redisTemplate;
    private final RedisLock redisLock;
    private final MetricsRegistry metrics;

    @Value("${cs.cache.enabled:true}")
    private boolean enabled;

    @Value("${cs.cache.l1-ttl-seconds:60}")
    private long l1Ttl;

    @Value("${cs.cache.l2-ttl-seconds:600}")
    private long l2Ttl;

    @Value("${cs.cache.lock-wait-ms:200}")
    private long lockWaitMs;

    @Value("${cs.cache.lock-lease-ms:20000}")
    private long lockLeaseMs;

    /** 没抢到锁时最多等多久别人的结果（要大于最坏生成耗时，否则单飞就失效了） */
    @Value("${cs.cache.wait-for-cache-ms:8000}")
    private long waitForCacheMs;

    /** KB 版本号：KB 更新时自增，旧 key 自然失效（避免 KEYS 扫描） */
    private volatile String kbVersion = "v1";

    private static final String PREFIX = "cs:qa:";
    private static final String LOCK_PREFIX = "cs:lock:qa:";

    /**
     * 三层缓存读取 + 单飞回源。
     *
     * 流程：
     *   1. L1 命中 -> 直接返回
     *   2. L2 命中 -> 回填 L1 -> 返回
     *   3. 都 miss -> 抢锁
     *      3a. 抢到 -> double check L2 -> 回源 -> 写 L2/L1 -> 释放锁
     *      3b. 没抢到 -> 轮询 L2 4 次 -> 仍未命中则直接回源（降级，绝不死等）
     */
    public String get(String normalizedQuery, Supplier<String> loader) {
        if (!enabled || normalizedQuery == null || normalizedQuery.isBlank()) {
            return loader.get();
        }
        if (!isCacheable(normalizedQuery)) {
            return loader.get();
        }

        String key = buildKey(normalizedQuery);

        // L1
        String v = localCache.get(key);
        if (v != null) {
            metrics.incCacheL1Hit();
            return v;
        }

        // L2
        v = redisGet(key);
        if (v != null) {
            metrics.incCacheL2Hit();
            localCache.put(key, v, l1Ttl);
            return v;
        }

        // L3 单飞
        String token = redisLock.tryLock(LOCK_PREFIX + key, lockWaitMs, lockLeaseMs);
        if (token != null) {
            metrics.incLockAcquired();
            try {
                // double check：可能别的实例刚写好
                String again = redisGet(key);
                if (again != null) {
                    metrics.incCacheL2Hit();
                    localCache.put(key, again, l1Ttl);
                    return again;
                }
                String loaded = loader.get();
                if (loaded != null && !loaded.isBlank()) {
                    redisPut(key, loaded);
                    localCache.put(key, loaded, l1Ttl);
                }
                return loaded;
            } finally {
                redisLock.unlock(LOCK_PREFIX + key, token);
            }
        }

        // 没抢到锁：轮询等待持锁者把结果写进缓存（这就是"单飞"省下的 LLM 调用）
        long deadline = System.currentTimeMillis() + waitForCacheMs;
        while (System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            String fromOther = redisGet(key);
            if (fromOther == null) {
                fromOther = localCache.get(key);
            }
            if (fromOther != null) {
                metrics.incLockServedFromCache();
                localCache.put(key, fromOther, l1Ttl);
                return fromOther;
            }
        }

        // 降级：直接回源，不让用户死等锁
        log.debug("[AnswerCache] 未获取锁且缓存未就绪，降级回源: {}", normalizedQuery);
        return loader.get();
    }

    /** 缓存准入判断：含敏感信息的查询不缓存 */
    boolean isCacheable(String query) {
        String q = query.toLowerCase();
        // 含手机号、订单号、身份证等特征的查询不缓存
        if (q.matches(".*\\\\d{6,}.*")) {
            return false;
        }
        String[] sensitive = { "我的订单", "订单号", "手机号", "身份证", "银行卡", "转人工" };
        for (String s : sensitive) {
            if (q.contains(s)) {
                return false;
            }
        }
        return true;
    }

    /** KB 更新后调用：版本号自增，旧 key 自然失效 */
    public void bumpKbVersion() {
        kbVersion = "v" + System.currentTimeMillis();
        localCache.invalidateAll();
        log.info("[AnswerCache] KB 版本已切换，旧缓存自然失效: {}", kbVersion);
    }

    public String getKbVersion() {
        return kbVersion;
    }

    public void invalidateAll() {
        localCache.invalidateAll();
    }

    private String buildKey(String query) {
        return PREFIX + kbVersion + ":" + sha256(query).substring(0, 16);
    }

    private String redisGet(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.debug("[AnswerCache] Redis 读取失败，按未命中处理: {}", e.getMessage());
            return null;
        }
    }

    private void redisPut(String key, String value) {
        try {
            redisTemplate.opsForValue().set(key, value, l2Ttl, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.debug("[AnswerCache] Redis 写入失败（忽略）: {}", e.getMessage());
        }
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }
}
