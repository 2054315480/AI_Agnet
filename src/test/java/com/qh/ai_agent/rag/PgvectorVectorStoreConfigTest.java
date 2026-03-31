package com.qh.ai_agent.rag;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest
class PgvectorVectorStoreConfigTest {

    @Resource
    private VectorStore pgvectorVectorStore;

    @Resource
    private LoveAppDocumentLoder loveAppDocumentLoder;
    @Test
    void pgvectorVectorStore() {

        List<Document> documents = List.of(
                new Document("秋鹤的这个恋爱大师应用有什么用，谈恋爱，脱单，解决婚姻矛盾啊，都可以啊", Map.of("meta1", "meta1")),
                new Document("秋鹤的gitee管理地址 https://gitee.com/qiuhee/ai-super-intelligent-agent/tree"),
                new Document("苍茫的天涯是我的爱", Map.of("meta2", "meta2")));

// Add the documents to PGVector
        pgvectorVectorStore.add(documents);
// Retrieve documents similar to a query
        List<Document> results = this.pgvectorVectorStore.similaritySearch(SearchRequest.builder().query("怎么和秋鹤谈恋爱").topK(3).build());
        Assertions.assertNotNull(results);

    }

    /**
     * 手动加载完整知识库到 PostgreSQL
     * 如果数据库中没有数据，运行此测试方法
     */
    @Test
    void loadKnowledgeBase() {
        // 从 docs 目录加载所有 Markdown 文档
        List<Document> documents = loveAppDocumentLoder.loadMarkdown();

        System.out.println("========== 开始加载知识库 ==========");
        System.out.println("文档总数: " + documents.size());

        // 分批添加，每批 10 条（阿里云 DashScope Embedding API 限制）
        int batchSize = 10;
        for (int i = 0; i < documents.size(); i += batchSize) {
            int end = Math.min(i + batchSize, documents.size());
            List<Document> batch = documents.subList(i, end);
            pgvectorVectorStore.add(batch);
            System.out.println("已添加: " + (i + 1) + " - " + end + " / " + documents.size());
        }

        System.out.println("========== 知识库加载完成 ==========");

        // 验证数据是否加载成功
        List<Document> results = pgvectorVectorStore.similaritySearch(
            SearchRequest.builder().query("婚后关系不好怎么办").topK(3).build()
        );

        System.out.println("\n========== 验证检索结果 ==========");
        System.out.println("检索到 " + results.size() + " 条相关文档:");
        for (int i = 0; i < results.size(); i++) {
            System.out.println((i + 1) + ". " + results.get(i).getText());
        }

        Assertions.assertTrue(results.size() > 0, "应该能检索到相关文档");
    }
}