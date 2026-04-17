package com.qh.ai_agent.app;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@SpringBootTest
class LoveAppTest {

    @Resource
    private LoveApp loveApp;

    @Test
    void testChat() {
        String chatId = UUID.randomUUID().toString();
        // 第一轮
        String message = "你好，我是秋鹤，你是谁";
        String answer = loveApp.doChat(message, chatId);


        // 第二轮
         message = "我现在想让我的爱人更喜欢我，我应该怎么办";
         answer = loveApp.doChat(message, chatId);
        Assertions.assertNotNull(answer);

        // 第三轮
         message = "但是我是谁来着，我刚刚和你说过，你回忆一下";
         answer = loveApp.doChat(message, chatId);
        Assertions.assertNotNull(answer);
    }


    @Test
    void doChatWithReport() {
        String chatId = UUID.randomUUID().toString();
        String message = "你好，我是秋鹤，我是一个应届毕业生,我现在想让我的另一半更爱我，" +
                "但是我不知道我应该怎么办";
        LoveApp.LoveReport loveReport = loveApp.doChatWithReport(message, chatId);
        Assertions.assertNotNull(loveReport);

    }

    @Test
    void doChat() {
        String chatId = UUID.randomUUID().toString();
        String message = "我已经结婚了，但是婚后关系不好怎么办";
        String answer = loveApp.doChatWithRag(message, chatId);
        Assertions.assertNotNull(answer);
    }

    @Test
    void testDoChatWithReport() {
    }

    @Test
    void doChatWithRag() {
        String chatId = UUID.randomUUID().toString();
        String message = "我已经结婚了，但是婚后关系不好怎么办";
        String answer = loveApp.doChatWithRag(message, chatId);
        Assertions.assertNotNull(answer);
    }
    @Test
    void doChatWithPGSQL() {
        String chatId = UUID.randomUUID().toString();
        String message = "我已经结婚了，但是婚后关系不好怎么办";
        String answer = loveApp.doChatWithPGSQL(message, chatId);
        Assertions.assertNotNull(answer);
    }

    @Test
    void doChatWithFactory() {
        String chatId = UUID.randomUUID().toString();
        String message = "我已经结婚了，但是婚后关系不好怎么办";
        String answer = loveApp.doChatWithFactory(message, chatId);
        Assertions.assertNotNull(answer);
    }

    @Test
    void doChatWithTools() {
            // 测试联网搜索问题的答案
            testMessage("周末想带女朋友去南昌约会，推荐几个适合情侣的小众打卡地？");

            // 测试网页抓取
            testMessage("获取这个网站（https://gitee.com/qiuhee/ai-super-intelligent-agent）的内容，看看有什么项目信息");

            // 测试资源下载：图片下载（使用更可靠的图片来源）
            testMessage("搜索一张免费的4K情侣壁纸，然后下载这张图片");

            // 测试文件操作：保存用户档案
            testMessage("将以下内容保存为文件：用户档案-姓名：张三，年龄：25，兴趣爱好：摄影、旅行");

            // 测试 PDF 生成
            testMessage("生成一份南昌’情人节约会计划’PDF，包含美食推荐、活动流程和礼物清单");
        }

        private void testMessage(String message) {
            String chatId = UUID.randomUUID().toString();
            log.info("========== 开始测试: {} ==========", message);
            log.info("测试使用 chatId: {}", chatId);

            try {
                String answer = loveApp.doChatWithTools(message, chatId);
                Assertions.assertNotNull(answer);
                Assertions.assertFalse(answer.trim().isEmpty());

                log.info("回答成功，长度: {} 字符", answer.length());
                log.info("========== 测试完成 ==========\n");
            } catch (Exception e) {
                log.error("测试失败", e);
                throw e;
            }
        }

    @Test
    void doChatWithMCP() {
        String chatId = UUID.randomUUID().toString();
        // 测试高德MCP
        String message = "我对象在南昌市，请帮我在这附近100公里找几个室内的约会地点";
        String answer = loveApp.doChatWithMCP(message, chatId);
        Assertions.assertNotNull(answer);
        // 测试图片搜索MCP
   //     String message2 = "帮我找几个星空的图片";
   //     String answer2 = loveApp.doChatWithMCP(message2, chatId);
   //     Assertions.assertNotNull(answer2);
    }
}