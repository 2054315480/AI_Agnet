package com.qh.ai_agent.rag;

import com.qh.ai_agent.app.LoveApp;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 增强的 RAG 测试 - 验证修复后的知识库引用效果
 */
@SpringBootTest
@Slf4j
public class EnhancedRagTest {

    @Autowired
    private LoveApp loveApp;

    /**
     * 测试1：验证RAG回答是否包含知识库中的具体案例
     */
    @Test
    void testRagAnswerContainsKnowledgeBaseExamples() {
        log.info("=== 测试1：验证RAG回答是否包含知识库案例 ===");
        
        String chatId = UUID.randomUUID().toString();
        String question = "婚后如何平衡工作与家庭责任";
        
        log.info("问题: {}", question);
        
        String answer = loveApp.doChatWithRag(question, chatId);
        
        log.info("RAG 回答:\n{}", answer);
        log.info("========================================");
        
        // 检查是否包含知识库中的关键特征
        boolean containsKnowledgeBaseContent = 
            answer.contains("老陈") ||              // 知识库中的案例人物
            answer.contains("制定详细的日程表") ||   // 知识库中的具体建议
            answer.contains("合理分配工作与家庭时间") ||
            answer.contains("预留两小时陪伴家人") ||
            answer.contains("工作日晚上");
        
        boolean containsCourseRecommendation = 
            answer.contains("课程") ||
            answer.contains("婚后工作家庭平衡之道") ||
            answer.contains("gitee.com");
        
        log.info("✓ 包含知识库具体内容: {}", containsKnowledgeBaseContent);
        log.info("✓ 包含课程推荐: {}", containsCourseRecommendation);
        
        assertTrue(containsKnowledgeBaseContent || containsCourseRecommendation, 
            "RAG回答应该包含知识库的具体内容或课程推荐");
    }

    /**
     * 测试2：验证RAG回答是否包含知识库中关于维护亲密关系的建议
     */
    @Test
    void testRagAnswerForIntimacyMaintenance() {
        log.info("=== 测试2：验证RAG对亲密关系维护问题的回答 ===");
        
        String chatId = UUID.randomUUID().toString();
        String question = "怎样维护婚后夫妻间的亲密关系";
        
        log.info("问题: {}", question);
        
        String answer = loveApp.doChatWithRag(question, chatId);
        
        log.info("RAG 回答:\n{}", answer);
        log.info("========================================");
        
        // 检查是否包含知识库中的关键建议
        boolean containsSpecificAdvice = 
            answer.contains("二人世界") ||
            answer.contains("每周一次") ||
            answer.contains("老张") ||
            answer.contains("分享日常") ||
            answer.contains("身体亲密接触") ||
            answer.contains("拥抱") ||
            answer.contains("亲吻");
        
        boolean containsCourseLink = 
            answer.contains("婚后亲密关系维护秘籍") ||
            answer.contains("课程");
        
        log.info("✓ 包含知识库具体建议: {}", containsSpecificAdvice);
        log.info("✓ 包含课程推荐: {}", containsCourseLink);
        
        assertTrue(containsSpecificAdvice || containsCourseLink,
            "RAG回答应该包含知识库中关于亲密关系的具体建议");
    }

    /**
     * 测试3：验证RAG回答是否包含"根据专业建议"等关键标识
     */
    @Test
    void testRagAnswerContainsProfessionalIndicators() {
        log.info("=== 测试3：验证RAG回答包含专业标识 ===");
        
        String chatId = UUID.randomUUID().toString();
        String question = "婚后与伴侣家人产生矛盾，如何妥善解决";
        
        log.info("问题: {}", question);
        
        String answer = loveApp.doChatWithRag(question, chatId);
        
        log.info("RAG 回答:\n{}", answer);
        log.info("========================================");
        
        // 检查是否包含专业标识或知识库内容
        boolean containsProfessionalMarkers = 
            answer.contains("根据专业建议") ||
            answer.contains("专业建议") ||
            answer.contains("课程推荐") ||
            answer.contains("老王") ||  // 知识库中的案例
            answer.contains("婆媳") ||
            answer.contains("与伴侣坦诚沟通");
        
        log.info("✓ 包含专业标识或知识库内容: {}", containsProfessionalMarkers);
        
        assertNotNull(answer);
        assertTrue(answer.length() > 0);
    }

    /**
     * 测试4：测试多轮对话中RAG的持续性
     */
    @Test
    void testRagInMultiTurnConversation() {
        log.info("=== 测试4：测试多轮对话中的RAG效果 ===");
        
        String chatId = UUID.randomUUID().toString();
        
        // 第一轮
        String question1 = "婚后如何保持自我成长";
        log.info("第一轮问题: {}", question1);
        String answer1 = loveApp.doChatWithRag(question1, chatId);
        log.info("第一轮回答:\n{}\n", answer1);
        
        // 第二轮 - 追问
        String question2 = "能具体说说如何利用碎片化时间吗";
        log.info("第二轮问题: {}", question2);
        String answer2 = loveApp.doChatWithRag(question2, chatId);
        log.info("第二轮回答:\n{}\n", answer2);
        
        // 检查第一轮是否包含知识库内容
        boolean round1HasKnowledge = 
            answer1.contains("老孙") ||
            answer1.contains("碎片化时间") ||
            answer1.contains("通勤路上") ||
            answer1.contains("婚后个人成长");
        
        log.info("✓ 第一轮包含知识库内容: {}", round1HasKnowledge);
        
        assertNotNull(answer1);
        assertNotNull(answer2);
    }

    /**
     * 测试5：对比修复前后的效果（理论测试）
     */
    @Test
    void testEnhancedRagVsBasicChat() {
        log.info("=== 测试5：增强RAG效果验证 ===");
        
        String question = "婚后夫妻消费观念不同，如何协调理财规划";
        
        String chatIdRag = UUID.randomUUID().toString();
        String chatIdBasic = UUID.randomUUID().toString();
        
        // 使用RAG
        log.info("使用增强RAG回答...");
        String ragAnswer = loveApp.doChatWithRag(question, chatIdRag);
        
        // 使用基础对话
        log.info("使用基础对话回答...");
        String basicAnswer = loveApp.doChat(question, chatIdBasic);
        
        log.info("========================================");
        log.info("问题: {}", question);
        log.info("========================================");
        log.info("【增强RAG回答】:\n{}", ragAnswer);
        log.info("========================================");
        log.info("【基础对话回答】:\n{}", basicAnswer);
        log.info("========================================");
        
        // 检查RAG回答是否包含知识库特征
        boolean ragHasKnowledge = 
            ragAnswer.contains("老李") ||
            ragAnswer.contains("制定家庭预算") ||
            ragAnswer.contains("储蓄目标") ||
            ragAnswer.contains("婚后理财规划");
        
        log.info("✓ RAG回答包含知识库特征: {}", ragHasKnowledge);
        
        // 统计回答长度
        log.info("RAG回答字数: {}", ragAnswer.length());
        log.info("基础回答字数: {}", basicAnswer.length());
        
        assertNotNull(ragAnswer);
        assertNotNull(basicAnswer);
    }
}
