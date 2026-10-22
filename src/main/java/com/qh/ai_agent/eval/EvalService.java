package com.qh.ai_agent.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qh.ai_agent.knowledge.retriever.HybridRetriever;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

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

    private static final ObjectMapper JSON = new ObjectMapper();

    /** 一条评测样本。note 中保留难度、标注来源等信息，避免额外改表。 */
    public record Sample(String id, String query, List<String> expectedKbIds, String note,
                         List<String> expectedSources) {

        /** 兼容旧调用（自动集没有 expectedSources） */
        public Sample(String id, String query, List<String> expectedKbIds, String note) {
            this(id, query, expectedKbIds, note, List.of());
        }
    }

    /** 兼容旧调用：手工传入样本。 */
    public Map<String, Object> run(String runName, List<Sample> samples, int topK) {
        return run(runName, samples, topK, "manual", "request");
    }

    /**
     * 跑一次评测。
     *
     * @param runName       版本名（如 baseline-vector / rrf / rrf-rerank）
     * @param samples       样本集
     * @param topK          取前 K 条计算指标
     * @param datasetType   auto / labeled / manual，报告必须显式展示
     * @param datasetSource 数据集来源（文件路径或 knowledge_base）
     */
    public Map<String, Object> run(String runName, List<Sample> samples, int topK,
                                   String datasetType, String datasetSource) {
        long t0 = System.currentTimeMillis();
        List<Double> recalls = new ArrayList<>();
        List<Double> mrrs = new ArrayList<>();
        List<Double> ndcgs = new ArrayList<>();
        List<Map<String, Object>> details = new ArrayList<>();

        String effectiveType = (datasetType == null || datasetType.isBlank()) ? "unknown" : datasetType;
        String effectiveSource = (datasetSource == null || datasetSource.isBlank()) ? "unknown" : datasetSource;

        for (Sample s : samples) {
            List<String> retrieved = new ArrayList<>();
            List<String> retrievedSources = new ArrayList<>();
            try {
                hybridRetriever.search(s.query(), topK).forEach(r -> {
                    if (r.getKbId() != null) {
                        retrieved.add(r.getKbId());
                    }
                    if (r.getSourceFile() != null) {
                        retrievedSources.add(r.getSourceFile());
                    }
                });
            } catch (Exception e) {
                log.warn("[Eval] 检索失败 sample={}: {}", s.id(), e.getMessage());
            }
            Set<String> expected = new HashSet<>(s.expectedKbIds());
            // 分块口径：top-k 命中的期望块数 / 期望块总数
            double chunkRecall = RetrievalMetrics.recallAtK(retrieved, expected, topK);
            // 文档口径（标注集用）：top-k 里只要有任意一条来自期望文档就算命中。
            // 否则一份 50 块的 xlsx 在 top5 下 recall 上限只有 10%，指标没有解释力。
            double recall = chunkRecall;
            if (s.expectedSources() != null && !s.expectedSources().isEmpty()) {
                boolean hit = retrievedSources.stream().anyMatch(src -> s.expectedSources().stream()
                        .anyMatch(exp -> src.equals(exp) || src.endsWith("/" + exp) || src.contains(exp)));
                recall = hit ? 1.0 : 0.0;
            }
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
            d.put("chunkRecall", round(chunkRecall));
            d.put("retrievedSources", retrievedSources);
            d.put("mrr", round(mrr));
            d.put("ndcg", round(ndcg));
            d.put("note", s.note());
            details.add(d);
        }

        double avgRecall = avg(recalls);
        double avgMrr = avg(mrrs);
        double avgNdcg = avg(ndcgs);
        long elapsed = System.currentTimeMillis() - t0;

        // 落库：eval_run 表结构由 SchemaInitializer 维护，这里不新增列；
        // datasetType / datasetSource 写入 config_snapshot JSONB，history/compare 再反解。
        Long runId = null;
        try {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("topK", topK);
            config.put("rrfK", 10);
            config.put("datasetType", effectiveType);
            config.put("datasetSource", effectiveSource);
            runId = jdbcTemplate.queryForObject(
                    "INSERT INTO eval_run (run_name, sample_count, recall_at_5, mrr, ndcg_at_5, config_snapshot, created_at) " +
                    "VALUES (?,?,?,?,?,?::jsonb, now()) RETURNING id",
                    Long.class,
                    runName, samples.size(), avgRecall, avgMrr, avgNdcg,
                    JSON.writeValueAsString(config));
        } catch (Exception e) {
            log.warn("[Eval] 结果落库失败（不影响返回）: {}", e.getMessage());
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("runId", runId);
        out.put("runName", runName);
        out.put("datasetType", effectiveType);
        out.put("datasetSource", effectiveSource);
        out.put("sampleCount", samples.size());
        out.put("topK", topK);
        out.put("recallAtK", round(avgRecall));
        out.put("mrr", round(avgMrr));
        out.put("ndcgAtK", round(avgNdcg));
        out.put("elapsedMs", elapsed);
        out.put("details", details);
        log.info("[Eval] run={} type={} source={} samples={} recall@{}={} mrr={} ndcg={} elapsed={}ms",
                runName, effectiveType, effectiveSource, samples.size(), topK,
                round(avgRecall), round(avgMrr), round(avgNdcg), elapsed);
        return out;
    }

    /** 历史评测结果（用于版本对比）。保留旧字段名，同时回显数据集口径。 */
    public List<Map<String, Object>> history(int limit) {
        try {
            return jdbcTemplate.queryForList(
                "SELECT id, run_name, sample_count, recall_at_5, mrr, ndcg_at_5, " +
                "COALESCE(config_snapshot->>'datasetType', 'legacy') AS dataset_type, " +
                "COALESCE(config_snapshot->>'datasetSource', 'unknown') AS dataset_source, " +
                "created_at FROM eval_run ORDER BY created_at DESC LIMIT ?", limit);
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * 对比两次 run 的检索指标。差值口径固定为 b - a：
     * 正数表示 b 更好，负数表示 b 更差。
     */
    public Map<String, Object> compare(long a, long b) {
        Map<String, Object> rowA = loadRun(a);
        Map<String, Object> rowB = loadRun(b);
        Map<String, Object> viewA = metricView(rowA);
        Map<String, Object> viewB = metricView(rowB);

        double deltaRecall = ((Number) viewB.get("recallAtK")).doubleValue()
                - ((Number) viewA.get("recallAtK")).doubleValue();
        double deltaMrr = ((Number) viewB.get("mrr")).doubleValue()
                - ((Number) viewA.get("mrr")).doubleValue();
        double deltaNdcg = ((Number) viewB.get("ndcgAtK")).doubleValue()
                - ((Number) viewA.get("ndcgAtK")).doubleValue();

        Map<String, Object> delta = new LinkedHashMap<>();
        delta.put("recallAtK", round(deltaRecall));
        delta.put("mrr", round(deltaMrr));
        delta.put("ndcgAtK", round(deltaNdcg));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("a", viewA);
        out.put("b", viewB);
        out.put("delta", delta);
        out.put("deltaDefinition", "b - a（正数表示 b 更好，负数表示 b 更差）");
        return out;
    }

    private Map<String, Object> loadRun(long id) {
        List<Map<String, Object>> rows;
        try {
            rows = jdbcTemplate.queryForList(
                    "SELECT id, run_name, sample_count, recall_at_5, mrr, ndcg_at_5, " +
                    "COALESCE(config_snapshot->>'datasetType', 'legacy') AS dataset_type, " +
                    "COALESCE(config_snapshot->>'datasetSource', 'unknown') AS dataset_source, " +
                    "created_at FROM eval_run WHERE id = ?", id);
        } catch (Exception e) {
            throw new IllegalStateException("查询评测结果失败: " + e.getMessage(), e);
        }
        if (rows.isEmpty()) {
            throw new NoSuchElementException("评测 run 不存在: id=" + id);
        }
        return rows.get(0);
    }

    private Map<String, Object> metricView(Map<String, Object> row) {
        if (row.get("recall_at_5") == null || row.get("mrr") == null || row.get("ndcg_at_5") == null) {
            throw new IllegalStateException("评测 run id=" + row.get("id") + " 缺少 recall/mrr/ndcg 指标，无法对比");
        }
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("runId", row.get("id"));
        view.put("runName", row.get("run_name"));
        view.put("datasetType", row.get("dataset_type") == null ? "legacy" : row.get("dataset_type"));
        view.put("datasetSource", row.get("dataset_source") == null ? "unknown" : row.get("dataset_source"));
        view.put("sampleCount", row.get("sample_count"));
        view.put("recallAtK", round(((Number) row.get("recall_at_5")).doubleValue()));
        view.put("mrr", round(((Number) row.get("mrr")).doubleValue()));
        view.put("ndcgAtK", round(((Number) row.get("ndcg_at_5")).doubleValue()));
        view.put("createdAt", row.get("created_at"));
        return view;
    }

    private static double avg(List<Double> list) {
        return list.isEmpty() ? 0 : list.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    private static double round(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}
