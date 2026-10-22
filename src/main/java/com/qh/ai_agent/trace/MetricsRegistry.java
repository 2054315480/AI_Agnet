package com.qh.ai_agent.trace;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 全局计数器：给"自证条"提供只增不减的真实数字。
 *
 * 设计原则：所有数字必须来自真实运行，不允许写死。
 * 观众可以刷新页面看到数字持续增长，这是"活实例"的直接证据。
 */
@Slf4j
@Component
public class MetricsRegistry {

    /** 可选注入：单测里没有数据库时跳过持久化 */
    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    private final Instant startedAt = Instant.now();

    /** 对话请求总数 */
    private final AtomicLong chatRequests = new AtomicLong();
    /** LLM 实际调用次数（静态：Agent 是 new 出来的，拿不到 Spring Bean） */
    private static final AtomicLong LLM_CALLS = new AtomicLong();
    /** 缓存 L1 命中 */
    private final AtomicLong cacheL1Hits = new AtomicLong();
    /** 缓存 L2 命中 */
    private final AtomicLong cacheL2Hits = new AtomicLong();
    /** 分布式锁实际获取次数（防击穿生效的证明） */
    private final AtomicLong lockAcquired = new AtomicLong();
    /** 锁等待后命中缓存次数（单飞省下的 LLM 调用） */
    private final AtomicLong lockServedFromCache = new AtomicLong();
    /** 降级次数 */
    private final AtomicLong degradedCount = new AtomicLong();
    /** 检索总次数 */
    private final AtomicLong retrievalCalls = new AtomicLong();
    /** 重排调用次数（静态：RerankService 可能被非 Bean 调用） */
    private static final AtomicLong RERANK_CALLS = new AtomicLong();

    public void incChatRequests() { chatRequests.incrementAndGet(); }
    public static void incLlmCalls() { LLM_CALLS.incrementAndGet(); }
    public void incCacheL1Hit() { cacheL1Hits.incrementAndGet(); }
    public void incCacheL2Hit() { cacheL2Hits.incrementAndGet(); }
    public void incLockAcquired() { lockAcquired.incrementAndGet(); }
    public void incLockServedFromCache() { lockServedFromCache.incrementAndGet(); }
    public void incDegraded() { degradedCount.incrementAndGet(); }
    public void incRetrievalCalls() { retrievalCalls.incrementAndGet(); }
    public static void incRerankCallsStatic() { RERANK_CALLS.incrementAndGet(); }
    public void incRerankCalls() { RERANK_CALLS.incrementAndGet(); }

    /** 启动时从数据库恢复计数器（重启不清零） */
    @PostConstruct
    public void restore() {
        if (jdbcTemplate == null) {
            return;
        }
        try {
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS metrics_snapshot ("
                    + "name VARCHAR(64) PRIMARY KEY, value BIGINT NOT NULL, "
                    + "updated_at TIMESTAMPTZ DEFAULT now())");
            Map<String, Long> saved = new HashMap<>();
            jdbcTemplate.query("SELECT name, value FROM metrics_snapshot",
                    rs -> { saved.put(rs.getString(1), rs.getLong(2)); });
            if (!saved.isEmpty()) {
                chatRequests.set(saved.getOrDefault("chatRequests", 0L));
                LLM_CALLS.set(saved.getOrDefault("llmCalls", 0L));
                cacheL1Hits.set(saved.getOrDefault("cacheL1Hits", 0L));
                cacheL2Hits.set(saved.getOrDefault("cacheL2Hits", 0L));
                lockAcquired.set(saved.getOrDefault("lockAcquired", 0L));
                lockServedFromCache.set(saved.getOrDefault("lockServedFromCache", 0L));
                degradedCount.set(saved.getOrDefault("degradedCount", 0L));
                retrievalCalls.set(saved.getOrDefault("retrievalCalls", 0L));
                RERANK_CALLS.set(saved.getOrDefault("rerankCalls", 0L));
                log.info("[Metrics] 已从数据库恢复计数器: {}", saved);
            }
        } catch (Exception e) {
            log.warn("[Metrics] 计数器恢复失败（忽略）: {}", e.getMessage());
        }
    }

    /** 每 10 秒把计数器刷到数据库，重启后从库里恢复 */
    @Scheduled(fixedDelay = 10_000, initialDelay = 15_000)
    public void flush() {
        if (jdbcTemplate == null) {
            return;
        }
        try {
            upsert("chatRequests", chatRequests.get());
            upsert("llmCalls", LLM_CALLS.get());
            upsert("cacheL1Hits", cacheL1Hits.get());
            upsert("cacheL2Hits", cacheL2Hits.get());
            upsert("lockAcquired", lockAcquired.get());
            upsert("lockServedFromCache", lockServedFromCache.get());
            upsert("degradedCount", degradedCount.get());
            upsert("retrievalCalls", retrievalCalls.get());
            upsert("rerankCalls", RERANK_CALLS.get());
        } catch (Exception e) {
            log.debug("[Metrics] 计数器落库失败: {}", e.getMessage());
        }
    }

    private void upsert(String name, long value) {
        jdbcTemplate.update(
                "INSERT INTO metrics_snapshot(name, value, updated_at) VALUES (?,?,now()) "
                        + "ON CONFLICT (name) DO UPDATE SET value = EXCLUDED.value, updated_at = now()",
                name, value);
    }

    public Instant getStartedAt() { return startedAt; }
    public long getChatRequests() { return chatRequests.get(); }
    public long getLlmCalls() { return LLM_CALLS.get(); }
    public long getCacheL1Hits() { return cacheL1Hits.get(); }
    public long getCacheL2Hits() { return cacheL2Hits.get(); }
    public long getLockAcquired() { return lockAcquired.get(); }
    public long getLockServedFromCache() { return lockServedFromCache.get(); }
    public long getDegradedCount() { return degradedCount.get(); }
    public long getRetrievalCalls() { return retrievalCalls.get(); }
    public long getRerankCalls() { return RERANK_CALLS.get(); }
}
