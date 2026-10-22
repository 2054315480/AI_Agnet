package com.qh.ai_agent.trace;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * trace 费用估算。
 *
 * 不新增 agent_trace 列：promptTokens/completionTokens 已写入 llm span.attributes，
 * 查询详情时按 qwen-plus 配置单价现算。
 */
@Component
@RequiredArgsConstructor
public class CostEstimator {

    private final JdbcTemplate jdbcTemplate;

    @Value("${cs.cost.input-per-1k:0.0008}")
    private double inputPer1k;

    @Value("${cs.cost.output-per-1k:0.002}")
    private double outputPer1k;

    public Map<String, Object> estimateForTrace(String traceId) {
        long prompt = 0L;
        long completion = 0L;
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT COALESCE(SUM(CAST(NULLIF(attributes->>'promptTokens','') AS BIGINT)),0) AS prompt_tokens, " +
                    "COALESCE(SUM(CAST(NULLIF(attributes->>'completionTokens','') AS BIGINT)),0) AS completion_tokens " +
                    "FROM trace_span WHERE trace_id = ?", traceId);
            if (!rows.isEmpty()) {
                prompt = longValue(rows.get(0).get("prompt_tokens"));
                completion = longValue(rows.get(0).get("completion_tokens"));
            }
        } catch (Exception e) {
            return unavailable("读取 span token 失败: " + e.getMessage(), null);
        }

        if (prompt <= 0 && completion <= 0) {
            Integer total = null;
            try {
                total = jdbcTemplate.queryForObject(
                        "SELECT total_tokens FROM agent_trace WHERE trace_id = ?", Integer.class, traceId);
            } catch (Exception ignore) {
                // 详情接口已做存在性校验，这里仅用于补充 unavailable 时的 totalTokens 展示
            }
            return unavailable("prompt/completion token 明细不可用（旧 trace 或 provider 未返回 usage）", total);
        }

        BigDecimal inputCost = BigDecimal.valueOf(prompt)
                .divide(BigDecimal.valueOf(1000))
                .multiply(BigDecimal.valueOf(inputPer1k))
                .setScale(6, RoundingMode.HALF_UP);
        BigDecimal outputCost = BigDecimal.valueOf(completion)
                .divide(BigDecimal.valueOf(1000))
                .multiply(BigDecimal.valueOf(outputPer1k))
                .setScale(6, RoundingMode.HALF_UP);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("available", true);
        out.put("currency", "CNY");
        out.put("promptTokens", prompt);
        out.put("completionTokens", completion);
        out.put("totalTokens", prompt + completion);
        out.put("inputPer1k", inputPer1k);
        out.put("outputPer1k", outputPer1k);
        out.put("inputCost", inputCost);
        out.put("outputCost", outputCost);
        out.put("totalCost", inputCost.add(outputCost));
        out.put("formula", "promptTokens/1000*inputPer1k + completionTokens/1000*outputPer1k");
        return out;
    }

    private static Map<String, Object> unavailable(String reason, Integer totalTokens) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("available", false);
        out.put("currency", "CNY");
        out.put("reason", reason);
        out.put("totalTokens", totalTokens);
        return out;
    }

    private static long longValue(Object v) {
        return v instanceof Number n ? n.longValue() : 0L;
    }
}
