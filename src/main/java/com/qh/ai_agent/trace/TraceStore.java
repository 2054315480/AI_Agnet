package com.qh.ai_agent.trace;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 链路落库。失败不影响主流程（trace 只是观测，不能拖垮业务）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TraceStore {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void save(TraceContext ctx) {
        if (ctx == null) {
            return;
        }
        try {
            Integer totalTokens = toInt(ctx.getMetrics().get("totalTokens"));
            if (totalTokens == null) {
                Integer prompt = toInt(ctx.getMetrics().get("promptTokens"));
                Integer completion = toInt(ctx.getMetrics().get("completionTokens"));
                if (prompt != null && completion != null) {
                    totalTokens = prompt + completion;
                }
            }
            String degraded = ctx.getDegradedReason();
            if (totalTokens == null && degraded == null && hasLlmSpan(ctx)) {
                degraded = "token_usage_unavailable";
            }
            String retrievalDetailJson = null;
            Object detail = ctx.getMetrics().get("retrievalDetail");
            if (detail != null) {
                try {
                    retrievalDetailJson = objectMapper.writeValueAsString(detail);
                } catch (Exception ignore) {
                    // 明细序列化失败不影响 trace 落库
                }
            }
            String dbRefJson = null;
            Object dbRef = ctx.getMetrics().get("dbRef");
            if (dbRef != null) {
                try {
                    dbRefJson = objectMapper.writeValueAsString(dbRef);
                } catch (Exception ignore) {
                }
            }
            jdbcTemplate.update(
                "INSERT INTO agent_trace (trace_id, session_id, user_query, rewritten, intent, status, " +
                "total_ms, total_tokens, degraded_reason, retrieval_detail, db_ref) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?::jsonb,?::jsonb) " +
                "ON CONFLICT (trace_id) DO UPDATE SET retrieval_detail = EXCLUDED.retrieval_detail, " +
                "db_ref = EXCLUDED.db_ref",
                ctx.getTraceId(), ctx.getSessionId(), truncate(ctx.getUserQuery(), 2000),
                truncate(ctx.getRewrittenQuery(), 2000), ctx.getIntent(), ctx.getStatus(),
                (int) ctx.totalMs(), totalTokens, degraded, retrievalDetailJson, dbRefJson);

            for (TraceContext.Span span : ctx.getSpans()) {
                jdbcTemplate.update(
                    "INSERT INTO trace_span (trace_id, span_name, start_ms, duration_ms, status, attributes) " +
                    "VALUES (?,?,?,?,?,?::jsonb)",
                    ctx.getTraceId(), span.getName(), (int) span.getStartOffsetMs(),
                    (int) span.getDurationMs(), span.getStatus(),
                    objectMapper.writeValueAsString(span.getAttributes() == null ? java.util.Map.of() : span.getAttributes()));
            }
        } catch (Exception e) {
            log.warn("[TraceStore] 链路落库失败（不影响业务）: {}", e.getMessage());
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static Integer toInt(Object v) {
        if (v instanceof Number n) {
            return n.intValue();
        }
        return null;
    }

    private static boolean hasLlmSpan(TraceContext ctx) {
        return ctx.getSpans().stream()
                .anyMatch(s -> s.getName() != null && s.getName().startsWith("llm"));
    }
}
