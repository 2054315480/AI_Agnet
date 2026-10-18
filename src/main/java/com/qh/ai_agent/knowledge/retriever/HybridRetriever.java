package com.qh.ai_agent.knowledge.retriever;

import com.qh.ai_agent.knowledge.PgVectorSearchDao;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 三阶段混合检索编排器。
 *
 * <p>流程：</p>
 * <pre>
 *   用户查询
 *     ├→ BM25 稀疏检索 (候选 topK=10)
 *     ├→ PgVector 稠密检索 (候选 topK=10)
 *     └→ RRF 融合 → 去重排序 → 返回 topK
 * </pre>
 *
 * <p>降级策略：</p>
 * <ul>
 *   <li>pgvector 直查不可用或失败 → 原 VectorStore.similaritySearch</li>
 *   <li>PgVector 整体不可用 → 仅 BM25</li>
 *   <li>BM25 索引未就绪 → 仅 PgVector</li>
 *   <li>两者都不可用 → 空结果</li>
 * </ul>
 */
@Slf4j
@Service
public class HybridRetriever {

    private final Bm25Retriever bm25Retriever;

    @Autowired(required = false)
    @Qualifier("csVectorStore")
    private VectorStore csVectorStore;

    @Autowired(required = false)
    private PgVectorSearchDao pgVectorSearchDao;

    /** 并行检索时的候选池大小。 */
    private static final int CANDIDATE_TOP_K = 10;

    /**
     * RRF 融合公式 score(doc) = Σ 1/(rrfK + rank_i(doc)) 中的 rrfK。
     * 默认 10，可通过 cs.retrieval.rrf-k 覆盖；rank 从 1 开始计数。
     */
    @org.springframework.beans.factory.annotation.Value("${cs.retrieval.rrf-k:10}")
    private int rrfK;

    /**
     * metadata.doc_type 过滤值，可通过 cs.retrieval.filter-doc-type 覆盖。
     * 默认空字符串，表示不过滤。
     */
    @org.springframework.beans.factory.annotation.Value("${cs.retrieval.filter-doc-type:}")
    private String filterDocType;

    /**
     * 向量检索相关性下限（余弦相似度）。
     * 低于该值视为知识库没有可用证据，用于触发槽位追问/澄清；
     * 调大更严格（更容易判定为没命中），可用 cs.vector.similarity-threshold 覆盖。
     */
    @org.springframework.beans.factory.annotation.Value("${cs.vector.similarity-threshold:0.5}")
    private double similarityThreshold;

    public HybridRetriever(Bm25Retriever bm25Retriever) {
        this.bm25Retriever = bm25Retriever;
    }

    /**
     * 执行混合检索，使用 cs.retrieval.filter-doc-type 配置的 metadata 过滤条件。
     *
     * @param query 用户原始查询
     * @param topK  最终返回的结果数
     * @return 融合并排序后的检索结果
     */
    public List<RetrievalResult> search(String query, int topK) {
        return search(query, topK, filterDocType);
    }

    /**
     * 执行混合检索，并显式指定 metadata.doc_type 过滤值。
     * 该重载不改变原 search(String, int) 签名，KnowledgeSearchService 无需调整。
     *
     * @param query   用户原始查询
     * @param topK    最终返回的结果数
     * @param docType metadata.doc_type 过滤值，空表示不过滤
     * @return 融合并排序后的检索结果
     */
    public List<RetrievalResult> search(String query, int topK, String docType) {
        if (query == null || query.isBlank() || topK <= 0) {
            return List.of();
        }

        String effectiveDocType = normalizeDocType(docType);
        int effectiveRrfK = Math.max(1, rrfK);

        // 并行执行 BM25 和 PgVector 检索
        CompletableFuture<List<RetrievalResult>> bm25Future =
                CompletableFuture.supplyAsync(() -> bm25Search(query));
        CompletableFuture<List<RetrievalResult>> vectorFuture =
                CompletableFuture.supplyAsync(() -> vectorSearch(query, effectiveDocType));

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

        log.debug("[HybridRetriever] 候选召回: query='{}', bm25Candidates={}, vectorCandidates={}, "
                        + "rrfK={}, threshold={}, filterDocType='{}', topK={}",
                query, bm25Results.size(), vectorResults.size(), effectiveRrfK,
                similarityThreshold, effectiveDocType == null ? "" : effectiveDocType, topK);

        if (bm25Results.isEmpty() && vectorResults.isEmpty()) {
            return List.of();
        }
        if (bm25Results.isEmpty()) {
            log.debug("[HybridRetriever] 仅向量路有结果，跳过 RRF: vector={}, k={}",
                    vectorResults.size(), effectiveRrfK);
            return vectorResults.stream().limit(topK).toList();
        }
        if (vectorResults.isEmpty()) {
            log.debug("[HybridRetriever] 仅 BM25 路有结果，跳过 RRF: bm25={}, k={}",
                    bm25Results.size(), effectiveRrfK);
            return bm25Results.stream().limit(topK).toList();
        }

        // RRF 融合，k 由配置注入并写入日志
        List<RetrievalResult> fused = reciprocalRankFusion(
                bm25Results, vectorResults, effectiveRrfK);
        log.debug("[HybridRetriever] RRF 融合完成: bm25Candidates={}, vectorCandidates={}, "
                        + "rrfK={}, fused={}, topK={}",
                bm25Results.size(), vectorResults.size(), effectiveRrfK, fused.size(), topK);
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
     *
     * <p>优先走 PgVectorSearchDao 直查拿真实余弦相似度；任何异常都降级到
     * 原 VectorStore.similaritySearch。带 metadata 过滤的 VectorStore 查询若失败，
     * 再降级到不带过滤的原查询，保证检索链路不被打断。</p>
     */
    private List<RetrievalResult> vectorSearch(String query, String docType) {
        if (pgVectorSearchDao != null && pgVectorSearchDao.isAvailable()) {
            try {
                List<RetrievalResult> directResults = pgVectorSearchDao.search(
                        query, CANDIDATE_TOP_K, similarityThreshold, docType);
                log.debug("[HybridRetriever] pgvector 直查成功: hits={}, docType='{}'",
                        directResults.size(), docType == null ? "" : docType);
                return directResults;
            } catch (Exception e) {
                log.warn("[HybridRetriever] pgvector 直查失败，降级 VectorStore.similaritySearch: {}",
                        e.getMessage());
            }
        } else if (csVectorStore == null) {
            log.debug("[HybridRetriever] PgVector 未启用");
            return List.of();
        }

        if (csVectorStore == null) {
            log.debug("[HybridRetriever] PgVector 直查不可用，且 VectorStore 未启用");
            return List.of();
        }

        try {
            return vectorStoreSearch(query, docType);
        } catch (Exception e) {
            log.warn("[HybridRetriever] VectorStore 带过滤检索失败: {}", e.getMessage());
            if (docType != null) {
                try {
                    // 最保守降级：保留原 VectorStore 检索行为（不带 metadata 过滤）。
                    return vectorStoreSearch(query, null);
                } catch (Exception fallbackException) {
                    log.error("[HybridRetriever] VectorStore 降级检索仍失败: {}",
                            fallbackException.getMessage());
                }
            }
            return List.of();
        }
    }

    /**
     * 原 VectorStore 检索路径。非 SQL 路径使用 SearchRequest.filterExpression 实现 metadata 过滤。
     * 分数优先使用 Document.getScore()；若底层未返回分数则记为 0，绝不再用位置假分。
     */
    private List<RetrievalResult> vectorStoreSearch(String query, String docType) {
        SearchRequest.Builder requestBuilder = SearchRequest.builder()
                .query(query)
                .topK(CANDIDATE_TOP_K)
                .similarityThreshold(similarityThreshold);

        if (docType != null) {
            requestBuilder.filterExpression(
                    new FilterExpressionBuilder().eq("doc_type", docType).build());
        }

        List<Document> docs = csVectorStore.similaritySearch(requestBuilder.build());
        if (docs == null || docs.isEmpty()) {
            return List.of();
        }

        List<RetrievalResult> results = new ArrayList<>();
        for (Document doc : docs) {
            Map<String, Object> meta = doc.getMetadata() != null
                    ? doc.getMetadata() : Map.of();

            Double documentScore = doc.getScore();
            if (documentScore == null) {
                log.debug("[HybridRetriever] VectorStore 未返回相似度分数，该条记 0.0: id={}",
                        doc.getId());
            }
            double score = documentScore != null ? documentScore : 0.0d;

            results.add(RetrievalResult.builder()
                    .kbId(extractKbId(doc))
                    .question(stringValue(meta.get("question")))
                    .answer(doc.getText())
                    .domain(stringValue(meta.get("domain")))
                    .category(stringValue(meta.get("category")))
                    .sourceFile(stringValue(meta.get("source")))
                    .score(score)
                    .source(RetrievalResult.Source.VECTOR)
                    .build());
        }
        return results;
    }
    /**
     * 从 PgVector Document 中提取 kbId。
     */
    private String extractKbId(Document doc) {
        Map<String, Object> meta = doc.getMetadata();
        if (meta != null) {
            if (meta.containsKey("kb_id")) {
                return stringValue(meta.get("kb_id"));
            }
            if (meta.containsKey("kbId")) {
                return stringValue(meta.get("kbId"));
            }
        }
        // 从文本内容中尝试提取
        return com.qh.ai_agent.knowledge.parser.KbEntryIdExtractor.extract(doc.getText());
    }

    /**
     * RRF（Reciprocal Rank Fusion）融合算法。
     *
     * <p>score(doc) = Σ 1/(rrfK + rank_i(doc))，其中 rrfK 来自
     * cs.retrieval.rrf-k（默认 10），rank_i 从 1 开始计数。</p>
     */
    private List<RetrievalResult> reciprocalRankFusion(
            List<RetrievalResult> list1, List<RetrievalResult> list2, int fusionK) {

        Map<String, Double> rrfScores = new LinkedHashMap<>();
        Map<String, RetrievalResult> docMap = new LinkedHashMap<>();

        // 处理第一个列表
        for (int i = 0; i < list1.size(); i++) {
            RetrievalResult r = list1.get(i);
            String key = keyForDedup(r);
            rrfScores.merge(key, 1.0 / (fusionK + i + 1), Double::sum);
            docMap.putIfAbsent(key, r);
        }

        // 处理第二个列表
        for (int i = 0; i < list2.size(); i++) {
            RetrievalResult r = list2.get(i);
            String key = keyForDedup(r);
            rrfScores.merge(key, 1.0 / (fusionK + i + 1), Double::sum);
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
     * 生成去重 key：优先 kbId；kbId 为空时使用 source + question 前缀组合，
     * 避免仅凭 question 前缀把不同来源的条目误合并。
     */
    private String keyForDedup(RetrievalResult r) {
        if (r.getKbId() != null && !r.getKbId().isBlank()) {
            return "kb:" + r.getKbId().trim();
        }

        String source = normalizeKeyPart(r.getSourceFile());
        String question = normalizeKeyPart(r.getQuestion());
        if (!source.isEmpty() || !question.isEmpty()) {
            return "source:" + source + "|question:" + prefix(question, 30);
        }

        String answer = normalizeKeyPart(r.getAnswer());
        if (!answer.isEmpty()) {
            return "source:" + source + "|answer:" + prefix(answer, 30);
        }
        return "empty:" + UUID.randomUUID();
    }

    private String normalizeDocType(String docType) {
        return docType == null || docType.isBlank() ? null : docType.trim();
    }

    private String normalizeKeyPart(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String prefix(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.substring(0, Math.min(maxLength, value.length()));
    }

    private String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }

    /**
     * 检查 PgVector 是否可用（直查路径或原 VectorStore 任一可用即可）。
     */
    public boolean isVectorAvailable() {
        return (pgVectorSearchDao != null && pgVectorSearchDao.isAvailable())
                || csVectorStore != null;
    }

    /**
     * 检查 BM25 索引是否就绪。
     */
    public boolean isBm25Ready() {
        return bm25Retriever.isIndexReady();
    }
}