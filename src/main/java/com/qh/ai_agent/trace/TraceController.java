package com.qh.ai_agent.trace;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 链路查询接口：把落库的 trace / span 暴露出来。
 *
 * 这是"实时链路面板"之外的事后回放能力：
 * 实时面板证明"正在跑"，这里证明"跑过的都留了痕"。
 */
@RestController
@RequestMapping("/admin/trace")
@RequiredArgsConstructor
public class TraceController {

    private final JdbcTemplate jdbcTemplate;

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

    /** 单条 trace 的 span 明细（瀑布图数据） */
    @GetMapping("/{traceId}")
    public ResponseEntity<?> detail(@PathVariable String traceId) {
        Map<String, Object> out = new LinkedHashMap<>();
        try {
            List<Map<String, Object>> traces = jdbcTemplate.queryForList(
                    "SELECT * FROM agent_trace WHERE trace_id = ?", traceId);
            if (traces.isEmpty()) {
                return ResponseEntity.ok(Map.of("error", "trace 不存在"));
            }
            out.put("trace", traces.get(0));
            out.put("spans", jdbcTemplate.queryForList(
                    "SELECT span_name, start_ms, duration_ms, status, attributes " +
                    "FROM trace_span WHERE trace_id = ? ORDER BY start_ms", traceId));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("error", e.getMessage()));
        }
        return ResponseEntity.ok(out);
    }
}
