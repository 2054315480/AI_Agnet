package com.qh.ai_agent.rag;

import com.qh.ai_agent.app.LoveApp;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 云端 RAG（阿里云百炼 ·「恋爱大师」知识库）验证测试
 * 目的：
 * 1) 验证 doChatWithCloudRag 能成功返回回答
 * 2) 粗略校验回答中包含知识库特征（案例/课程等关键词）
 * 3) 与本地 RAG 对比，确认两者均可用
 *
 * 注意：
 * - API Key 已在 application-local.yml 的 ai.dashscope.api-key 配置
 * - application.yml 中 profiles.active=local，默认会启用本地配置
 */
@SpringBootTest
@Slf4j
public class CloudRagVerificationTest {

    @Autowired
    private LoveApp loveApp;

    /**
     * 测试1：云 RAG 能返回非空回答
     */
    @Test
    void testCloudRagBasic() {
        String chatId = "cloud-rag-basic-" + System.currentTimeMillis();
        String question = "婚后关系不好怎么办";

        log.info("=== 测试1：云 RAG 基础可用性 ===");
        log.info("问题: {}", question);

        String answer = loveApp.doChatWithCloudRag(question, chatId);

        log.info("云 RAG 回答: \n{}", answer);

        Assertions.assertNotNull(answer, "云 RAG 应有回答");
        Assertions.assertFalse(answer.isBlank(), "云 RAG 回答不应为空");
    }

    /**
     * 测试2：云 RAG 回答包含知识库特征内容（关键词/案例/课程）
     * 说明：因云知识库内容与产品形态可能调整，此处只做关键词级别的宽松校验
     */
    @Test
    void testCloudRagContainsKnowledgeSignals() {
        String chatId = "cloud-rag-kw-" + System.currentTimeMillis();
        String question = "婚后如何平衡工作与家庭责任";

        log.info("=== 测试2：云 RAG 回答包含知识库特征 ===");
        log.info("问题: {}", question);

        String answer = loveApp.doChatWithCloudRag(question, chatId);
        log.info("云 RAG 回答: \n{}", answer);

        boolean containsSignals =
                answer.contains("老陈") ||
                answer.contains("老张") ||
                answer.contains("老王") ||
                answer.contains("老李") ||
                answer.contains("老孙") ||
                answer.contains("课程") ||
                answer.contains("根据专业建议") ||
                answer.contains("课程推荐");

        log.info("是否包含知识库特征内容: {}", containsSignals);

        Assertions.assertNotNull(answer, "云 RAG 应有回答");
        Assertions.assertTrue(containsSignals, "回答应包含知识库特征内容（案例/课程/标识）");
    }

    /**
     * 测试3：对比云 RAG 与本地 RAG 的回答（不做严格一致性，仅验证均可用）
     */
    @Test
    void testCompareCloudAndLocalRag() {
        String question = "婚后与伴侣家人产生矛盾，如何妥善解决？";

        String cloudChatId = "cloud-rag-compare-" + System.currentTimeMillis();
        String localChatId = "local-rag-compare-" + System.currentTimeMillis();

        log.info("=== 测试3：对比 云RAG 与 本地RAG 的回答 ===");
        log.info("问题: {}", question);

        String cloudAns = loveApp.doChatWithCloudRag(question, cloudChatId);
        String localAns = loveApp.doChatWithRag(question, localChatId);

        log.info("----------------------------------------");
        log.info("【云 RAG 回答】:\n{}", cloudAns);
        log.info("----------------------------------------");
        log.info("【本地 RAG 回答】:\n{}", localAns);
        log.info("----------------------------------------");

        Assertions.assertNotNull(cloudAns, "云 RAG 应有回答");
        Assertions.assertFalse(cloudAns.isBlank(), "云 RAG 回答不应为空");
        Assertions.assertNotNull(localAns, "本地 RAG 应有回答");
        Assertions.assertFalse(localAns.isBlank(), "本地 RAG 回答不应为空");
    }
}
