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
            List<Map<String, Object>> traces = jdbcTemplate.queryForList(
                    "SELECT * FROM agent_trace WHERE trace_id = ?", traceId);
            if (traces.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("error", "trace 不存在: " + traceId));
            }
            out.put("trace", traces.get(0));
            out.put("spans", jdbcTemplate.queryForList(
                    "SELECT span_name, start_ms, duration_ms, status, attributes " +
                    "FROM trace_span WHERE trace_id = ? ORDER BY start_ms", traceId));
            out.put("costEstimate", costEstimator.estimateForTrace(traceId));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
        return ResponseEntity.ok(out);
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
