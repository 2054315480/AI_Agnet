package com.qh.ai_agent.rag;

import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

public class LoveAppRagCustomAdvisorFactory {

    /**
     * 创建自定义的RAG检索增强顾问
     * @param vectorStore
     * @param status
     * @return
     */
    public static Advisor createLoveAppRagCustomAdvisor(VectorStore vectorStore,String status) {
        // 过滤特定状态
        Filter.Expression expression = new FilterExpressionBuilder()
                .eq("status", status)
                .build();

        VectorStoreDocumentRetriever documentRetriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .filterExpression(expression)  //过滤条件
                .similarityThreshold(0.5)  // 相似度阈值
                .topK(3)  // 返回的文档数量
                .build();

        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(documentRetriever)
                // 应用自定义的RAG 检索增强服务，文档查询器和上下文增强
                .queryAugmenter(LoveAppContexttualQueryAugmenterFactory.createInstance())
                .build();
    }
}
