package com.qh.ai_agent.rag;

import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

public class LoveAppRagCustomAdvisorFactory {

    /**
     * 创建自定义的RAG检索增强顾问（按 status 过滤）
     */
    public static Advisor createLoveAppRagCustomAdvisor(VectorStore vectorStore, String status) {
        Filter.Expression expression = new FilterExpressionBuilder()
                .eq("status", status)
                .build();

        VectorStoreDocumentRetriever documentRetriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .filterExpression(expression)
                .similarityThreshold(0.5)
                .topK(3)
                .build();

        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(documentRetriever)
                .queryAugmenter(LoveAppContexttualQueryAugmenterFactory.createInstance())
                .build();
    }

    /**
     * 创建增强版 RAG 顾问，支持多字段组合过滤
     */
    public static Advisor createEnhancedRagAdvisor(VectorStore vectorStore,
                                                   String status,
                                                   String sourceType,
                                                   String category,
                                                   double similarityThreshold,
                                                   int topK) {
        FilterExpressionBuilder builder = new FilterExpressionBuilder();
        FilterExpressionBuilder.Op result = null;

        if (status != null && !status.isEmpty()) {
            result = builder.eq("status", status);
        }
        if (sourceType != null && !sourceType.isEmpty()) {
            FilterExpressionBuilder.Op sourceOp = builder.eq("source_type", sourceType);
            result = (result == null) ? sourceOp : builder.and(result, sourceOp);
        }
        if (category != null && !category.isEmpty()) {
            FilterExpressionBuilder.Op categoryOp = builder.eq("category", category);
            result = (result == null) ? categoryOp : builder.and(result, categoryOp);
        }

        Filter.Expression expression = (result != null) ? result.build() : null;

        VectorStoreDocumentRetriever documentRetriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .filterExpression(expression)
                .similarityThreshold(similarityThreshold)
                .topK(topK)
                .build();

        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(documentRetriever)
                .queryAugmenter(LoveAppContexttualQueryAugmenterFactory.createInstance())
                .build();
    }
}
