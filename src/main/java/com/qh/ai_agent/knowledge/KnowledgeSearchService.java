package com.qh.ai_agent.knowledge;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeSearchService {

    private final KnowledgeBaseLoader knowledgeBaseLoader;

    @Autowired(required = false)
    @Qualifier("csVectorStore")
    private VectorStore csVectorStore;

    private List<Document> cachedDocs;

    /**
     * 两级检索：FAQ 直接匹配 → RAG 向量语义检索
     */
    public SearchResult search(String query, int topK) {
        // Tier 1: FAQ 直接匹配
        FaqMatchResult faqResult = tryFaqDirectMatch(query);
        if (faqResult != null) {
            List<String> suggested = collectSuggestedQuestions(null, faqResult.category);
            log.info("[KnowledgeSearch] query='{}', FAQ直接命中, suggested={}", query, suggested.size());
            return new SearchResult(
                    List.of(faqResult.result),
                    true,
                    suggested
            );
        }

        // Tier 2: RAG 向量语义检索
        List<KnowledgeSearchResult> results = vectorSearch(query, topK);
        List<String> suggested = collectSuggestedQuestions(results, null);
        log.info("[KnowledgeSearch] query='{}', RAG检索 hits={}, suggested={}", query, results.size(), suggested.size());
        return new SearchResult(results, false, suggested);
    }

    /**
     * Tier 1: FAQ 直接匹配
     * 遍历所有 doc_type=FAQ 的文档，比较 metadata.question 与 query 的相似度
     */
    private FaqMatchResult tryFaqDirectMatch(String query) {
        List<Document> docs = getDocs();
        String q = query.toLowerCase().trim();

        Document bestMatch = null;
        double bestScore = 0;
        String bestCategory = null;

        for (Document doc : docs) {
            Map<String, Object> meta = doc.getMetadata();
            if (meta == null || !"FAQ".equals(meta.get("doc_type"))) continue;
            if (!meta.containsKey("question")) continue;

            String question = meta.get("question").toString().toLowerCase().trim();
            String category = (String) meta.getOrDefault("category", "");

            double score = computeFaqSimilarity(q, question);
            if (score > bestScore) {
                bestScore = score;
                bestMatch = doc;
                bestCategory = category;
            }
        }

        // 相似度阈值 0.6
        if (bestMatch != null && bestScore >= 0.6) {
            KnowledgeSearchResult result = KnowledgeSearchResult.from(bestMatch, bestScore);
            // FAQ 直接命中时清除 source 字段
            result = new KnowledgeSearchResult(
                    result.content(), null, result.docType(),
                    result.category(), result.chunkIndex(), result.relevance()
            );
            return new FaqMatchResult(result, bestCategory);
        }

        return null;
    }

    /**
     * FAQ 相似度计算：双向包含 + 编辑距离
     */
    private double computeFaqSimilarity(String query, String question) {
        double score = 0;

        // 双向包含检查
        if (question.contains(query) || query.contains(question)) {
            score += 0.7;
        }

        // 关键词匹配
        String[] queryWords = query.split("[\\s，。？、！]+");
        String[] questionWords = question.split("[\\s，。？、！]+");
        int matchedWords = 0;
        for (String qw : queryWords) {
            if (qw.length() < 2) continue;
            for (String qtw : questionWords) {
                if (qtw.length() < 2) continue;
                if (qw.equals(qtw) || qw.contains(qtw) || qtw.contains(qw)) {
                    matchedWords++;
                    break;
                }
            }
        }
        int totalWords = Math.max(queryWords.length, 1);
        score += 0.3 * ((double) matchedWords / totalWords);

        // 编辑距离加分（短字符串更敏感）
        if (query.length() <= 15 && question.length() <= 15) {
            int editDist = editDistance(query, question);
            int maxLen = Math.max(query.length(), question.length());
            double editSim = 1.0 - ((double) editDist / maxLen);
            if (editSim > 0.8) {
                score += 0.2;
            }
        }

        return Math.min(score, 1.0);
    }

    /**
     * Tier 2: RAG 向量语义检索
     */
    private List<KnowledgeSearchResult> vectorSearch(String query, int topK) {
        if (csVectorStore == null) {
            log.warn("[KnowledgeSearch] PgVector 未启用，回退到关键词匹配");
            return keywordFallback(query, topK);
        }

        try {
            List<Document> searchResults = csVectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(query)
                            .topK(topK)
                            .build()
            );

            return searchResults.stream()
                    .map(doc -> KnowledgeSearchResult.from(doc, 0.8))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("[KnowledgeSearch] 向量检索失败，回退到关键词匹配", e);
            return keywordFallback(query, topK);
        }
    }

    /**
     * 关键词匹配降级方案
     */
    private List<KnowledgeSearchResult> keywordFallback(String query, int topK) {
        List<Document> docs = getDocs();
        List<ScoredDoc> scored = docs.stream()
                .map(doc -> new ScoredDoc(doc, computeKeywordScore(query, doc)))
                .filter(sd -> sd.score > 0)
                .sorted(Comparator.comparingDouble(sd -> -sd.score))
                .limit(topK)
                .toList();

        return scored.stream()
                .map(sd -> KnowledgeSearchResult.from(sd.doc, sd.score))
                .collect(Collectors.toList());
    }

    /**
     * 收集推荐相关问题
     * 从同一分类的 FAQ 中选取其他问题作为推荐
     */
    private List<String> collectSuggestedQuestions(List<KnowledgeSearchResult> results, String faqCategory) {
        List<Document> docs = getDocs();
        Set<String> suggested = new LinkedHashSet<>();
        String targetCategory = faqCategory;

        // 如果是 RAG 检索结果，从结果的 category 中获取
        if (targetCategory == null && results != null && !results.isEmpty()) {
            targetCategory = results.get(0).category();
        }

        // 收集同分类下的 FAQ 问题
        for (Document doc : docs) {
            Map<String, Object> meta = doc.getMetadata();
            if (meta == null || !"FAQ".equals(meta.get("doc_type"))) continue;
            if (!meta.containsKey("question")) continue;

            String category = (String) meta.getOrDefault("category", "");
            if (category.equals(targetCategory) || targetCategory == null) {
                suggested.add(meta.get("question").toString());
            }
            if (suggested.size() >= 5) break;
        }

        // 如果同分类不足，补充其他分类的 FAQ
        if (suggested.size() < 3) {
            for (Document doc : docs) {
                Map<String, Object> meta = doc.getMetadata();
                if (meta == null || !"FAQ".equals(meta.get("doc_type"))) continue;
                if (!meta.containsKey("question")) continue;
                suggested.add(meta.get("question").toString());
                if (suggested.size() >= 5) break;
            }
        }

        return new ArrayList<>(suggested);
    }

    private List<Document> getDocs() {
        if (cachedDocs == null) {
            cachedDocs = knowledgeBaseLoader.loadAll();
            log.info("[KnowledgeSearch] 知识库初始化完成，共加载 {} 条文档", cachedDocs.size());
        }
        return cachedDocs;
    }

    public void clearCache() {
        cachedDocs = null;
        log.info("[KnowledgeSearch] 缓存已清除");
    }

    private double computeKeywordScore(String query, Document doc) {
        String content = doc.getText().toLowerCase();
        String q = query.toLowerCase();
        double score = 0.0;

        if (content.contains(q)) score += 0.5;

        String[] queryWords = q.split("[\\s，。？、！]+");
        int matched = 0;
        for (String word : queryWords) {
            if (word.length() >= 2 && content.contains(word)) matched++;
        }
        if (queryWords.length > 0) score += 0.4 * ((double) matched / queryWords.length);

        Map<String, Object> meta = doc.getMetadata();
        if (meta != null && meta.containsKey("question")) {
            String question = meta.get("question").toString().toLowerCase();
            if (question.contains(q) || q.contains(question)) score += 0.3;
        }
        if (meta != null && meta.containsKey("product_name")) {
            String productName = meta.get("product_name").toString().toLowerCase();
            if (q.contains(productName) || productName.contains(q)) score += 0.3;
        }

        return Math.min(score, 1.0);
    }

    private int editDistance(String a, String b) {
        int m = a.length(), n = b.length();
        int[][] dp = new int[m + 1][n + 1];
        for (int i = 0; i <= m; i++) dp[i][0] = i;
        for (int j = 0; j <= n; j++) dp[0][j] = j;
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                if (a.charAt(i - 1) == b.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    dp[i][j] = 1 + Math.min(dp[i - 1][j - 1], Math.min(dp[i - 1][j], dp[i][j - 1]));
                }
            }
        }
        return dp[m][n];
    }

    private record ScoredDoc(Document doc, double score) {}

    private record FaqMatchResult(KnowledgeSearchResult result, String category) {}

    /**
     * 两级检索结果
     */
    public record SearchResult(
            List<KnowledgeSearchResult> results,
            boolean isFaqDirectMatch,
            List<String> suggestedQuestions
    ) {}

    /**
     * 知识库搜索结果（含出处信息）
     */
    public record KnowledgeSearchResult(
            String content,
            String source,
            String docType,
            String category,
            int chunkIndex,
            double relevance
    ) {
        public static KnowledgeSearchResult from(Document doc, double score) {
            Map<String, Object> meta = doc.getMetadata() != null ? doc.getMetadata() : Map.of();
            return new KnowledgeSearchResult(
                    doc.getText(),
                    (String) meta.getOrDefault("source", "未知来源"),
                    (String) meta.getOrDefault("doc_type", "未知类型"),
                    (String) meta.getOrDefault("category", "未分类"),
                    meta.containsKey("chunk_index") ? ((Number) meta.get("chunk_index")).intValue() : -1,
                    score
            );
        }

        public String toCitedText() {
            if (source == null || source.isEmpty()) {
                return content;
            }
            return content + "\n\n[来源：" + source + " #段落" + chunkIndex + "]";
        }
    }
}
