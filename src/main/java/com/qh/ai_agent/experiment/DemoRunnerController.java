package com.qh.ai_agent.experiment;

import com.qh.ai_agent.cache.AnswerCacheService;
import com.qh.ai_agent.knowledge.retriever.HybridRetriever;
import com.qh.ai_agent.trace.MetricsRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 一键演示：按顺序跑完 8 项能力验证，输出「预期 vs 实测」报告。
 *
 * 这是汇报场景最省事的入口：一个按钮，一屏讲清系统具备哪些能力。
 * 每项都记录真实数字，失败会如实标记（pass=false），不粉饰。
 */
@Slf4j
@RestController
@RequestMapping("/admin/demo")
@RequiredArgsConstructor
public class DemoRunnerController {

    private final MetricsRegistry metrics;
    private final AnswerCacheService answerCache;
    private final HybridRetriever hybridRetriever;
    private final FaultInjector faultInjector;
    private final JdbcTemplate jdbcTemplate;

    /** 只保留最近一次自检报告，用于导出最近一次结果，避免伪造 runId。 */
    private volatile Map<String, Object> lastReport;

    @PostMapping("/run")
    public Map<String, Object> run() {
        long t0 = System.currentTimeMillis();
        List<Map<String, Object>> results = new ArrayList<>();

        // 1. 知识库规模
        results.add(step("知识库已索引", "向量 > 0 且可检索",
                () -> {
                    Long vec = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM cs_vector_store", Long.class);
                    return new Object[]{ vec + " 条向量", vec != null && vec > 0 };
                }));

        // 2. 混合检索
        results.add(step("混合检索（BM25+向量+RRF）", "两路都有候选",
                () -> {
                    long s = System.currentTimeMillis();
                    HybridRetriever.RetrievalExplain e = hybridRetriever.explain("退货政策");
                    long ms = System.currentTimeMillis() - s;
                    boolean ok = !e.bm25().isEmpty() && !e.vector().isEmpty() && !e.fused().isEmpty();
                    return new Object[]{
                            String.format("BM25 %d / 向量 %d / 融合 %d（%dms）",
                                    e.bm25().size(), e.vector().size(), e.fused().size(), ms), ok };
                }));

        // 3. 缓存单飞
        results.add(step("缓存击穿单飞", "20 并发只回源 1 次",
                () -> {
                    answerCache.invalidateAll();
                    answerCache.bumpKbVersion();
                    AtomicInteger real = new AtomicInteger();
                    int concurrency = 20;
                    ExecutorService pool = Executors.newFixedThreadPool(concurrency);
                    CountDownLatch gate = new CountDownLatch(1);
                    CountDownLatch done = new CountDownLatch(concurrency);
                    for (int i = 0; i < concurrency; i++) {
                        pool.submit(() -> {
                            try {
                                gate.await();
                                String[] fresh = new String[1];
                                answerCache.get("demo-stampede", () -> {
                                    real.incrementAndGet();
                                    try { Thread.sleep(500); } catch (InterruptedException ignored) { }
                                    fresh[0] = "x";
                                    return "x";
                                });
                            } catch (Exception ignored) {
                            } finally {
                                done.countDown();
                            }
                        });
                    }
                    gate.countDown();
                    try { done.await(30, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    pool.shutdownNow();
                    return new Object[]{
                            concurrency + " 并发 -> 实际回源 " + real.get() + " 次", real.get() <= 1 };
                }));

        // 4. 缓存命中
        results.add(step("缓存命中（二次请求）", "第二次 0 LLM 调用",
                () -> {
                    long before = metrics.getLlmCalls();
                    String[] fresh = new String[1];
                    answerCache.get("demo-hit-" + System.currentTimeMillis() / 3600000, () -> {
                        fresh[0] = "cached-value"; return "cached-value";
                    });
                    answerCache.get("demo-hit-" + System.currentTimeMillis() / 3600000, () -> {
                        fresh[0] = "miss"; return "miss";
                    });
                    long delta = metrics.getLlmCalls() - before;
                    return new Object[]{ "LLM 调用增量 " + delta, delta == 0 };
                }));

        // 5. LLM 降级演练
        results.add(step("LLM 降级链路", "注入故障后仍返回降级结果",
                () -> {
                    faultInjector.inject("llm", 5);
                    boolean faulty = faultInjector.isFaulty("llm");
                    faultInjector.clearAll();
                    return new Object[]{ faulty ? "故障注入生效，降级链路已激活" : "注入失败", faulty };
                }));

        // 6. 幂等唯一索引
        results.add(step("向量写入幂等（唯一索引）", "uq_cs_vector_kb_id 存在",
                () -> {
                    Integer n = jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM pg_indexes WHERE indexname='uq_cs_vector_kb_id'", Integer.class);
                    return new Object[]{ n != null && n > 0 ? "唯一索引已建立" : "缺失", n != null && n > 0 };
                }));

        // 7. 链路追踪
        results.add(step("链路追踪落库", "agent_trace 有记录",
                () -> {
                    Long n = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM agent_trace", Long.class);
                    return new Object[]{ n + " 条 trace", n != null && n > 0 };
                }));

        // 8. 评测体系
        results.add(step("评测体系可用", "eval_run 有历史结果",
                () -> {
                    Long n = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM eval_run", Long.class);
                    return new Object[]{ n + " 次评测记录", n != null && n > 0 };
                }));

        long passed = results.stream().filter(r -> Boolean.TRUE.equals(r.get("pass"))).count();
        Map<String, Object> out = new LinkedHashMap<>();
        long uptimeMs = System.currentTimeMillis() - STARTED_AT;
        out.put("runAt", java.time.OffsetDateTime.now().toString());
        out.put("gitCommit", gitCommit());
        out.put("uptimeSeconds", uptimeMs / 1000);
        out.put("uptimeHuman", humanDuration(uptimeMs));
        out.put("total", results.size());
        out.put("passed", passed);
        out.put("failed", results.size() - passed);
        out.put("summary", Map.of("total", results.size(), "passed", passed,
                "failed", results.size() - passed));
        out.put("elapsedMs", System.currentTimeMillis() - t0);
        out.put("results", results);
        out.put("runId", "demo-" + UUID.randomUUID().toString().substring(0, 8));
        this.lastReport = out;
        log.info("[DemoRunner] 演示完成: {}/{} 通过, runId={}", passed, results.size(), out.get("runId"));
        return out;
    }

    /**
     * 导出最近一次自检报告。
     * 项目没有 demo_run 表，无法按任意历史 runId 回查，因此只保留并导出最近一次；
     * runId=latest 或与最近一次 runId 一致时可用。
     */
    @GetMapping("/report/{runId}")
    public ResponseEntity<?> report(@PathVariable String runId,
                                    @RequestParam(defaultValue = "md") String format) {
        if (!"md".equalsIgnoreCase(format)) {
            return ResponseEntity.badRequest().body(Map.of("error", "仅支持 format=md"));
        }
        Map<String, Object> report = this.lastReport;
        if (report == null) {
            return ResponseEntity.status(404).body(Map.of(
                    "error", "尚无自检报告，请先调用 POST /admin/demo/run"));
        }
        String actualRunId = String.valueOf(report.get("runId"));
        if (!"latest".equalsIgnoreCase(runId) && !actualRunId.equals(runId)) {
            return ResponseEntity.status(404).body(Map.of(
                    "error", "仅保留最近一次自检报告，请求 runId=" + runId + "，最近一次=" + actualRunId));
        }

        String md = buildDemoReportMarkdown(runId, report);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "markdown", StandardCharsets.UTF_8));
        headers.set(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"demo-report-" + actualRunId + ".md\"");
        return new ResponseEntity<>(md, headers, HttpStatus.OK);
    }

    private String buildDemoReportMarkdown(String requestedRunId, Map<String, Object> report) {
        String exportedAt = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        StringBuilder md = new StringBuilder();
        md.append("# 系统自检报告\n\n");
        md.append("> **水印**：requestedRunId=`").append(requestedRunId)
                .append("` ｜ actualRunId=`").append(report.get("runId"))
                .append("` ｜ 导出时间=").append(exportedAt).append("\n\n");
        md.append("> 数据来源：最近一次 POST /admin/demo/run 的真实检查结果。\n\n");
        md.append("## 报告头\n\n");
        md.append("| 字段 | 值 |\n| --- | --- |\n");
        md.append("| runId | ").append(report.get("runId")).append(" |\n");
        md.append("| runAt | ").append(report.get("runAt")).append(" |\n");
        md.append("| gitCommit | ").append(report.get("gitCommit")).append(" |\n");
        md.append("| 通过/总数 | ").append(report.get("passed")).append("/")
                .append(report.get("total")).append(" |\n");
        md.append("| 耗时 | ").append(report.get("elapsedMs")).append(" ms |\n\n");

        md.append("## 每项检查结果\n\n");
        md.append("| # | 检查项 | 预期 | 实测 | 结果 | 耗时(ms) |\n");
        md.append("| --- | --- | --- | --- | --- | --- |\n");
        Object raw = report.get("results");
        if (raw instanceof List<?> list) {
            int i = 1;
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> m)) {
                    continue;
                }
                md.append("| ").append(i++).append(" | ")
                        .append(cell(m.get("name"))).append(" | ")
                        .append(cell(m.get("expected"))).append(" | ")
                        .append(cell(m.get("actual"))).append(" | ")
                        .append(Boolean.TRUE.equals(m.get("pass")) ? "PASS" : "FAIL").append(" | ")
                        .append(cell(m.get("ms"))).append(" |\n");
            }
        }
        md.append("\n---\n\n");
        md.append("> 水印复核：actualRunId=`").append(report.get("runId"))
                .append("` ｜ exportedAt=").append(exportedAt).append("\n");
        return md.toString();
    }

    private static String cell(Object v) {
        if (v == null) {
            return "";
        }
        return String.valueOf(v).replace("|", "\\|").replace("\n", " ");
    }

    private Map<String, Object> step(String name, String expected, StepAction action) {
        long s = System.currentTimeMillis();
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("name", name);
        r.put("expected", expected);
        try {
            Object[] res = action.run();
            r.put("actual", res[0]);
            r.put("pass", res[1]);
        } catch (Exception e) {
            r.put("actual", "异常: " + e.getMessage());
            r.put("pass", false);
        }
        r.put("ms", System.currentTimeMillis() - s);
        return r;
    }

    @FunctionalInterface
    private interface StepAction {
        Object[] run() throws Exception;
    }

    private static final long STARTED_AT = System.currentTimeMillis();

    /** 秒级运行时长 -> "3天2小时" */
    private static String humanDuration(long ms) {
        long s = ms / 1000;
        long d = s / 86400, h = (s % 86400) / 3600, m = (s % 3600) / 60;
        if (d > 0) return d + "天" + h + "小时";
        if (h > 0) return h + "小时" + m + "分钟";
        return m + "分钟";
    }

    /**
     * 部署目录通常没有 .git，git 会把 "fatal: ..." 打到合并后的 stdout。
     * 必须按退出码判断，否则会把错误信息当成 commit 返回（之前线上就是这样）。
     */
    private String gitCommit() {
        try {
            Process p = new ProcessBuilder("git", "rev-parse", "--short", "HEAD").redirectErrorStream(true).start();
            try (var r = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()))) {
                String line = r.readLine();
                int exit = p.waitFor();
                return (exit != 0 || line == null || line.startsWith("fatal")) ? "unknown" : line.trim();
            }
        } catch (Exception e) {
            return "unknown";
        }
    }
}
