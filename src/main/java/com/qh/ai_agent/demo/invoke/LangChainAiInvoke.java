package com.qh.ai_agent.demo.invoke;

import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.model.chat.ChatLanguageModel;

public class LangChainAiInvoke {
    public static void main(String[] args) {
        ChatLanguageModel qwenChatModel = QwenChatModel.builder()
                .apiKey(TestApiKey.API_KEY)
                .modelName("qwen-max")
                .build();
       String answer = qwenChatModel.chat("我是秋鹤，这个是我准备应届毕业生面试Java的一个面试项目");
        System.out.println(answer);
    }
}
