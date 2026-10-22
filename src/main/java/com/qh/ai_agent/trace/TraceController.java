package com.qh.ai_agent.trace;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 链路查询接口：把落库的 trace / span 暴露出来，并支持证据快照导出。
 */
@RestController
@RequestMapping("/admin/trace")
@RequiredArgsConstructor
public class TraceController {

    private final JdbcTemplate jdbcTemplate;
    private final CostEstimator costEstimator;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    /** 最近的 trace 列表 */
    @GetMapping("/recent")
    public ResponseEntity<?> recent(@RequestParam(defaultValue = "10") int limit) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT trace_id, session_id, user_query, intent, status, total_ms, " +
                    "total_tokens, degraded_reason, created_at FROM agent_trace " +
                    "ORDER BY created_at DESC LIMIT ?", Math.min(limit, 100));
            return ResponseEntity.ok(rows);
        } catch (Exception e) {
            return ResponseEntity.ok(List.of());
        }
    }

    /** 单条 trace 的 span 明细（瀑布图数据）。费用仅在此处按 token 现算，不落表。 */
    @GetMapping("/{traceId}")
    public ResponseEntity<?> detail(@PathVariable String traceId) {
        Map<String, Object> out = new LinkedHashMap<>();
        try {
            // jsonb 列用 ::text 取出，避免 JDBC 返回 PGobject 导致前端拿到 {type,value}
            List<Map<String, Object>> traces = jdbcTemplate.queryForList(
                    "SELECT trace_id, session_id, user_query, rewritten, intent, status, total_ms, " +
                    "total_tokens, degraded_reason, created_at, " +
                    "retrieval_detail::text AS retrieval_detail, db_ref::text AS db_ref " +
                    "FROM agent_trace WHERE trace_id = ?", traceId);
            if (traces.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("error", "trace 不存在: " + traceId));
            }
            Map<String, Object> trace = traces.get(0);
            List<Map<String, Object>> spans = jdbcTemplate.queryForList(
                    "SELECT span_name, start_ms, duration_ms, status, attributes " +
                    "FROM trace_span WHERE trace_id = ? ORDER BY start_ms", traceId);
            out.put("trace", trace);
            out.put("spans", spans);
            // 9 跳链路：把 trace / retrieval_detail / db_ref / spans 组装成前端可直接渲染的结构
            out.put("hops", buildHops(trace, spans));
            out.put("costEstimate", costEstimator.estimateForTrace(traceId));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
        return ResponseEntity.ok(out);
    }

    /**
     * 组装 9 跳链路。
     *
     * 数据来源：agent_trace（query/rewritten/intent/tokens/retrieval_detail/db_ref）+ trace_span（耗时）。
     * 检索内部的 BM25/向量/RRF/精排 是并行执行、没有单独 span，这里如实标注 durationMs=0，
     * 并把整段检索耗时放在 retrieveSpanMs 上，不做假拆分。
     */
    private List<Map<String, Object>> buildHops(Map<String, Object> trace,
                                                List<Map<String, Object>> spans) {
        List<Map<String, Object>> hops = new java.util.ArrayList<>();
        Map<String, Object> detail = asMap(trace.get("retrieval_detail"));
        Map<String, Object> dbRef = asMap(trace.get("db_ref"));

        long retrieveMs = spanMs(spans, "retrieve");
        long llmMs = spanMs(spans, "llm");
        long intentMs = spanMs(spans, "intent");

        hops.add(hop(1, "用户提问", "session=" + trace.get("session_id"),
                intentMs, str(trace.get("user_query")),
                mapOf("sessionId", trace.get("session_id"), "query", trace.get("user_query")), 0));

        hops.add(hop(2, "查询改写",
                sameOrChanged(trace.get("user_query"), trace.get("rewritten")),
                0, str(trace.get("rewritten")),
                mapOf("original", trace.get("user_query"), "rewritten", trace.get("rewritten")), 0));

        hops.add(hop(3, "BM25 检索", "候选 " + num(detail.get("bm25Candidates")) + " 条",
                spanMs(spans, "bm25"), "", mapOf("candidates", detail.get("bm25Candidates"),
                        "path", detail.get("path"), "ms", detail.get("bm25Ms")),
                spanStart(spans, "bm25")));

        hops.add(hop(4, "向量检索",
                "候选 " + num(detail.get("vectorCandidates")) + " 条，阈值 " + num(detail.get("threshold")),
                spanMs(spans, "vector"), "", mapOf("candidates", detail.get("vectorCandidates"),
                        "threshold", detail.get("threshold"), "ms", detail.get("vectorMs")),
                spanStart(spans, "vector")));

        hops.add(hop(5, "RRF 融合",
                "k=" + num(detail.get("rrfK")) + "，融合 " + num(detail.get("fused")) + " 条",
                spanMs(spans, "rrf"), "", mapOf("rrfK", detail.get("rrfK"), "fused", detail.get("fused"),
                        "kgEnabled", detail.get("kgEnabled"), "retrieveSpanMs", retrieveMs),
                spanStart(spans, "rrf")));

        hops.add(hop(6, "精排",
                num(detail.get("rerankIn")) + " → " + num(detail.get("rerankOut")),
                spanMs(spans, "rerank"), "", mapOf("in", detail.get("rerankIn"),
                        "out", detail.get("rerankOut")),
                spanStart(spans, "rerank")));

        hops.add(hop(7, "LLM 调用",
                "total " + num(trace.get("total_tokens")) + " tokens",
                llmMs, "", mapOf("model", "qwen-plus", "totalTokens", trace.get("total_tokens"),
                        "degraded", trace.get("degraded_reason")), spanStart(spans, "llm")));

        hops.add(hop(8, "落库",
                dbRef.isEmpty() ? "未记录" : "messages id=" + dbRef.get("assistantMessageId"),
                0, "", dbRef, 0));

        hops.add(hop(9, "响应",
                str(trace.get("status")) + "，" + spans.size() + " 个 span",
                0, "", mapOf("status", trace.get("status"), "spanCount", spans.size(),
                        "totalMs", trace.get("total_ms")), 0));
        return hops;
    }

    private Map<String, Object> hop(int seq, String name, String detail, long durationMs,
                                    String content, Map<String, Object> data, long startMs) {
        Map<String, Object> h = new LinkedHashMap<>();
        h.put("seq", seq);
        h.put("name", name);
        h.put("detail", detail);
        h.put("durationMs", durationMs);
        h.put("startMs", startMs);
        h.put("content", content);
        h.put("data", data);
        h.put("status", "ok");
        return h;
    }

    private static Map<String, Object> mapOf(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object v) {
        if (v instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            m.forEach((k, val) -> out.put(String.valueOf(k), val));
            return out;
        }
        if (v instanceof String s && !s.isBlank()) {
            try {
                return new com.fasterxml.jackson.databind.ObjectMapper().readValue(s, Map.class);
            } catch (Exception ignore) {
                return Map.of();
            }
        }
        return Map.of();
    }

    private static long spanStart(List<Map<String, Object>> spans, String name) {
        for (Map<String, Object> sp : spans) {
            if (name.equals(String.valueOf(sp.get("span_name")))) {
                Object s = sp.get("start_ms");
                if (s instanceof Number n) {
                    return n.longValue();
                }
            }
        }
        return 0;
    }

    private static long spanMs(List<Map<String, Object>> spans, String name) {
        for (Map<String, Object> sp : spans) {
            if (name.equals(String.valueOf(sp.get("span_name")))) {
                Object d = sp.get("duration_ms");
                if (d instanceof Number n) {
                    return n.longValue();
                }
            }
        }
        return 0;
    }

    private static String sameOrChanged(Object a, Object b) {
        String x = str(a);
        String y = str(b);
        if (y.isEmpty() || x.equals(y)) {
            return "未改写";
        }
        return "已改写";
    }

    private static String num(Object v) {
        return v == null ? "-" : String.valueOf(v);
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    /** 把一条 trace 的链路导出为 Markdown 证据快照。 */
    @GetMapping("/{traceId}/export")
    public ResponseEntity<?> export(@PathVariable String traceId,
                                    @RequestParam(defaultValue = "md") String format) {
        if (!"md".equalsIgnoreCase(format)) {
            return ResponseEntity.badRequest().body(Map.of("error", "仅支持 format=md"));
        }
        try {
            List<Map<String, Object>> traces = jdbcTemplate.queryForList(
                    "SELECT * FROM agent_trace WHERE trace_id = ?", traceId);
            if (traces.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("error", "trace 不存在: " + traceId));
            }
            List<Map<String, Object>> spans = jdbcTemplate.queryForList(
                    "SELECT span_name, start_ms, duration_ms, status, attributes::text AS attributes " +
                    "FROM trace_span WHERE trace_id = ? ORDER BY start_ms", traceId);

            String md = buildTraceMarkdown(traceId, traces.get(0), spans);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(new MediaType("text", "markdown", StandardCharsets.UTF_8));
            headers.set(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"trace-" + safeName(traceId) + ".md\"");
            return new ResponseEntity<>(md, headers, HttpStatus.OK);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    private String buildTraceMarkdown(String traceId, Map<String, Object> trace,
                                      List<Map<String, Object>> spans) {
        StringBuilder md = new StringBuilder();
        String exportedAt = OffsetDateTime.now().format(TIME_FMT);
        md.append("# 证据快照（Trace Export）\n\n");
        md.append("> **水印**：traceId=`").append(traceId)
                .append("` ｜ 导出时间=").append(exportedAt).append("\n");
        md.append("> 本文件由 /admin/trace/{traceId}/export 生成，内容来自 agent_trace / trace_span 落库数据。\n\n");

        md.append("## 概要\n\n");
        md.append("| 字段 | 值 |\n| --- | --- |\n");
        md.append("| traceId | ").append(traceId).append(" |\n");
        md.append("| userQuery | ").append(cell(trace.get("user_query"))).append(" |\n");
        md.append("| intent | ").append(cell(trace.get("intent"))).append(" |\n");
        md.append("| status | ").append(cell(trace.get("status"))).append(" |\n");
        md.append("| totalMs | ").append(cell(trace.get("total_ms"))).append(" |\n");
        md.append("| totalTokens | ").append(cell(trace.get("total_tokens"))).append(" |\n");
        md.append("| degradedReason | ").append(cell(trace.get("degraded_reason"))).append(" |\n");
        md.append("| createdAt | ").append(cell(trace.get("created_at"))).append(" |\n\n");

        md.append("## 链路明细（").append(spans.size()).append(" 跳）\n\n");
        int i = 1;
        for (Map<String, Object> span : spans) {
            md.append("### ").append(i++).append(". ").append(cell(span.get("span_name"))).append("\n\n");
            md.append("- 耗时：").append(cell(span.get("duration_ms"))).append(" ms\n");
            md.append("- 起始偏移：").append(cell(span.get("start_ms"))).append(" ms\n");
            md.append("- 状态：").append(cell(span.get("status"))).append("\n");
            md.append("- attributes：`").append(cell(span.get("attributes"))).append("`\n\n");
        }
        md.append("---\n\n");
        md.append("> 水印复核：traceId=`").append(traceId)
                .append("` ｜ exportedAt=").append(exportedAt).append("\n");
        return md.toString();
    }

    private static String cell(Object v) {
        if (v == null) {
            return "";
        }
        return String.valueOf(v).replace("|", "\\|").replace("\n", " ");
    }

    private static String safeName(String s) {
        return s == null ? "unknown" : s.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
