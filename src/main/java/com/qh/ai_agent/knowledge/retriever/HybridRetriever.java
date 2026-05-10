package com.qh.ai_agent.knowledge.retriever;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 三阶段混合检索编排器。
 *
 * 流程：
 *   用户查询
 *     ├→ BM25 稀疏检索 (topK=10)
 *     ├→ PgVector 稠密检索 (topK=10)
 *     └→ RRF 融合 → 去重排序 → 返回 topK
 *
 * 降级策略：
 *   - PgVector 不可用时 → 仅 BM25
 *   - BM25 索引未就绪时 → 仅 PgVector
 *   - 两者都不可用 → 空结果
 */
@Slf4j
@Service
public class HybridRetriever {

    private final Bm25Retriever bm25Retriever;

    @Autowired(required = false)
    @Qualifier("csVectorStore")
    private VectorStore csVectorStore;

    /** 并行检索时的 topK（也是 RRF 动态 k 的基准值） */
    private static final int CANDIDATE_TOP_K = 10;

    /**
     * RRF 融合算法的 k 参数。
     * 标准 RRF 使用 k=60 适用于大规模检索（100+ 候选），
     * 对于小规模检索（CANDIDATE_TOP_K=10），k 应与候选池大小匹配，
     * 否则分数被极端压缩，排名区分度不足。
     */
    private static final int RRF_K = CANDIDATE_TOP_K;

    public HybridRetriever(Bm25Retriever bm25Retriever) {
        this.bm25Retriever = bm25Retriever;
    }

    /**
     * 执行混合检索。
     *
     * @param query  用户原始查询
     * @param topK   最终返回的结果数
     * @return 融合并排序后的检索结果
     */
    public List<RetrievalResult> search(String query, int topK) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        // 并行执行 BM25 和 PgVector 检索
        CompletableFuture<List<RetrievalResult>> bm25Future =
                CompletableFuture.supplyAsync(() -> bm25Search(query));

        CompletableFuture<List<RetrievalResult>> vectorFuture =
                CompletableFuture.supplyAsync(() -> vectorSearch(query));

        List<RetrievalResult> bm25Results;
        List<RetrievalResult> vectorResults;

        try {
            bm25Results = bm25Future.get();
        } catch (Exception e) {
            log.warn("[HybridRetriever] BM25 检索异常: {}", e.getMessage());
            bm25Results = List.of();
        }

        try {
            vectorResults = vectorFuture.get();
        } catch (Exception e) {
            log.warn("[HybridRetriever] PgVector 检索异常: {}", e.getMessage());
            vectorResults = List.of();
        }

        // 如果只有一种结果可用
        if (bm25Results.isEmpty() && vectorResults.isEmpty()) {
            return List.of();
        }
        if (bm25Results.isEmpty()) {
            return vectorResults.stream().limit(topK).toList();
        }
        if (vectorResults.isEmpty()) {
            return bm25Results.stream().limit(topK).toList();
        }

        // RRF 融合
        List<RetrievalResult> fused = reciprocalRankFusion(bm25Results, vectorResults);
        return fused.stream().limit(topK).toList();
    }

    /**
     * BM25 检索。
     */
    private List<RetrievalResult> bm25Search(String query) {
        if (!bm25Retriever.isIndexReady()) {
            return List.of();
        }
        return bm25Retriever.search(query, CANDIDATE_TOP_K);
    }

    /**
     * PgVector 向量检索。
     */
    private List<RetrievalResult> vectorSearch(String query) {
        if (csVectorStore == null) {
            log.debug("[HybridRetriever] PgVector 未启用");
            return List.of();
        }

        try {
            List<Document> docs = csVectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(query)
                            .topK(CANDIDATE_TOP_K)
                            .build()
            );

            List<RetrievalResult> results = new ArrayList<>();
            for (int i = 0; i < docs.size(); i++) {
                Document doc = docs.get(i);
                Map<String, Object> meta = doc.getMetadata() != null
                        ? doc.getMetadata() : Map.of();

                // 归一化分数：位置越靠前分数越高
                double score = 1.0 - (i * 0.05);

                results.add(RetrievalResult.builder()
                        .kbId(extractKbId(doc))
                        .question((String) meta.getOrDefault("question", ""))
                        .answer(doc.getText())
                        .domain((String) meta.getOrDefault("domain", ""))
                        .category((String) meta.getOrDefault("category", ""))
                        .sourceFile((String) meta.getOrDefault("source", ""))
                        .score(score)
                        .source(RetrievalResult.Source.VECTOR)
                        .build());
            }
            return results;
        } catch (Exception e) {
            log.error("[HybridRetriever] PgVector 检索失败: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 从 PgVector Document 中提取 kbId。
     */
    private String extractKbId(Document doc) {
        Map<String, Object> meta = doc.getMetadata();
        if (meta != null) {
            if (meta.containsKey("kb_id")) {
                return meta.get("kb_id").toString();
            }
            if (meta.containsKey("kbId")) {
                return meta.get("kbId").toString();
            }
        }
        // 从文本内容中尝试提取
        return com.qh.ai_agent.knowledge.parser.KbEntryIdExtractor.extract(doc.getText());
    }

    /**
     * RRF（Reciprocal Rank Fusion）融合算法。
     *
     * score(doc) = Σ 1/(k + rank_i(doc))
     * 其中 k=60，rank_i 从 1 开始计数。
     */
    private List<RetrievalResult> reciprocalRankFusion(
            List<RetrievalResult> list1, List<RetrievalResult> list2) {

        Map<String, Double> rrfScores = new LinkedHashMap<>();
        Map<String, RetrievalResult> docMap = new LinkedHashMap<>();

        // 处理第一个列表
        for (int i = 0; i < list1.size(); i++) {
            RetrievalResult r = list1.get(i);
            String key = keyForDedup(r);
            rrfScores.merge(key, 1.0 / (RRF_K + i + 1), Double::sum);
            docMap.putIfAbsent(key, r);
        }

        // 处理第二个列表
        for (int i = 0; i < list2.size(); i++) {
            RetrievalResult r = list2.get(i);
            String key = keyForDedup(r);
            rrfScores.merge(key, 1.0 / (RRF_K + i + 1), Double::sum);
            docMap.putIfAbsent(key, r);
        }

        // 按 RRF 分数降序排列
        return rrfScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(e -> {
                    RetrievalResult r = docMap.get(e.getKey());
                    // 更新分数和来源
                    r.setScore(e.getValue());
                    r.setSource(RetrievalResult.Source.HYBRID);
                    return r;
                })
                .collect(Collectors.toList());
    }

    /**
     * 生成去重用的 key：优先 kbId，其次 question 文本的前 30 字符。
     */
    private String keyForDedup(RetrievalResult r) {
        if (r.getKbId() != null && !r.getKbId().isBlank()) {
            return r.getKbId();
        }
        String q = r.getQuestion();
        return q != null ? q.substring(0, Math.min(30, q.length())) : UUID.randomUUID().toString();
    }

    /**
     * 检查 PgVector 是否可用。
     */
    public boolean isVectorAvailable() {
        return csVectorStore != null;
    }

    /**
     * 检查 BM25 索引是否就绪。
     */
    public boolean isBm25Ready() {
        return bm25Retriever.isIndexReady();
    }
}
