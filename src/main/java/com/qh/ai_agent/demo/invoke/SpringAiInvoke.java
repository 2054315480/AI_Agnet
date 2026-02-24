package com.qh.ai_agent.demo.invoke;


import com.alibaba.dashscope.assistants.Assistant;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Spring AI 框架 AI 调用大模型
 */
@Component
public class SpringAiInvoke implements CommandLineRunner {
    @Resource
    private ChatModel dashscopeChatModel; //chatclinet


    @Override
    public void run(String... args) throws Exception {
        AssistantMessage assistantMessage = dashscopeChatModel.call(new Prompt("你好，我是秋鹤"))
                .getResult()
                .getOutput();
        System.out.println(assistantMessage.getText());
    }
}
