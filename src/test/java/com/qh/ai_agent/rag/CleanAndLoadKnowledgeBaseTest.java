package com.qh.ai_agent.rag;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

@SpringBootTest
class CleanAndLoadKnowledgeBaseTest {

    @Resource
    private VectorStore pgvectorVectorStore;

    @Resource
    private LoveAppDocumentLoder loveAppDocumentLoder;

    @Resource
    private JdbcTemplate jdbcTemplate;

    /**
     * 清空数据库并重新加载正确的知识库
     */
    @Test
    void cleanAndReloadKnowledgeBase() {
        System.out.println("========== 开始清空旧数据 ==========");

        // 清空 vector_store 表
        int deletedCount = jdbcTemplate.update("DELETE FROM vector_store");
        System.out.println("已删除 " + deletedCount + " 条旧记录");

        // 从 docs 目录加载所有 Markdown 文档（已过滤非知识库文件）
        List<Document> documents = loveAppDocumentLoder.loadMarkdown();

        System.out.println("========== 开始加载知识库 ==========");
        System.out.println("文档总数: " + documents.size());

        // 分批添加，每批 10 条
        int batchSize = 10;
        for (int i = 0; i < documents.size(); i += batchSize) {
            int end = Math.min(i + batchSize, documents.size());
            List<Document> batch = documents.subList(i, end);
            pgvectorVectorStore.add(batch);
            System.out.println("已添加: " + (i + 1) + " - " + end + " / " + documents.size());
        }

        System.out.println("========== 知识库加载完成 ==========");

        // 验证数据
        int totalCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM vector_store", Integer.class);
        System.out.println("\n数据库中共有 " + totalCount + " 条记录");

        // 测试检索
        List<Document> results = pgvectorVectorStore.similaritySearch(
            SearchRequest.builder().query("婚后关系不好怎么办").topK(3).build()
        );

        System.out.println("\n========== 验证检索结果 ==========");
        System.out.println("检索到 " + results.size() + " 条相关文档");
        for (int i = 0; i < Math.min(2, results.size()); i++) {
            String content = results.get(i).getText();
            String preview = content.length() > 100 ? content.substring(0, 100) + "..." : content;
            System.out.println((i + 1) + ". " + preview);
        }
    }
}
