package com.qh.ai_agent.rag;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.rag.DashScopeDocumentRetriever;
import com.alibaba.cloud.ai.dashscope.rag.DashScopeDocumentRetrieverOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.model.ApiKey;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;


/**
 * 自定义基于阿里云知识库服务的 RAG 增强顾问
 */
@Configuration
@Slf4j
public class LoveAppRagCloudAdvisorConfig {
    
    @Value("${spring.ai.dashscope.api-key}")
    private String apiKeyValue;
    
    /**
     * 提供所有必需的 9 个参数
     * 
     * @return DashScopeApi 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public DashScopeApi dashScopeApi() {
        ApiKey apiKey = () -> apiKeyValue;
        MultiValueMap<String, String> defaultHeaders = new LinkedMultiValueMap<>();
        String baseUrl = "https://dashscope.aliyuncs.com";
        String chatPath = "/api/v1/services/aigc/text-generation/generation";
        String embeddingPath = "/api/v1/services/embeddings/text-embedding/text-embedding";
        RestClient.Builder restClientBuilder = RestClient.builder();
        WebClient.Builder webClientBuilder = WebClient.builder();
        
        // 创建默认的错误处理器（不能为 null）
        ResponseErrorHandler responseErrorHandler = new ResponseErrorHandler() {
            @Override
            public boolean hasError(org.springframework.http.client.ClientHttpResponse response) {
                return false;
            }
            
            @Override
            public void handleError(org.springframework.http.client.ClientHttpResponse response) {
                // 使用默认处理
            }
        };
        
        return new DashScopeApi(
                baseUrl,
                apiKey,
                defaultHeaders,
                chatPath,
                embeddingPath,
                embeddingPath, // 第6个参数也是 embedding path
                restClientBuilder,
                webClientBuilder,
                responseErrorHandler
        );
    }
    
    /**
     * 创建基于阿里云知识库的 RAG Advisor
     * 
     * @param dashScopeApi DashScopeApi Bean
     * @return RAG 增强顾问
     */
    @Bean
    public Advisor loveAppRagCloudAdvisor(DashScopeApi dashScopeApi) {
        final String KNOWLEDGE = "恋爱大师";
        DocumentRetriever dashScopeDocumentRetriever = new DashScopeDocumentRetriever(dashScopeApi,
                DashScopeDocumentRetrieverOptions.builder()
                        .withIndexName(KNOWLEDGE)
                        .build());
        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(dashScopeDocumentRetriever)
                .build();
    }

}
