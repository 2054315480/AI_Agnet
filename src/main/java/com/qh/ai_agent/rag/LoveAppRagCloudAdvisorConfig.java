package com.qh.ai_agent.rag;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.rag.DashScopeDocumentRetriever;
import com.alibaba.cloud.ai.dashscope.rag.DashScopeDocumentRetrieverOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 基于阿里云百炼知识库的最小化 RAG 顾问配置
 * 1. 使用 DashScopeApi.builder() 由 SDK 负责内部默认配置（baseUrl、WebClient 等）
 * 2. 索引名使用控制台显示名「恋爱大师」，也可切换为 ID「jew5jcwf1s」
 */
@Configuration
@Slf4j
public class LoveAppRagCloudAdvisorConfig {

    @Value("${spring.ai.dashscope.api-key}")
    private String dashScopeApiKey;

    // 环境要求通过索引ID检索，可切换为 jew5jcwf1s
    private static final String KNOWLEDGE_INDEX_NAME = "恋爱大师";
    // private static final String KNOWLEDGE_INDEX_ID = "jew5jcwf1s";

    @Bean
    public Advisor loveAppRagCloudAdvisor() {
        // 最简 DashScopeApi，避免手动注入 header / 拦截器引发的兼容性问题
        DashScopeApi dashScopeApi = DashScopeApi.builder()
                .apiKey(dashScopeApiKey)
                .build();

        // 使用显示名称进行索引解析；如遇到名称解析失败，可改为 .withIndexName(KNOWLEDGE_INDEX_ID)
        DocumentRetriever retriever = new DashScopeDocumentRetriever(
                dashScopeApi,
                DashScopeDocumentRetrieverOptions.builder()
                        .withIndexName(KNOWLEDGE_INDEX_NAME)
                        .build()
        );

        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(retriever)
                .build();
    }
}
