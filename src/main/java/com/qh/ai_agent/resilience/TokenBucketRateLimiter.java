package com.qh.ai_agent.resilience;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 令牌桶限流（按用户维度，手写零依赖）。
 *
 * 为什么用令牌桶而不是固定窗口计数器：
 * 固定窗口在窗口边界会有双倍突发（前 1 秒 100 次 + 后 1 秒 100 次 = 2 秒 200 次），
 * 令牌桶按速率匀速补充，天然平滑突发。
 */
@Slf4j
@Component
public class TokenBucketRateLimiter {

    private static class Bucket {
        double tokens;
        long lastRefillMs;
    }

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final AtomicLong rejected = new AtomicLong();

    /** 每秒补充的令牌数（即 QPS 上限） */
    @Value("${cs.ratelimit.qps:10}")
    private double refillPerSecond;

    /** 桶容量（允许的突发量） */
    @Value("${cs.ratelimit.burst:20}")
    private double burstCapacity;

    /** 是否启用 */
    @Value("${cs.ratelimit.enabled:true}")
    private boolean enabled;

    public boolean tryAcquire(String key) {
        if (!enabled) {
            return true;
        }
        String k = key == null || key.isBlank() ? "anonymous" : key;
        Bucket b = buckets.computeIfAbsent(k, x -> {
            Bucket nb = new Bucket();
            nb.tokens = burstCapacity;
            nb.lastRefillMs = System.currentTimeMillis();
            return nb;
        });
        synchronized (b) {
            long now = System.currentTimeMillis();
            double elapsedSec = (now - b.lastRefillMs) / 1000.0;
            b.tokens = Math.min(burstCapacity, b.tokens + elapsedSec * refillPerSecond);
            b.lastRefillMs = now;
            if (b.tokens >= 1.0) {
                b.tokens -= 1.0;
                return true;
            }
            rejected.incrementAndGet();
            return false;
        }
    }

    public long getRejectedCount() {
        return rejected.get();
    }

    public int getBucketCount() {
        return buckets.size();
    }
}
