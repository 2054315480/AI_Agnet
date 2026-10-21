package com.qh.ai_agent.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Redis 分布式锁（手写版，不使用 Redisson，便于讲清每个细节）。
 *
 * 三个关键点：
 * 1. 加锁用 SET key value NX PX ttl —— 一条命令完成"不存在才设置 + 过期"，
 *    避免 SETNX + EXPIRE 两条命令之间宕机导致永久死锁。
 * 2. value 用 UUID，标识"这把锁是我加的"。
 * 3. 释放用 Lua 脚本先比对 value 再删除 —— 防止 A 的锁超时后 B 拿到锁，A 恢复时误删 B 的锁。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisLock {

    private final StringRedisTemplate redisTemplate;

    private static final String UNLOCK_LUA =
            "if redis.call('GET', KEYS[1]) == ARGV[1] then " +
            "  return redis.call('DEL', KEYS[1]) " +
            "else " +
            "  return 0 " +
            "end";

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT =
            new DefaultRedisScript<>(UNLOCK_LUA, Long.class);

    /**
     * 尝试加锁。
     *
     * @param key      锁 key
     * @param waitMs   最多等待多久（等不到就走降级，绝不让用户死等）
     * @param leaseMs  租期（必须大于最坏业务耗时）
     * @return token，null 表示没拿到
     */
    public String tryLock(String key, long waitMs, long leaseMs) {
        String token = UUID.randomUUID().toString();
        long deadline = System.currentTimeMillis() + waitMs;
        do {
            Boolean ok = redisTemplate.opsForValue()
                    .setIfAbsent(key, token, leaseMs, TimeUnit.MILLISECONDS);
            if (Boolean.TRUE.equals(ok)) {
                return token;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        } while (System.currentTimeMillis() < deadline);
        return null;
    }

    /** 释放锁：只有 value 匹配才删除 */
    public boolean unlock(String key, String token) {
        if (token == null) {
            return false;
        }
        try {
            Long r = redisTemplate.execute(UNLOCK_SCRIPT, List.of(key), token);
            return r != null && r > 0;
        } catch (Exception e) {
            log.warn("[RedisLock] 释放锁失败 key={}: {}", key, e.getMessage());
            return false;
        }
    }
}
