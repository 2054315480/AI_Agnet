package com.qh.ai_agent.app;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class LoveAppTest {

    @Resource
    private LoveApp loveApp;

    @Test
    void testChat() {
        String chatId = UUID.randomUUID().toString();
        // 第一轮
        String message = "你好，我是秋鹤，我是一个应届毕业生";
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

            // 测试网页抓取：恋爱案例分析
            testMessage("最近和对象吵架了，看看这个网站（https://gitee.com/qiuhee/ai-super-intelligent-agent）的其他情侣是怎么解决矛盾的？");

            // 测试资源下载：图片下载
            testMessage("直接下载一张适合做手机壁纸的情侣图片为文件");

            // 测试终端操作：执行代码
            testMessage("执行 Python3 脚本来生成数据分析报告");

            // 测试文件操作：保存用户档案
            testMessage("保存我的恋爱档案为文件");

            // 测试 PDF 生成
            testMessage("生成一份‘情人节约会计划’PDF，包含路边摊，小吃街等美食、活动流程和礼物清单，以及说女朋友说的暧昧话术暖她一整天");
        }

        private void testMessage(String message) {
            String chatId = UUID.randomUUID().toString();
            String answer = loveApp.doChatWithTools(message, chatId);
            Assertions.assertNotNull(answer);
        }

}