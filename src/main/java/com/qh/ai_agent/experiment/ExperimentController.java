package com.qh.ai_agent.experiment;

import com.qh.ai_agent.cache.AnswerCacheService;
import com.qh.ai_agent.trace.MetricsRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 实验中心：让观众自己点一下，用真实数字验证机制确实生效。
 *
 * 设计原则：
 * 1. 每个实验都清空前置条件 -> 并发触发 -> 统计真实计数 -> 返回"预期 vs 实测"
 * 2. 所有数字来自 MetricsRegistry 的真实计数器，不写死
 * 3. 全部 ADMIN 权限 + 并发上限，防止被滥用
 */
@Slf4j
@RestController
@RequestMapping("/admin/experiments")
@RequiredArgsConstructor
public class ExperimentController {

    private final MetricsRegistry metrics;
    private final AnswerCacheService answerCache;
    private final FaultInjector faultInjector;
    private final com.qh.ai_agent.resilience.CircuitBreaker circuitBreaker;
    private final com.qh.ai_agent.resilience.TokenBucketRateLimiter rateLimiter;

    private static final int MAX_CONCURRENCY = 50;

    /**
     * 实验 1：缓存击穿 / 单飞验证。
     *
     * 清空缓存 -> N 个线程同时问同一个问题 -> 统计 LLM 实际调用次数。
     * 预期：只有 1 个线程真正回源，其余走缓存。
     */
    @PostMapping("/stampede")
    public ResponseEntity<?> stampede(@RequestBody Map<String, Object> body) {
        String query = String.valueOf(body.getOrDefault("query", "退货政策是什么？"));
        int concurrency = Math.min(MAX_CONCURRENCY,
                Integer.parseInt(String.valueOf(body.getOrDefault("concurrency", 20))));

        // 1) 清空缓存，确保从冷启动开始
        answerCache.invalidateAll();
        answerCache.bumpKbVersion();
        long llmBefore = metrics.getLlmCalls();
        long lockBefore = metrics.getLockAcquired();
        long servedBefore = metrics.getLockServedFromCache();

        // 2) 并发起跑
        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(concurrency);
        AtomicInteger cacheHits = new AtomicInteger();
        AtomicInteger realCalls = new AtomicInteger();
        List<Long> latencies = java.util.Collections.synchronizedList(new ArrayList<>());

        long t0 = System.currentTimeMillis();
        for (int i = 0; i < concurrency; i++) {
            pool.submit(() -> {
                try {
                    startGate.await();
                    long s = System.currentTimeMillis();
                    String[] holder = new String[1];
                    String v = answerCache.get(query, () -> {
                        realCalls.incrementAndGet();
                        // 模拟真实回源（这里不调 LLM，避免实验把配额打爆；
                        // 真实链路的回源耗时可从 /answer 端点的实测数据看到）
                        try { Thread.sleep(600); } catch (InterruptedException ignored) { }
                        holder[0] = "simulated-answer";
                        return "simulated-answer";
                    });
                    if (v != null && "simulated-answer".equals(v) && holder[0] == null) {
                        cacheHits.incrementAndGet();
                    }
                    latencies.add(System.currentTimeMillis() - s);
                } catch (Exception e) {
                    log.warn("实验线程异常: {}", e.getMessage());
                } finally {
                    doneGate.countDown();
                }
            });
        }
        startGate.countDown();
        boolean finished;
        try {
            finished = doneGate.await(60, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            finished = false;
        }
        long totalMs = System.currentTimeMillis() - t0;
        pool.shutdownNow();

        long llmAfter = metrics.getLlmCalls();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("experiment", "缓存击穿单飞");
        result.put("requests", concurrency);
        result.put("realBackendCalls", realCalls.get());
        result.put("cacheHits", cacheHits.get());
        result.put("llmCallsDelta", llmAfter - llmBefore);
        result.put("lockAcquiredDelta", metrics.getLockAcquired() - lockBefore);
        result.put("lockServedFromCacheDelta", metrics.getLockServedFromCache() - servedBefore);
        result.put("totalMs", totalMs);
        result.put("serialEstimateMs", concurrency * 600L);
        result.put("finished", finished);
        result.put("expectation", "realBackendCalls = 1（其余走缓存）");
        result.put("pass", realCalls.get() <= 1);
        return ResponseEntity.ok(result);
    }

    /**
     * 实验 2：LLM 降级演练。
     *
     * 注入故障 -> 下一次真实请求应当走降级链而不是报错。
     */
    @PostMapping("/fault")
    public ResponseEntity<?> injectFault(@RequestBody Map<String, Object> body) {
        String target = String.valueOf(body.getOrDefault("target", "llm"));
        int duration = Integer.parseInt(String.valueOf(body.getOrDefault("durationSec", 60)));
        faultInjector.inject(target, duration);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("experiment", "LLM 降级演练");
        r.put("target", target);
        r.put("durationSec", duration);
        r.put("hint", "现在发一条消息，应返回 200 且标记 degraded（降级到 FAQ 直出）");
        r.put("activeFaults", faultInjector.status());
        return ResponseEntity.ok(r);
    }

    @PostMapping("/fault/clear")
    public ResponseEntity<?> clearFault(@RequestBody(required = false) Map<String, Object> body) {
        faultInjector.clearAll();
        return ResponseEntity.ok(Map.of("ok", true, "activeFaults", faultInjector.status()));
    }

    @GetMapping("/fault/status")
    public ResponseEntity<?> faultStatus() {
        return ResponseEntity.ok(Map.of("activeFaults", faultInjector.status()));
    }

    /**
     * 实验 3：缓存命中率（用一批真实问题回放）。
     */
    @PostMapping("/cache-hit-rate")
    public ResponseEntity<?> cacheHitRate(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> queries = (List<String>) body.getOrDefault("queries", List.of());
        if (queries.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "queries 不能为空"));
        }
        long l1Before = metrics.getCacheL1Hits();
        long l2Before = metrics.getCacheL2Hits();
        int hits = 0;
        for (String q : queries) {
            String[] fresh = new String[1];
            answerCache.get(q, () -> { fresh[0] = "x"; return "x"; });
            if (fresh[0] == null) {
                hits++;
            }
        }
        long l1 = metrics.getCacheL1Hits() - l1Before;
        long l2 = metrics.getCacheL2Hits() - l2Before;
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("experiment", "缓存命中率");
        r.put("queries", queries.size());
        r.put("hits", hits);
        r.put("hitRate", queries.isEmpty() ? 0 : (double) hits / queries.size());
        r.put("l1Hits", l1);
        r.put("l2Hits", l2);
        return ResponseEntity.ok(r);
    }

    /** 熔断器 / 限流器状态 */
    @GetMapping("/resilience")
    public ResponseEntity<?> resilience() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("circuitState", circuitBreaker.getState());
        r.put("failureRate", circuitBreaker.getFailureRate());
        r.put("windowTotal", circuitBreaker.getWindowTotal());
        r.put("rejectedByCircuit", circuitBreaker.getRejectedCount());
        r.put("rateLimitRejected", rateLimiter.getRejectedCount());
        r.put("rateLimitBuckets", rateLimiter.getBucketCount());
        return ResponseEntity.ok(r);
    }

    /** 熔断器控制：reset / open */
    @PostMapping("/resilience/control")
    public ResponseEntity<?> resilienceControl(@RequestBody Map<String, Object> body) {
        String action = String.valueOf(body.getOrDefault("action", "reset"));
        if ("open".equals(action)) {
            circuitBreaker.forceOpen();
        } else {
            circuitBreaker.reset();
        }
        return ResponseEntity.ok(Map.of("ok", true, "circuitState", circuitBreaker.getState()));
    }
}
