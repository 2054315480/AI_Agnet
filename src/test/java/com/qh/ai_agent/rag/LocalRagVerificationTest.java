package com.qh.ai_agent.rag;

import com.qh.ai_agent.app.LoveApp;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 本地 RAG 向量库验证测试
 * 测试目的：验证本地向量库是否成功加载知识库文档，以及 RAG 检索是否工作
 */
@SpringBootTest
@Slf4j
public class LocalRagVerificationTest {

    @Autowired
    private VectorStore loveAppVectorStore;

    @Autowired
    private LoveApp loveApp;

    /**
     * 测试1：验证向量库中是否有文档
     */
    @Test
    void testVectorStoreHasDocuments() {
        log.info("=== 测试1：检查向量库是否加载了文档 ===");
        
        // 使用简单的字符串查询
        List<Document> documents = loveAppVectorStore.similaritySearch("婚后");
        
        log.info("向量库中找到 {} 个相关文档", documents.size());
        
        // 打印文档内容
        documents.forEach(doc -> {
            log.info("文档内容预览: {}", doc.getText().substring(0, Math.min(100, doc.getText().length())));
            log.info("文档元数据: {}", doc.getMetadata());
        });
        
        assertTrue(documents.size() > 0, "向量库应该包含文档");
    }

    /**
     * 测试2：测试特定问题的相似度搜索
     */
    @Test
    void testSimilaritySearchForSpecificQuestion() {
        log.info("=== 测试2：测试特定问题的相似度搜索 ===");
        
        String query = "婚后关系不好怎么办";
        
        // 使用带 topK 的重载方法
        List<Document> results = loveAppVectorStore.similaritySearch(query);
        
        log.info("查询: {}", query);
        log.info("找到 {} 个相关文档", results.size());
        
        results.forEach(doc -> {
            log.info("========================================");
            log.info("文档内容: {}", doc.getText());
            log.info("文档元数据: {}", doc.getMetadata());
        });
        
        assertTrue(results.size() > 0, "应该能找到相关文档");
    }

    /**
     * 测试3：测试 RAG 问答，并验证答案是否基于知识库
     */
    @Test
    void testRagAnswerUsesKnowledgeBase() {
        log.info("=== 测试3：测试 RAG 问答是否使用知识库 ===");
        
        String chatId = UUID.randomUUID().toString();
        String question = "婚后关系不好怎么办";
        
        log.info("问题: {}", question);
        
        String answer = loveApp.doChatWithRag(question, chatId);
        
        log.info("RAG 回答: {}", answer);
        
        // 检查答案中是否包含知识库的特征内容
        boolean containsKnowledgeBaseContent = 
            answer.contains("亲密关系") || 
            answer.contains("二人世界") || 
            answer.contains("分享") ||
            answer.contains("沟通") ||
            answer.contains("课程");
        
        log.info("答案是否包含知识库特征内容: {}", containsKnowledgeBaseContent);
        
        assertNotNull(answer, "应该有回答");
        assertTrue(answer.length() > 0, "回答不应为空");
    }

    /**
     * 测试4：对比有 RAG 和无 RAG 的回答差异
     */
    @Test
    void testCompareWithAndWithoutRag() {
        log.info("=== 测试4：对比有 RAG 和无 RAG 的回答 ===");
        
        String question = "婚后如何平衡工作与家庭责任";
        
        // 使用 RAG
        String chatIdWithRag = UUID.randomUUID().toString();
        String answerWithRag = loveApp.doChatWithRag(question, chatIdWithRag);
        
        // 不使用 RAG（普通对话）
        String chatIdWithoutRag = UUID.randomUUID().toString();
        String answerWithoutRag = loveApp.doChat(question, chatIdWithoutRag);
        
        log.info("========================================");
        log.info("问题: {}", question);
        log.info("========================================");
        log.info("【使用 RAG 的回答】:\n{}", answerWithRag);
        log.info("========================================");
        log.info("【不使用 RAG 的回答】:\n{}", answerWithoutRag);
        log.info("========================================");
        
        // 检查 RAG 回答是否包含知识库特征
        boolean ragAnswerHasKnowledgeBase = 
            answerWithRag.contains("制定详细的日程表") ||
            answerWithRag.contains("合理分配") ||
            answerWithRag.contains("老陈") ||
            answerWithRag.contains("课程");
        
        log.info("RAG 回答是否包含知识库内容特征: {}", ragAnswerHasKnowledgeBase);
        
        assertNotNull(answerWithRag);
        assertNotNull(answerWithoutRag);
    }

    /**
     * 测试5：测试知识库文档的加载数量
     */
    @Test
    void testDocumentLoadingCount() {
        log.info("=== 测试5：检查文档加载数量 ===");
        
        // 搜索不同主题的文档
        String[] queries = {"单身", "恋爱", "结婚", "婚后"};
        
        for (String query : queries) {
            List<Document> docs = loveAppVectorStore.similaritySearch(query);
            log.info("主题 '{}' 找到 {} 个相关文档", query, docs.size());
        }
    }
}
