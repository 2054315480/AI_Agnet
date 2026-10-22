package com.qh.ai_agent.experiment;

import com.qh.ai_agent.knowledge.retriever.Bm25Retriever;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * P7-4：检索并行 vs 串行的 P50/P95 对比实验。
 *
 * <p>串行组：对每条 query 先执行 BM25，再执行向量检索；并行组：用 CompletableFuture
 * 同时执行 BM25 与向量检索。每组重复 samples * rounds 次后统计 P50/P95/均值。</p>
 *
 * <p>成本控制：PgVectorSearchDao 的公开 search(String, ...) 内部会调用 embeddingModel.embed(query)，
 * 无法传入固定向量。为避免 samples*rounds*2 次 embedding 调用，本实验直接复用其同一张表、
 * 同一 pgvector 余弦距离 SQL 和同一相似度阈值，用固定的 1024 维伪随机向量查询，embedding 调用为 0 次。</p>
 *
 * <p>HybridRetriever.search 内部已经用 CompletableFuture 并行执行两路，但它的向量路会调用 embedding API；
 * 因此这里拆出底层 BM25 与 pgvector SQL 单独计时，既能对比串行/并行，又不产生 API 成本。</p>
 */
@Slf4j
@RestController
@RequestMapping("/admin/experiments")
@RequiredArgsConstructor
public class RetrievalBenchController {

    private static final int TOP_K = 10;
    private static final int VECTOR_DIMENSIONS = 1024;
    private static final int MAX_ROUNDS = 5;

    /** 来自 data/data/knowledge_base 下业务 FAQ 的 20 条真实中文问题。 */
    private static final List<String> QUERY_POOL = List.of(
            "我想了解我的退款状态,您能帮我查询一下吗?",
            "我需要在线退货,请协助我。",
            "退款流程在哪里可以查询?",
            "退货需要满足哪些条件?",
            "运费怎么计算,首重多少钱?",
            "北京到上海标准快递几天能到?",
            "运单长时间不更新怎么办?",
            "偏远地区支持次日达吗?",
            "订单支付失败但是扣款了怎么办?",
            "优惠券可以叠加使用吗?"
    );

    private static final List<String> QUERY_POOL_PART2 = List.of(
            "商品有质量问题怎么申请售后?",
            "保修期内维修需要收费吗?",
            "发票怎么开具,可以开电子发票吗?",
            "信用卡账单分期手续费怎么算?",
            "保险理赔需要准备哪些材料?",
            "电信套餐如何变更?",
            "能否代办业务,需要什么材料?",
            "业务办理需要多长时间?",
            "如何查询业务办理进度?",
            "退款多久能到账?"
    );

    private final Bm25Retriever bm25Retriever;
    private final JdbcTemplate jdbcTemplate;
    private final ExperimentRunStore experimentRunStore;

    @Value("${cs.vector.similarity-threshold:0.5}")
    private double similarityThreshold;

    /**
     * POST /admin/experiments/retrieval-bench
     * 请求体可选：{"samples":10,"rounds":2}。samples 表示取前 N 条真实业务问题，rounds 表示重复轮数。
     */
    @PostMapping("/retrieval-bench")
    public ResponseEntity<?> retrievalBench(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> request = body == null ? Map.of() : body;
        List<String> allQueries = new ArrayList<>(QUERY_POOL);
        allQueries.addAll(QUERY_POOL_PART2);
        int samples = clamp(intValue(request, "samples", 10), 1, allQueries.size());
        int rounds = clamp(intValue(request, "rounds", 2), 1, MAX_ROUNDS);

        if (!bm25Retriever.isIndexReady()) {
            return ResponseEntity.status(503).body(Map.of(
                    "error", "BM25 索引未就绪，无法做串行/并行对比；请先完成知识库索引构建"));
        }

        Long vectorRows;
        try {
            vectorRows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM cs_vector_store", Long.class);
        } catch (Exception e) {
            return ResponseEntity.status(503).body(Map.of(
                    "error", "cs_vector_store 不可查询，无法做向量路径对比: " + rootMessage(e)));
        }
        if (vectorRows == null || vectorRows == 0) {
            return ResponseEntity.status(503).body(Map.of(
                    "error", "cs_vector_store 为空，向量路径无真实数据，拒绝输出无意义的耗时对比"));
        }

        List<String> queries = allQueries.subList(0, samples);
        String fixedVector = fixedVectorLiteral();
        // 预热一次，降低 JIT / Lucene DirectoryReader / 连接池首次建连对首样本的污染。
        try {
            runSerial(queries.get(0), fixedVector);
            runParallel(queries.get(0), fixedVector);
        } catch (Exception e) {
            log.warn("[RetrievalBench] 预热失败（不影响后续测量异常上报）: {}", e.getMessage());
        }

        List<Long> serialNanos = new ArrayList<>();
        List<Long> parallelNanos = new ArrayList<>();
        long startedAt = System.currentTimeMillis();
        try {
            for (int round = 1; round <= rounds; round++) {
                for (String query : queries) {
                    long serialStart = System.nanoTime();
                    runSerial(query, fixedVector);
                    serialNanos.add(System.nanoTime() - serialStart);

                    long parallelStart = System.nanoTime();
                    runParallel(query, fixedVector);
                    parallelNanos.add(System.nanoTime() - parallelStart);
                }
            }
        } catch (Exception e) {
            log.error("[RetrievalBench] 基准测试失败: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                    "error", "基准测试执行失败: " + rootMessage(e),
                    "note", "未输出伪造的 P50/P95；请检查 BM25 索引与 cs_vector_store 是否可用"));
        }
        Map<String, Object> serial = stats(serialNanos);
        Map<String, Object> parallel = stats(parallelNanos);
        double serialP50 = ((Number) serial.get("p50Ms")).doubleValue();
        double serialP95 = ((Number) serial.get("p95Ms")).doubleValue();
        double parallelP50 = ((Number) parallel.get("p50Ms")).doubleValue();
        double parallelP95 = ((Number) parallel.get("p95Ms")).doubleValue();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("samples", samples);
        result.put("rounds", rounds);
        result.put("querySource", "data/data/knowledge_base 业务 FAQ + 中文业务场景硬编码");
        result.put("serial", serial);
        result.put("parallel", parallel);
        result.put("speedupP50", round2(parallelP50 > 0 ? serialP50 / parallelP50 : 0));
        result.put("speedupP95", round2(parallelP95 > 0 ? serialP95 / parallelP95 : 0));
        result.put("note",
                "同一进程内对比，未隔离 GC 抖动；BM25 与向量两路分别计时，串行组顺序执行、"
                        + "并行组用 CompletableFuture 同时执行。向量使用固定 1024 维伪随机向量直接复用 "
                        + "PgVectorSearchDao 的 pgvector SQL，embedding 调用 0 次；samples 默认 10、rounds 默认 2。");
        result.put("embeddingCalls", 0);
        result.put("vectorMode", "fixed_1024d_sql");
        result.put("topK", TOP_K);
        result.put("similarityThreshold", similarityThreshold);
        result.put("bm25IndexReady", true);
        result.put("vectorRows", vectorRows);
        result.put("warmupRuns", 1);
        result.put("hybridNote", "HybridRetriever.search 已并行执行两路，但其向量路会调 embedding；本实验拆出底层 BM25 与 pgvector SQL 单独计时");
        result.put("elapsedMs", System.currentTimeMillis() - startedAt);
            experimentRunStore.save("retrieval-bench", result);
        return ResponseEntity.ok(result);
    }
    /** 串行组：先 BM25，再向量 SQL，两段顺序执行。 */
    private void runSerial(String query, String vectorLiteral) {
        bm25Retriever.search(query, TOP_K);
        vectorSearchFixed(vectorLiteral);
    }

    /** 并行组：BM25 与向量 SQL 同时提交，等待两路都完成。 */
    private void runParallel(String query, String vectorLiteral) {
        CompletableFuture<Void> bm25Future = CompletableFuture.runAsync(
                () -> bm25Retriever.search(query, TOP_K));
        CompletableFuture<Void> vectorFuture = CompletableFuture.runAsync(
                () -> vectorSearchFixed(vectorLiteral));
        CompletableFuture.allOf(bm25Future, vectorFuture).join();
    }

    /**
     * 复用 PgVectorSearchDao 的 SQL 形态：同一张表、同一余弦相似度表达式、同一阈值与 topK。
     * PgVectorSearchDao.search(String, ...) 会先调 embedding API，这里改用固定向量以避免成本。
     */
    private void vectorSearchFixed(String vectorLiteral) {
        String sql = """
                SELECT content,
                       COALESCE(metadata->>'kb_id', '') AS kb_id,
                       COALESCE(metadata->>'question', '') AS question,
                       COALESCE(metadata->>'domain', '') AS domain,
                       COALESCE(metadata->>'category', '') AS category,
                       COALESCE(metadata->>'source', '') AS source_file,
                       1 - (embedding <=> ?::vector) AS similarity
                FROM cs_vector_store
                WHERE 1 - (embedding <=> ?::vector) >= ?
                ORDER BY embedding <=> ?::vector
                LIMIT ?
                """;
        List<String> hits = jdbcTemplate.query(sql,
                (rs, rowNum) -> rs.getString("content"),
                vectorLiteral, vectorLiteral, similarityThreshold, vectorLiteral, TOP_K);
        if (hits.isEmpty()) {
            log.debug("[RetrievalBench] 固定向量查询命中 0 行（latency 样本仍有效）");
        }
    }
    /** 生成固定的、已归一化的 1024 维向量，避免 0 向量导致余弦距离 NaN，也避免任何 API 调用。 */
    private String fixedVectorLiteral() {
        float[] vector = new float[VECTOR_DIMENSIONS];
        double sumSquares = 0.0;
        for (int i = 0; i < VECTOR_DIMENSIONS; i++) {
            double value = Math.sin((i + 1) * 0.173) + Math.cos((i + 1) * 0.071);
            vector[i] = (float) value;
            sumSquares += value * value;
        }
        double norm = Math.sqrt(sumSquares);
        StringBuilder builder = new StringBuilder(VECTOR_DIMENSIONS * 12);
        builder.append('[');
        for (int i = 0; i < VECTOR_DIMENSIONS; i++) {
            if (i > 0) {
                builder.append(',');
            }
            vector[i] = (float) (vector[i] / norm);
            builder.append(vector[i]);
        }
        builder.append(']');
        return builder.toString();
    }

    private Map<String, Object> stats(List<Long> nanos) {
        List<Double> millis = new ArrayList<>(nanos.size());
        for (Long value : nanos) {
            millis.add(value / 1_000_000.0);
        }
        millis.sort(Comparator.naturalOrder());
        double mean = millis.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("p50Ms", round2(percentile(millis, 0.50)));
        output.put("p95Ms", round2(percentile(millis, 0.95)));
        output.put("meanMs", round2(mean));
        output.put("sampleCount", millis.size());
        return output;
    }

    /** 线性插值百分位；样本数较小时比最近秩更平滑。 */
    private double percentile(List<Double> sorted, double percentile) {
        if (sorted.isEmpty()) {
            return 0.0;
        }
        if (sorted.size() == 1) {
            return sorted.get(0);
        }
        double position = (sorted.size() - 1) * percentile;
        int lower = (int) Math.floor(position);
        int upper = (int) Math.ceil(position);
        if (lower == upper) {
            return sorted.get(lower);
        }
        double weight = position - lower;
        return sorted.get(lower) + (sorted.get(upper) - sorted.get(lower)) * weight;
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

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}