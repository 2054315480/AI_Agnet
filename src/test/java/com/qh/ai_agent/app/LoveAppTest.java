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
}