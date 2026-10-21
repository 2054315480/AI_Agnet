package com.qh.ai_agent.trace;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 全局计数器：给"自证条"提供只增不减的真实数字。
 *
 * 设计原则：所有数字必须来自真实运行，不允许写死。
 * 观众可以刷新页面看到数字持续增长，这是"活实例"的直接证据。
 */
@Component
public class MetricsRegistry {

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
