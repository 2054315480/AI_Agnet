package com.qh.ai_agent.eval;

import com.qh.ai_agent.knowledge.retriever.HybridRetriever;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 评测服务：用标注集跑检索指标，结果落 eval_run 表，支持版本对比。
 *
 * 判定策略（规则优先，控制成本）：
 * - 引用正确率：纯规则（正则提取出处 + 集合比对），0 成本 100% 可复现
 * - 检索指标：纯计算，无 LLM
 * - 主观质量才考虑 LLM-as-judge（本实现暂不启用，避免评测成本失控）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvalService {

    private final HybridRetriever hybridRetriever;
    private final JdbcTemplate jdbcTemplate;

    private static final Pattern SOURCE_PATTERN = Pattern.compile("\"source\"\s*:\s*\"([^\"]+)\"");

    /** 一条评测样本 */
    public record Sample(String id, String query, List<String> expectedKbIds, String note) {
    }

    /**
     * 跑一次评测。
     *
     * @param runName 版本名（如 baseline-vector / rrf / rrf-rerank）
     * @param samples 样本集
     * @param topK    取前 K 条算指标
     */
    public Map<String, Object> run(String runName, List<Sample> samples, int topK) {
        long t0 = System.currentTimeMillis();
        List<Double> recalls = new ArrayList<>();
        List<Double> mrrs = new ArrayList<>();
        List<Double> ndcgs = new ArrayList<>();
        List<Map<String, Object>> details = new ArrayList<>();

        for (Sample s : samples) {
            List<String> retrieved = new ArrayList<>();
            try {
                hybridRetriever.search(s.query(), topK).forEach(r -> {
                    if (r.getKbId() != null) {
                        retrieved.add(r.getKbId());
                    }
                });
            } catch (Exception e) {
                log.warn("[Eval] 检索失败 sample={}: {}", s.id(), e.getMessage());
            }
            Set<String> expected = new HashSet<>(s.expectedKbIds());
            double recall = RetrievalMetrics.recallAtK(retrieved, expected, topK);
            double mrr = RetrievalMetrics.mrr(retrieved, expected);
            double ndcg = RetrievalMetrics.ndcgAtK(retrieved, expected, topK);
            recalls.add(recall);
            mrrs.add(mrr);
            ndcgs.add(ndcg);

            Map<String, Object> d = new LinkedHashMap<>();
            d.put("id", s.id());
            d.put("query", s.query());
            d.put("retrieved", retrieved);
            d.put("expected", s.expectedKbIds());
            d.put("recall", round(recall));
            d.put("mrr", round(mrr));
            d.put("ndcg", round(ndcg));
            details.add(d);
        }

        double avgRecall = avg(recalls);
        double avgMrr = avg(mrrs);
        double avgNdcg = avg(ndcgs);
        long elapsed = System.currentTimeMillis() - t0;

        // 落库
        try {
            jdbcTemplate.update(
                "INSERT INTO eval_run (run_name, sample_count, recall_at_5, mrr, ndcg_at_5, config_snapshot, created_at) " +
                "VALUES (?,?,?,?,?,?::jsonb, now())",
                runName, samples.size(), avgRecall, avgMrr, avgNdcg,
                "{\"topK\":" + topK + ",\"rrfK\":" + 10 + "}");
        } catch (Exception e) {
            log.warn("[Eval] 结果落库失败（不影响返回）: {}", e.getMessage());
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("runName", runName);
        out.put("sampleCount", samples.size());
        out.put("topK", topK);
        out.put("recallAtK", round(avgRecall));
        out.put("mrr", round(avgMrr));
        out.put("ndcgAtK", round(avgNdcg));
        out.put("elapsedMs", elapsed);
        out.put("details", details);
        log.info("[Eval] run={} samples={} recall@{}={} mrr={} ndcg={} elapsed={}ms",
                runName, samples.size(), topK, round(avgRecall), round(avgMrr), round(avgNdcg), elapsed);
        return out;
    }

    /** 历史评测结果（用于版本对比） */
    public List<Map<String, Object>> history(int limit) {
        try {
            return jdbcTemplate.queryForList(
                "SELECT run_name, sample_count, recall_at_5, mrr, ndcg_at_5, created_at " +
                "FROM eval_run ORDER BY created_at DESC LIMIT ?", limit);
        } catch (Exception e) {
            return List.of();
        }
    }

    private static double avg(List<Double> list) {
        return list.isEmpty() ? 0 : list.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    private static double round(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}
