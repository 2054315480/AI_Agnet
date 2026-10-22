package com.qh.ai_agent.experiment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * P7-3：cs_vector_store 唯一索引的并发幂等实验。
 * 验证目标：在 uq_cs_vector_kb_id（metadata->>'kb_id' 表达式唯一索引）存在时，
 * 多个线程同时插入同一个 kb_id，最终该 kb_id 只能留下 1 行。
 */
@Slf4j
@RestController
@RequestMapping("/admin/experiments")
@RequiredArgsConstructor
public class IdempotencyExperimentController {
    private static final String INDEX_NAME = "uq_cs_vector_kb_id";
    private static final int VECTOR_DIMENSIONS = 1024;
    private static final int MAX_CONCURRENCY = 50;
    private static final int MAX_ROUNDS = 5;

    private static final String INSERT_SQL = """
            INSERT INTO cs_vector_store (id, content, metadata, embedding)
            VALUES (?, ?, ?::jsonb, ?::vector)
            """;

    private static final String COUNT_BY_KB_ID_SQL = """
            SELECT COUNT(*) FROM cs_vector_store WHERE metadata->>'kb_id' = ?
            """;

    private static final String DELETE_BY_KB_ID_SQL = """
            DELETE FROM cs_vector_store WHERE metadata->>'kb_id' = ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ExperimentRunStore experimentRunStore;

    /**
     * POST /admin/experiments/idempotency
     * 请求体可选：{"concurrency":20,"rounds":1}。
     * rounds 表示重复做几轮独立的并发写入验证；每轮使用独立测试 kb_id 并在结束后清理。
     */
    @PostMapping("/idempotency")
    public ResponseEntity<?> idempotency(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> request = body == null ? Map.of() : body;
        int concurrency = clamp(intValue(request, "concurrency", 20), 1, MAX_CONCURRENCY);
        int rounds = clamp(intValue(request, "rounds", 1), 1, MAX_ROUNDS);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("indexName", INDEX_NAME);
        result.put("concurrency", concurrency);
        result.put("rounds", rounds);
        Boolean indexExists = null;
        String indexCheckError = null;
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM pg_indexes WHERE indexname = ?", Integer.class, INDEX_NAME);
            indexExists = count != null && count > 0;
        } catch (Exception e) {
            indexCheckError = e.getMessage();
            log.warn("[IdempotencyExperiment] 查询 pg_indexes 失败: {}", e.getMessage());
        }

        boolean hasIndex = Boolean.TRUE.equals(indexExists);
        result.put("indexExists", hasIndex);

        // 索引不存在时不做任何写入，明确返回失败原因，绝不假装通过。
        if (!hasIndex) {
            result.put("attempted", 0);
            result.put("succeeded", 0);
            result.put("rejected", 0);
            result.put("failed", 0);
            result.put("finalRows", 0);
            result.put("pass", false);
            result.put("reason", indexCheckError != null
                    ? "查询 pg_indexes 失败，无法确认唯一索引是否存在: " + indexCheckError
                    : "唯一索引 " + INDEX_NAME + " 不存在（pg_indexes 未查到），无法验证并发幂等；本次未写入任何测试数据");
            experimentRunStore.save("idempotency", result);
        return ResponseEntity.ok(result);
        }
        String zeroVector = zeroVectorLiteral();
        List<String> testKbIds = new ArrayList<>();
        List<RoundResult> roundResults = new ArrayList<>();
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        String executionError = null;

        long startedAt = System.currentTimeMillis();
        try {
            for (int round = 1; round <= rounds; round++) {
                String kbId = newTestKbId();
                testKbIds.add(kbId);

                RoundStat stat = executeConcurrentRound(concurrency, kbId, zeroVector);
                int rowsAfterRound = countRows(kbId);
                succeeded.addAndGet(stat.succeeded());
                rejected.addAndGet(stat.rejected());
                failed.addAndGet(stat.failed());

                boolean roundPass = stat.allThreadsReady()
                        && stat.finished()
                        && stat.succeeded() == 1
                        && stat.rejected() == concurrency - 1
                        && stat.failed() == 0
                        && rowsAfterRound == 1;
                roundResults.add(new RoundResult(round, kbId, concurrency,
                        stat.succeeded(), stat.rejected(), stat.failed(),
                        rowsAfterRound, stat.elapsedMs(), roundPass));
            }
        } catch (Exception e) {
            executionError = e.getMessage();
            log.error("[IdempotencyExperiment] 并发实验执行异常: {}", e.getMessage(), e);
        } finally {
            // 必须清理：只删除本次实验自己生成的 kb_id，避免误删业务数据。
            CleanupStat cleanup = cleanup(testKbIds);
            result.put("cleanupDeleted", cleanup.deleted());
            result.put("cleanupRemaining", cleanup.remaining());
            if (cleanup.error() != null) {
                result.put("cleanupError", cleanup.error());
            }
        }
        long elapsedMs = System.currentTimeMillis() - startedAt;
        int attempted = concurrency * rounds;
        int finalRows = roundResults.isEmpty()
                ? 0 : roundResults.get(roundResults.size() - 1).rowsAfterRound();
        int cleanupRemaining = ((Number) result.getOrDefault("cleanupRemaining", 0)).intValue();

        List<String> reasons = new ArrayList<>();
        if (executionError != null) {
            reasons.add("执行异常: " + executionError);
        }
        if (failed.get() > 0) {
            reasons.add("非唯一键异常 " + failed.get() + " 次");
        }
        if (succeeded.get() != rounds) {
            reasons.add("成功写入 " + succeeded.get() + " 次，期望每轮恰好 1 次，共 " + rounds + " 次");
        }
        if (rejected.get() != rounds * (concurrency - 1)) {
            reasons.add("唯一键拒绝 " + rejected.get() + " 次，期望 " + (rounds * (concurrency - 1)) + " 次");
        }
        if (roundResults.stream().anyMatch(r -> !r.pass())) {
            reasons.add("存在未满足「每轮结束后仅剩 1 行」的轮次");
        }
        if (cleanupRemaining != 0) {
            reasons.add("清理后仍残留 " + cleanupRemaining + " 行测试数据");
        }

        boolean pass = reasons.isEmpty();
        result.put("attempted", attempted);
        result.put("succeeded", succeeded.get());
        result.put("rejected", rejected.get());
        result.put("failed", failed.get());
        result.put("finalRows", finalRows);
        result.put("elapsedMs", elapsedMs);
        result.put("pass", pass);
        result.put("roundDetails", roundResults);
        result.put("concurrencyNote",
                "所有工作线程先在 readyGate 就绪，再由主线程释放 startGate 同时开跑；"
                        + "唯一键冲突由 PostgreSQL 唯一索引裁决，失败线程按 SQLState=23505 归类为 rejected；"
                        + "JdbcTemplate 的实际连接并发还受连接池 maximumPoolSize 限制，但 INSERT 竞争仍由数据库唯一索引裁决");
        if (!pass) {
            result.put("reason", String.join("；", reasons));
        }
            experimentRunStore.save("idempotency", result);
        return ResponseEntity.ok(result);
    }
    /** 执行一轮并发写入：concurrency 个线程同时 INSERT 同一个 kb_id。 */
    private RoundStat executeConcurrentRound(int concurrency, String kbId, String vectorLiteral) {
        ExecutorService pool = Executors.newFixedThreadPool(concurrency, runnable -> {
            Thread thread = new Thread(runnable,
                    "idem-experiment-" + kbId.substring(Math.max(0, kbId.length() - 8)));
            thread.setDaemon(true);
            return thread;
        });
        CountDownLatch readyGate = new CountDownLatch(concurrency);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(concurrency);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        String metadataJson = "{\"kb_id\":\"" + kbId
                + "\",\"doc_type\":\"IDEMPOTENCY_EXPERIMENT\",\"question\":\"并发幂等写入实验\"}";
        String content = "idempotency experiment row: " + kbId;
        long startedAt = System.currentTimeMillis();
        for (int i = 0; i < concurrency; i++) {
            pool.execute(() -> {
                readyGate.countDown();
                try {
                    // 所有线程在这里等同一道闸门，确保尽量同时发起 INSERT。
                    startGate.await();
                    jdbcTemplate.update(INSERT_SQL, UUID.randomUUID(), content, metadataJson, vectorLiteral);
                    succeeded.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    failed.incrementAndGet();
                } catch (Exception e) {
                    if (isUniqueViolation(e)) {
                        rejected.incrementAndGet();
                    } else {
                        failed.incrementAndGet();
                        log.warn("[IdempotencyExperiment] 非唯一键写入失败: {}", rootMessage(e));
                    }
                } finally {
                    doneGate.countDown();
                }
            });
        }

        boolean allThreadsReady;
        try {
            allThreadsReady = readyGate.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            allThreadsReady = false;
        }
        startGate.countDown();

        boolean finished;
        try {
            finished = doneGate.await(60, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            finished = false;
        }
        pool.shutdownNow();

        return new RoundStat(concurrency, succeeded.get(), rejected.get(), failed.get(),
                allThreadsReady, finished, System.currentTimeMillis() - startedAt);
    }

    /** 判断异常链中是否存在 PostgreSQL 唯一键冲突（SQLState 23505）。 */
    private boolean isUniqueViolation(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof DuplicateKeyException) {
                return true;
            }
            if (current instanceof SQLException sqlException
                    && "23505".equals(sqlException.getSQLState())) {
                return true;
            }
            String message = current.getMessage();
            if (message != null) {
                String lower = message.toLowerCase();
                if (lower.contains("duplicate key") || message.contains(INDEX_NAME)) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }
    private CleanupStat cleanup(List<String> kbIds) {
        int deleted = 0;
        int remaining = 0;
        String error = null;
        for (String kbId : kbIds) {
            try {
                deleted += jdbcTemplate.update(DELETE_BY_KB_ID_SQL, kbId);
            } catch (Exception e) {
                error = rootMessage(e);
                log.error("[IdempotencyExperiment] 删除测试数据失败 kbId={}: {}", kbId, error);
            }
            try {
                remaining += countRows(kbId);
            } catch (Exception e) {
                error = rootMessage(e);
                log.error("[IdempotencyExperiment] 清理后复查失败 kbId={}: {}", kbId, error);
            }
        }
        return new CleanupStat(deleted, remaining, error);
    }

    private int countRows(String kbId) {
        Integer count = jdbcTemplate.queryForObject(COUNT_BY_KB_ID_SQL, Integer.class, kbId);
        return count == null ? 0 : count;
    }

    /** 生成唯一的测试 kb_id，例如 idem_test_1750000000000_ab12cd34。 */
    private String newTestKbId() {
        return "idem_test_" + System.currentTimeMillis() + "_"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    /** 构造 1024 维全零向量字面量；只走数据库写入，不调用 embedding API。 */
    private String zeroVectorLiteral() {
        StringBuilder builder = new StringBuilder(VECTOR_DIMENSIONS * 2 + 2);
        builder.append('[');
        for (int i = 0; i < VECTOR_DIMENSIONS; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append('0');
        }
        builder.append(']');
        return builder.toString();
    }
    private int intValue(Map<String, Object> body, String key, int defaultValue) {
        Object value = body.get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private record RoundStat(int attempted, int succeeded, int rejected, int failed,
                             boolean allThreadsReady, boolean finished, long elapsedMs) {
    }

    private record RoundResult(int round, String kbId, int attempted,
                               int succeeded, int rejected, int failed,
                               int rowsAfterRound, long elapsedMs, boolean pass) {
    }

    private record CleanupStat(int deleted, int remaining, String error) {
    }
}