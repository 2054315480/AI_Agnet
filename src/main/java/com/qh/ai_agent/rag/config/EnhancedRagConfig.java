package com.qh.ai_agent.rag.config;

import com.qh.ai_agent.rag.retriever.HybridDocumentRetriever;
import com.qh.ai_agent.rag.retriever.KeywordSearchRetriever;
import com.qh.ai_agent.rag.transformer.DictionaryQueryTransformer;
import com.qh.ai_agent.rag.LoveAppContexttualQueryAugmenterFactory;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@Slf4j
public class EnhancedRagConfig {

    @Resource(name = "pgvectorVectorStore")
    private VectorStore pgvectorVectorStore;

    /**
     * 关键词检索器（使用 PostgreSQL LIKE 查询）
     */
    @Bean
    @ConditionalOnProperty(name = "ai.rag.hybrid.keyword.enabled", havingValue = "true")
    public KeywordSearchRetriever keywordSearchRetriever(JdbcTemplate jdbcTemplate) {
        log.info("初始化关键词检索器");
        return new KeywordSearchRetriever(jdbcTemplate, "vector_store", 6);
    }

    /**
     * 混合检索器（向量 + 关键词）
     */
    @Bean
    @ConditionalOnProperty(name = "ai.rag.hybrid.enabled", havingValue = "true")
    public HybridDocumentRetriever hybridDocumentRetriever(
            @Autowired(required = false) KeywordSearchRetriever keywordSearchRetriever) {

        VectorStoreDocumentRetriever vectorRetriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(pgvectorVectorStore)
                .similarityThreshold(0.3)
                .topK(6)
                .build();

        HybridDocumentRetriever.Builder builder = HybridDocumentRetriever.builder()
                .addDelegate(vectorRetriever)
                .strategy(HybridDocumentRetriever.HybridStrategy.FALLBACK)
                .minResults(1);

        if (keywordSearchRetriever != null) {
            builder.addDelegate(keywordSearchRetriever);
            log.info("混合检索器初始化: 向量检索 + 关键词检索 (FALLBACK 策略)");
        } else {
            log.info("混合检索器初始化: 仅向量检索 (关键词检索未启用)");
        }

        return builder.build();
    }

    /**
     * 增强版 RAG Advisor（使用字典查询转换 + 混合检索）
     */
    @Bean
    @ConditionalOnProperty(name = "ai.rag.hybrid.enabled", havingValue = "true")
    public RetrievalAugmentationAdvisor enhancedRagAdvisor(
            DictionaryQueryTransformer dictionaryQueryTransformer,
            @Autowired(required = false) HybridDocumentRetriever hybridDocumentRetriever) {

        RetrievalAugmentationAdvisor.Builder builder = RetrievalAugmentationAdvisor.builder();

        builder.queryTransformers(dictionaryQueryTransformer);

        if (hybridDocumentRetriever != null) {
            builder.documentRetriever(hybridDocumentRetriever);
        } else {
            builder.documentRetriever(VectorStoreDocumentRetriever.builder()
                    .vectorStore(pgvectorVectorStore)
                    .similarityThreshold(0.3)
                    .topK(6)
                    .build());
        }

        builder.queryAugmenter(LoveAppContexttualQueryAugmenterFactory.createInstance());

        log.info("增强版 RAG Advisor 初始化完成");
        return builder.build();
    }
}
