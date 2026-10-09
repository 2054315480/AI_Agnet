package com.qh.ai_agent.knowledge;

import com.qh.ai_agent.knowledge.parser.KbEntry;
import com.qh.ai_agent.knowledge.parser.UnifiedKbLoader;
import com.qh.ai_agent.knowledge.retriever.HybridRetriever;
import com.qh.ai_agent.knowledge.retriever.RetrievalResult;
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

    /**
     * 向量检索相关性下限（余弦相似度）。
     * 低于该值视为知识库没有可用证据，用于触发槽位追问/澄清；
     * 调大更严格（更容易判定为没命中），可用 cs.vector.similarity-threshold 覆盖。
     */
    @org.springframework.beans.factory.annotation.Value("${cs.vector.similarity-threshold:0.5}")
    private double similarityThreshold;

    private final KnowledgeBaseLoader knowledgeBaseLoader;
    private final UnifiedKbLoader unifiedKbLoader;

    @Autowired(required = false)
    @Qualifier("csVectorStore")
    private VectorStore csVectorStore;

    @Autowired(required = false)
    private HybridRetriever hybridRetriever;

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
     * 先搜索 classpath FAQ（KnowledgeBaseLoader），再搜索比赛知识库（UnifiedKbLoader），取最佳匹配
     */
    private FaqMatchResult tryFaqDirectMatch(String query) {
        String q = query.toLowerCase().trim();

        Document bestDoc = null;
        KbEntry bestKbEntry = null;
        double bestScore = 0;
        String bestCategory = null;

        // 1. 搜索 classpath FAQ 文档
        List<Document> docs = getDocs();
        for (Document doc : docs) {
            Map<String, Object> meta = doc.getMetadata();
            if (meta == null || !"FAQ".equals(meta.get("doc_type"))) continue;
            if (!meta.containsKey("question")) continue;

            String question = meta.get("question").toString().toLowerCase().trim();
            String category = (String) meta.getOrDefault("category", "");

            double score = computeFaqSimilarity(q, question);
            if (score > bestScore) {
                bestScore = score;
                bestDoc = doc;
                bestKbEntry = null;
                bestCategory = category;
            }
        }

        // 2. 搜索比赛知识库（UnifiedKbLoader，包含 data/data/knowledge_base/ 下所有行业）
        List<KbEntry> kbEntries = unifiedKbLoader.loadAll();
        for (KbEntry entry : kbEntries) {
            if (entry.getQuestion() == null || entry.getQuestion().isBlank()) continue;

            String question = entry.getQuestion().toLowerCase().trim();
            double score = computeFaqSimilarity(q, question);
            if (score > bestScore) {
                bestScore = score;
                bestDoc = null;
                bestKbEntry = entry;
                bestCategory = entry.getCategory();
            }
        }

        if (bestScore >= 0.6) {
            KnowledgeSearchResult result;
            if (bestKbEntry != null) {
                // 来自比赛知识库
                String content = "Q: " + bestKbEntry.getQuestion() + "\nA: " + bestKbEntry.getAnswer();
                result = new KnowledgeSearchResult(
                        content,
                        bestKbEntry.getSourceFile() != null ? bestKbEntry.getSourceFile() : "未知来源",
                        "FAQ",
                        bestKbEntry.getCategory() != null ? bestKbEntry.getCategory() : "未分类",
                        bestKbEntry.getChunkIndex(),
                        bestScore,
                        bestKbEntry.getKbId()
                );
            } else {
                // 来自 classpath FAQ
                result = KnowledgeSearchResult.from(bestDoc, bestScore);
            }
            return new FaqMatchResult(result, bestCategory);
        }

        return null;
    }

    /**
     * FAQ 相似度计算：精确匹配优先 → 双向包含 + 关键词 + 长度比例 + 编辑距离
     *
     * 核心思想：
     *   1. 精确匹配直接返回 1.0（解决"协助"vs"帮助"一字之差答案不同的问题）
     *   2. query 在 question 中的覆盖率越高，匹配越精确
     *   3. 防止"退款"这种短关键词匹配到所有包含"退款"的长问题
     */
    private double computeFaqSimilarity(String query, String question) {
        // 精确匹配（忽略首尾空白）：用户问题与 KB 问题完全一致时直接返回最高分
        if (query.equals(question)) {
            return 1.0;
        }
        // 忽略所有空白后的精确匹配
        if (query.replaceAll("\\s+", "").equals(question.replaceAll("\\s+", ""))) {
            return 1.0;
        }

        double score = 0;
        int queryLen = query.length();
        int questionLen = question.length();

        // 双向包含检查：按长度比例加权，防止短关键词命中所有长问题
        if (question.contains(query) || query.contains(question)) {
            double lengthRatio = (double) Math.min(queryLen, questionLen)
                               / Math.max(queryLen, questionLen);
            score += 0.7 * lengthRatio;
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

        // 编辑距离加分（适用于长度相近的短字符串）
        if (queryLen <= 15 && questionLen <= 15) {
            int editDist = editDistance(query, question);
            int maxLen = Math.max(queryLen, questionLen);
            double editSim = 1.0 - ((double) editDist / maxLen);
            if (editSim > 0.8) {
                score += 0.2 * editSim;
            }
        }

        // 字符级差异惩罚：当两个候选问题高度相似（包含关系）但尾字符不同时，
        // 降低非精确匹配项的得分，确保"协助"和"帮助"这种细节差异能被区分
        if (question.contains(query) || query.contains(question)) {
            double charJaccard = computeCharJaccard(query, question);
            // charJaccard < 1.0 说明存在字符差异，按差异程度扣分
            if (charJaccard < 0.98) {
                score -= 0.1 * (1.0 - charJaccard);
            }
        }

        // 高字符重叠加分：当 query 与 question 的 bigram 重叠度很高时（即使不是精确包含关系），
        // 说明语义高度接近（如少了"你们的"但仍指向同一问题），给予额外加分以突破 0.6 阈值
        double charOverlap = computeCharJaccard(query, question);
        if (charOverlap >= 0.65 && queryLen >= 5) {
            score += 0.30;
        }

        return Math.min(score, 1.0);
    }

    /**
     * 计算两个字符串的字符级 Jaccard 相似度（基于 bigram 集合）。
     * 用于区分"需要协助"和"需要帮助"这种末端细微差异。
     */
    private double computeCharJaccard(String a, String b) {
        Set<String> bigramsA = extractBigrams(a);
        Set<String> bigramsB = extractBigrams(b);
        if (bigramsA.isEmpty() && bigramsB.isEmpty()) return 1.0;
        if (bigramsA.isEmpty() != bigramsB.isEmpty()) return 0.0;

        Set<String> intersection = new HashSet<>(bigramsA);
        intersection.retainAll(bigramsB);
        Set<String> union = new HashSet<>(bigramsA);
        union.addAll(bigramsB);

        return (double) intersection.size() / union.size();
    }

    private Set<String> extractBigrams(String text) {
        Set<String> bigrams = new LinkedHashSet<>();
        String cleaned = text.replaceAll("\\s+", "");
        for (int i = 0; i < cleaned.length() - 1; i++) {
            bigrams.add(cleaned.substring(i, i + 2));
        }
        return bigrams;
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
                            .similarityThreshold(similarityThreshold)
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
     * 混合检索：FAQ 直接匹配 → 混合检索（BM25 + PgVector RRF 融合）。
     * 相比原有的两级检索，增加了 BM25 稀疏检索，提升精确匹配能力。
     */
    public HybridSearchResult hybridSearch(String query, int topK) {
        // Tier 1: FAQ 直接匹配（快速精确匹配）
        FaqMatchResult faqResult = tryFaqDirectMatch(query);
        if (faqResult != null) {
            List<String> suggested = collectSuggestedQuestions(null, faqResult.category);
            log.info("[KnowledgeSearch] hybrid: FAQ直接命中 '{}'", query);
            return new HybridSearchResult(
                    List.of(faqResult.result),
                    true,
                    suggested,
                    List.of()
            );
        }

        // Tier 2: 混合检索（BM25 + PgVector）
        List<RetrievalResult> hybridResults = List.of();
        List<KnowledgeSearchResult> kbResults;

        if (hybridRetriever != null) {
            hybridResults = hybridRetriever.search(query, topK);
            kbResults = hybridResults.stream()
                    .map(this::toKnowledgeSearchResult)
                    .collect(Collectors.toList());
            log.info("[KnowledgeSearch] hybrid: query='{}', hybrid hits={}", query, kbResults.size());
        } else {
            // 降级：仅 PgVector
            kbResults = vectorSearch(query, topK);
            log.info("[KnowledgeSearch] hybrid: query='{}', hybrid未就绪, 降级vectorSearch hits={}", query, kbResults.size());
        }

        List<String> suggested = collectSuggestedQuestions(kbResults, null);
        return new HybridSearchResult(kbResults, false, suggested, hybridResults);
    }

    /**
     * 将 RetrievalResult 转换为 KnowledgeSearchResult。
     */
    private KnowledgeSearchResult toKnowledgeSearchResult(RetrievalResult r) {
        return new KnowledgeSearchResult(
                "Q: " + r.getQuestion() + "\nA: " + r.getAnswer(),
                r.getSourceFile() != null ? r.getSourceFile() : "未知来源",
                "KB",
                r.getCategory() != null ? r.getCategory() : "未分类",
                -1,
                r.getScore(),
                r.getKbId()
        );
    }

    /**
     * 混合检索结果（扩展版，包含原始 RetrievalResult 列表）。
     */
    public record HybridSearchResult(
            List<KnowledgeSearchResult> results,
            boolean isFaqDirectMatch,
            List<String> suggestedQuestions,
            List<RetrievalResult> rawResults  // 可获取 kbId 等额外信息
    ) {}

    /**
     * 收集推荐相关问题
     * 从同一分类的 FAQ 和比赛知识库中选取其他问题作为推荐
     */
    private List<String> collectSuggestedQuestions(List<KnowledgeSearchResult> results, String faqCategory) {
        List<Document> docs = getDocs();
        Set<String> suggested = new LinkedHashSet<>();
        String targetCategory = faqCategory;

        // 如果是 RAG 检索结果，从结果的 category 中获取
        if (targetCategory == null && results != null && !results.isEmpty()) {
            targetCategory = results.get(0).category();
        }

        // 收集同分类下的 FAQ 问题（classpath）
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

        // 补充比赛知识库中的相关问题
        if (suggested.size() < 5) {
            List<KbEntry> kbEntries = unifiedKbLoader.loadAll();
            for (KbEntry entry : kbEntries) {
                if (entry.getQuestion() == null || entry.getQuestion().isBlank()) continue;
                if (suggested.contains(entry.getQuestion())) continue;
                if (targetCategory != null && !targetCategory.equals(entry.getCategory())) continue;
                suggested.add(entry.getQuestion());
                if (suggested.size() >= 5) break;
            }
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
        unifiedKbLoader.clearCache();
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
            double relevance,
            String kbId
    ) {
        public static KnowledgeSearchResult from(Document doc, double score) {
            Map<String, Object> meta = doc.getMetadata() != null ? doc.getMetadata() : Map.of();
            return new KnowledgeSearchResult(
                    doc.getText(),
                    (String) meta.getOrDefault("source", "未知来源"),
                    (String) meta.getOrDefault("doc_type", "未知类型"),
                    (String) meta.getOrDefault("category", "未分类"),
                    meta.containsKey("chunk_index") ? ((Number) meta.get("chunk_index")).intValue() : -1,
                    score,
                    (String) meta.getOrDefault("kb_id", null)
            );
        }

        public String toCitedText() {
            StringBuilder sb = new StringBuilder(content);
            sb.append("\n\n[来源：").append(source != null && !source.isEmpty() ? source : "未知来源");
            sb.append(" #段落").append(chunkIndex).append("]");
            if (kbId != null && !kbId.isEmpty()) {
                sb.append("\n[条目编号：").append(kbId).append("]");
            }
            return sb.toString();
        }
    }
}
