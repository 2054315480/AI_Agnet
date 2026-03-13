package com.qh.ai_agent.app;


import com.alibaba.cloud.ai.dashscope.spec.DashScopeModel;
import com.qh.ai_agent.advisor.BannedWordAdvisor;
import com.qh.ai_agent.advisor.My_loggerAdvisor;
import com.qh.ai_agent.advisor.PermissionAdvisor;
import com.qh.ai_agent.advisor.ReReadingAdvisor;
import com.qh.ai_agent.chatmemory.FileBasedChatMemory;
import com.qh.ai_agent.service.BannedWordService;
import com.qh.ai_agent.service.PromptTemplateService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.boot.autoconfigure.rsocket.RSocketProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
@Slf4j
public class LoveApp {
    private final ChatClient chatClient;
    private final PromptTemplateService promptTemplateService;

    private static final String LOVE_ADVISOR_TEMPLATE = "love-advisor";

    /*
    加载系统提示词模板
     */
    private String loadSystemPrompt() {
        return promptTemplateService.loadTemplate(LOVE_ADVISOR_TEMPLATE).render();
    }

    /*
     初始化 AI 客户端
     */
    public  LoveApp(ChatModel dashscopChatModel, BannedWordService bannedWordService, ChatMemory chatMemory, PromptTemplateService promptTemplateService)  {
        this.promptTemplateService = promptTemplateService;

        // 创建对话记忆 Advisor（使用注入的 ChatMemory Bean）
        MessageChatMemoryAdvisor memoryAdvisor =
                 MessageChatMemoryAdvisor.builder(chatMemory)
                .build();

        // 创建权限校验 Advisor
        PermissionAdvisor permissionAdvisor
                =  PermissionAdvisor.builder()
                .allowedUsers(Set.of("user","user1","admin"))
                .currentUser("user")
                .order(-100)
                .build();

        // 创建违禁词校验 Advisor
        BannedWordAdvisor bannedWordAdvisor
                = BannedWordAdvisor.builder()
                .bannedWordService(bannedWordService)
                .filterResponse(true)
                .order(-50)
                .build();

        chatClient = ChatClient.builder(dashscopChatModel)
                .defaultSystem(loadSystemPrompt())
                .defaultAdvisors(
                        permissionAdvisor,
                        bannedWordAdvisor,
                        memoryAdvisor,
                        new My_loggerAdvisor(99)
                )
                .build();
    }
    /*

    AI 基础对话，支持多轮对话
     */
    public String doChat (String message,String chatId){
        // 添加参数校验和调试日志
        log.info("doChat 调用 - chatId: {}, message: {}", chatId, message);
        if (chatId == null || chatId.trim().isEmpty()) {
            throw new IllegalArgumentException("chatId 不能为空");
        }

        ChatResponse chatResponse =
        chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec
                .param("chat_memory_conversation_id", chatId)
                )
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content:{}",content);
        return content;
    }


    record LoveReport(String title, List<String> suggestions) {

    }



    /*

AI 恋爱报告功能，实战结构化输出
 */
    public LoveReport doChatWithReport (String message,String chatId){
        // 添加参数校验和调试日志
        log.info("doChatWithReport 调用 - chatId: {}, message: {}", chatId, message);
        if (chatId == null || chatId.trim().isEmpty()) {
            throw new IllegalArgumentException("chatId 不能为空");
        }

        LoveReport loveReport = chatClient
                .prompt()
                .system(loadSystemPrompt() +
                        "每次对话后都要生成恋爱结果标题为{用户名} 的恋爱报告，内容为建议列表")
                .user(message)
                .advisors(spec -> spec
                        .param("chat_memory_conversation_id", chatId)
                        .param("chat_memory_retrieve_size_key",10)
                )
                .call()
                .entity(LoveReport.class);
        log.info("loveReport:{}",loveReport);
        return loveReport;

    }

    /*
        AI 恋爱知识库问答功能
     */
    @Resource
    private VectorStore lovaAppVectorStore;

    @Resource
    private Advisor loveAppRagCloudAdvisor;

    /**
     *  和RAG 知识库进行对话
     * @param message
     * @param chatId
     * @return
     */
    public String doChatWithRag(String message,String chatId){
        // 修改系统提示词，让 AI 更重视知识库内容
        String ragSystemPrompt = loadSystemPrompt() + 
                "\n\n重要提示：当用户询问恋爱、婚姻相关问题时，如果知识库中有相关专业建议，" +
                "请优先参考并引用知识库内容。可以在回答中提及「根据专业建议」或「课程推荐」等。";
        
        ChatResponse chatResponse = chatClient
                .prompt()
                .system(ragSystemPrompt)  // 使用强化的系统提示词
                .user(message)
                .advisors(spec -> spec
                        .param("chat_memory_conversation_id", chatId)
                        .param("chat_memory_retrieve_size_key",10)
                        // 开启日志
                        .advisors(
                                new My_loggerAdvisor(79),
                                new QuestionAnswerAdvisor(lovaAppVectorStore)  // 使用最简单的构造函数
                        )
                )
                // 应用RAG 检索增强服务
                // .advisors(loveAppRagCloudAdvisor)
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content:{}",content);
        return content;

    }


}
