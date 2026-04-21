package com.qh.ai_agent.rag.retriever;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class HybridDocumentRetriever implements DocumentRetriever {

    private final List<DocumentRetriever> delegates;
    private final HybridStrategy strategy;
    private final int minResults;

    public enum HybridStrategy {
        MERGE,
        FALLBACK
    }

    public HybridDocumentRetriever(List<DocumentRetriever> delegates,
                                   HybridStrategy strategy,
                                   int minResults) {
        this.delegates = delegates;
        this.strategy = strategy;
        this.minResults = minResults;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public List<Document> retrieve(Query query) {
        if (delegates.isEmpty()) {
            return List.of();
        }

        return switch (strategy) {
            case MERGE -> retrieveMerge(query);
            case FALLBACK -> retrieveFallback(query);
        };
    }

    private List<Document> retrieveMerge(Query query) {
        List<Document> allResults = new ArrayList<>();

        for (DocumentRetriever retriever : delegates) {
            try {
                List<Document> results = retriever.retrieve(query);
                allResults.addAll(results);
            } catch (Exception e) {
                log.warn("检索器 {} 执行失败: {}", retriever.getClass().getSimpleName(), e.getMessage());
            }
        }

        // 按 content 去重
        List<Document> deduped = deduplicate(allResults);
        log.info("混合检索(MERGE)总结果: {} (去重后: {})", allResults.size(), deduped.size());
        return deduped;
    }

    private List<Document> retrieveFallback(Query query) {
        for (int i = 0; i < delegates.size(); i++) {
            DocumentRetriever retriever = delegates.get(i);
            try {
                List<Document> results = retriever.retrieve(query);
                if (results.size() >= minResults) {
                    log.info("混合检索(FALLBACK)主检索器返回 {} 条结果", results.size());
                    return results;
                }
                log.info("混合检索(FALLBACK)检索器 {} 返回不足 {} 条，尝试下一个",
                        retriever.getClass().getSimpleName(), minResults);
            } catch (Exception e) {
                log.warn("检索器 {} 失败: {}", retriever.getClass().getSimpleName(), e.getMessage());
            }
        }
        log.warn("混合检索(FALLBACK)所有检索器均未返回足够结果");
        return List.of();
    }

    private List<Document> deduplicate(List<Document> documents) {
        Map<String, Document> byHash = new LinkedHashMap<>();
        for (Document doc : documents) {
            Object hash = doc.getMetadata().get("content_hash");
            String key = (hash != null) ? hash.toString() : doc.getText();
            byHash.putIfAbsent(key, doc);
        }
        return new ArrayList<>(byHash.values());
    }

    public static class Builder {
        private final List<DocumentRetriever> delegates = new ArrayList<>();
        private HybridStrategy strategy = HybridStrategy.MERGE;
        private int minResults = 1;

        public Builder addDelegate(DocumentRetriever retriever) {
            delegates.add(retriever);
            return this;
        }

        public Builder strategy(HybridStrategy strategy) {
            this.strategy = strategy;
            return this;
        }

        public Builder minResults(int minResults) {
            this.minResults = minResults;
            return this;
        }

        public HybridDocumentRetriever build() {
            return new HybridDocumentRetriever(delegates, strategy, minResults);
        }
    }
}
