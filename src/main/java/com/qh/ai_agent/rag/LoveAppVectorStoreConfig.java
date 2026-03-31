package com.qh.ai_agent.rag;


import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.spec.DashScopeModel;
import jakarta.annotation.Resource;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 恋爱大师向量数据库配置--初始化基于内存的向量数据库Bean
 */
@Configuration
public class LoveAppVectorStoreConfig {
    @Resource
    private LoveAppDocumentLoder loveAppDocumentLoder;

    @Resource
    private MykeywordEnricher mykeywordEnricher;


    @Resource
    private MyTokenTextSplitter myTokenTextSplitter;
    @Bean
    VectorStore loveAppVectorStore(EmbeddingModel dashscopeEmbeddingModel) {
        SimpleVectorStore simpleVectorStore = SimpleVectorStore.builder(dashscopeEmbeddingModel).build();
        //加载文档
        List<Document> documentlist = loveAppDocumentLoder.loadMarkdown();
        // 自主切分文档
        // List<Document> splitDocuments = myTokenTextSplitter.splitCustomized(documentlist);
        // 自动补充词源元信息
        List<Document> enrichdocuments= mykeywordEnricher.enrichDocuments(documentlist);

        simpleVectorStore.add(enrichdocuments);
        return simpleVectorStore;
    }


}
