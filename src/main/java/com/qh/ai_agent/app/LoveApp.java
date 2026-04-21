package com.qh.ai_agent.app;


import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.dashscope.spec.DashScopeModel;
import com.qh.ai_agent.advisor.BannedWordAdvisor;
import com.qh.ai_agent.advisor.My_loggerAdvisor;
import com.qh.ai_agent.advisor.PermissionAdvisor;
import com.qh.ai_agent.advisor.ReReadingAdvisor;
import com.qh.ai_agent.advisor.SensitiveInfoAdvisor;
import com.qh.ai_agent.chatmemory.FileBasedChatMemory;
import com.qh.ai_agent.rag.LoveAppRagCustomAdvisorFactory;
import com.qh.ai_agent.rag.QueryReweiter;
import com.qh.ai_agent.rag.reader.GitHubDocumentReader;
import com.qh.ai_agent.rag.model.RagRequest;
import com.qh.ai_agent.service.BannedWordService;
import com.qh.ai_agent.service.PromptTemplateService;
import com.qh.ai_agent.service.SensitiveInfoService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.content.Media;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Set;




@Component
@Slf4j
public class LoveApp {
    private final ChatClient chatClient;
    private final PromptTemplateService promptTemplateService;
    private final String visionModel;

    private static final String LOVE_ADVISOR_TEMPLATE = "love-advisor";

    /*
    加载系统提示词模板
     */
    private String loadSystemPrompt() {
        return promptTemplateService.loadTemplate(LOVE_ADVISOR_TEMPLATE).render();
    }

    @Resource
    private QueryReweiter queryReweiter;

    /*
     初始化 AI 客户端
     */
    public  LoveApp(ChatModel dashscopChatModel, BannedWordService bannedWordService, ChatMemory chatMemory, PromptTemplateService promptTemplateService, SensitiveInfoService sensitiveInfoService,
                     @Value("${spring.ai.dashscope.image.options.model:qwen-vl-plus}") String visionModel,
                     ToolCallbackProvider toolCallbackProvider)  {
        this.promptTemplateService = promptTemplateService;
        this.visionModel = visionModel;

        // 获取 MCP 工具（高德地图、图片搜索等）
        ToolCallback[] mcpCallbacks = toolCallbackProvider.getToolCallbacks();
        log.info("LoveApp 加载 MCP 工具数量: {}", mcpCallbacks.length);

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

        // 创建敏感信息脱敏 Advisor
        SensitiveInfoAdvisor sensitiveInfoAdvisor
                = SensitiveInfoAdvisor.builder()
                .sensitiveInfoService(sensitiveInfoService)
                .maskResponse(true)
                .order(-75)
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
                        sensitiveInfoAdvisor,
                        bannedWordAdvisor,
                        memoryAdvisor,
                        new My_loggerAdvisor(99)
                )
                .defaultToolCallbacks(mcpCallbacks)
                .build();
    }
    /*

    AI 基础对话，支持多轮对话
     */
    public String doChat (String message,String chatId){
        //使用查询重写器
        String rewrittenMessage = queryReweiter.doQueryReweiter(message);

        // 添加参数校验和调试日志
        log.info("doChat 调用 - chatId: {}, message: {}", chatId, rewrittenMessage);
        if (chatId == null || chatId.trim().isEmpty()) {
            throw new IllegalArgumentException("chatId 不能为空");
        }

        ChatResponse chatResponse =
        chatClient
                .prompt()
                .system(buildRagSystemPrompt())
                .user(rewrittenMessage)
                .advisors(spec -> spec
                .param("chat_memory_conversation_id", chatId)
                .advisors(buildRagAdvisor())
                )
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content:{}",content);
        return content;
    }

    /*

AI 基础对话，支持多轮对话 支持SSE流式传输
 */
    public Flux<String> doChatByStream (String message,String chatId){
        //使用查询重写器
        String rewrittenMessage = queryReweiter.doQueryReweiter(message);

        // 添加参数校验和调试日志
        log.info("doChat 调用 - chatId: {}, message: {}", chatId, rewrittenMessage);
        if (chatId == null || chatId.trim().isEmpty()) {
            throw new IllegalArgumentException("chatId 不能为空");
        }

        return chatClient
                        .prompt()
                        .system(buildRagSystemPrompt())
                        .user(rewrittenMessage)
                        .advisors(spec -> spec
                                .param("chat_memory_conversation_id", chatId)
                                .advisors(buildRagAdvisor())
                        )
                        .stream()
                        .content();
    }

    /**
     * AI 图片对话（SSE 流式），支持用户同时发送文字和图片
     */
    public Flux<String> doChatWithImageByStream(String message, String chatId, Media... media) {
        log.info("doChatWithImageByStream - chatId: {}, message: {}, images: {}", chatId, message, media != null ? media.length : 0);
        if (chatId == null || chatId.trim().isEmpty()) {
            throw new IllegalArgumentException("chatId 不能为空");
        }

        String rewrittenMessage = queryReweiter.doQueryReweiter(message);

        return chatClient
                .prompt()
                .options(DashScopeChatOptions.builder()
                        .withModel(visionModel)
                        .withMultiModel(true)
                        .withEnableThinking(false)
                        .build())
                .user(u -> {
                    u.text(rewrittenMessage);
                    if (media != null) {
                        for (Media m : media) {
                            u.media(m);
                        }
                    }
                })
                .advisors(spec -> spec
                        .param("chat_memory_conversation_id", chatId)
                )
                .stream()
                .content();
    }

    /**
     * AI 图片对话（同步），支持用户同时发送文字和图片
     */
    public String doChatWithImage(String message, String chatId, Media... media) {
        log.info("doChatWithImage - chatId: {}, message: {}, images: {}", chatId, message, media != null ? media.length : 0);
        if (chatId == null || chatId.trim().isEmpty()) {
            throw new IllegalArgumentException("chatId 不能为空");
        }

        String rewrittenMessage = queryReweiter.doQueryReweiter(message);

        ChatResponse chatResponse = chatClient
                .prompt()
                .options(DashScopeChatOptions.builder()
                        .withModel(visionModel)
                        .withMultiModel(true)
                        .withEnableThinking(false)
                        .build())
                .user(u -> {
                    u.text(rewrittenMessage);
                    if (media != null) {
                        for (Media m : media) {
                            u.media(m);
                        }
                    }
                })
                .advisors(spec -> spec
                        .param("chat_memory_conversation_id", chatId)
                )
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content:{}", content);
        return content;
    }

    /**
     * RAG 增强的系统提示词
     */
    private String buildRagSystemPrompt() {
        return loadSystemPrompt() +
                "\n\n## 知识库使用规则\n" +
                "1. 当回答用户问题时，你会收到来自专业知识库的相关内容\n" +
                "2. **必须严格基于知识库内容回答**，这是专业恋爱咨询的核心资料\n" +
                "3. 如果知识库中包含具体案例（如小王、小李、小张、小赵、小钱等），**必须在回答中引用**\n" +
                "4. 如果知识库中推荐了课程链接（包含gitee.com的链接），**必须在回答末尾完整保留**\n" +
                "5. 在回答开头或适当位置使用「根据专业建议」「课程推荐」等标识\n" +
                "6. 知识库内容与通用知识冲突时，**以知识库为准**";
    }

    /**
     * 构建 RAG 检索增强 Advisor（基于内存向量库）
     */
    private Advisor buildRagAdvisor() {
        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(new VectorStoreDocumentRetriever(
                        loveAppVectorStore,
                        0.3,
                        6,
                        () -> null
                ))
                .build();
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
    private VectorStore loveAppVectorStore;

    @Resource
    private Advisor loveAppRagCloudAdvisor;

    @Resource
    private VectorStore pgvectorVectorStore;

    /**
     *  和RAG 知识库进行对话
     * @param message
     * @param chatId
     * @return
     */
    public String doChatWithRag(String message,String chatId){
        // 强化系统提示词，明确要求使用知识库
        String ragSystemPrompt = loadSystemPrompt() + 
                "\n\n## 知识库使用规则\n" +
                "1. 当回答用户问题时，你会收到来自专业知识库的相关内容\n" +
                "2. **必须严格基于知识库内容回答**，这是专业恋爱咨询的核心资料\n" +
                "3. 如果知识库中包含具体案例（如老陈、老张、老王、老李、老孙等），**必须在回答中引用**\n" +
                "4. 如果知识库中推荐了课程链接（包含gitee.com的链接），**必须在回答末尾完整保留**\n" +
                "5. 在回答开头或适当位置使用「根据专业建议」「课程推荐」等标识\n" +
                "6. 知识库内容与通用知识冲突时，**以知识库为准**";
        String rewrittenMessage = queryReweiter.doQueryReweiter(message);

        ChatResponse chatResponse = chatClient
                .prompt()
                .system(ragSystemPrompt)  // 使用强化的系统提示词
                .user(rewrittenMessage)
                .advisors(spec -> spec
                        .param("chat_memory_conversation_id", chatId)
                        .param("chat_memory_retrieve_size_key",10)
                        // 开启日志和RAG检索
                        .advisors(
                                new My_loggerAdvisor(79),
                                RetrievalAugmentationAdvisor.builder()
                                        .documentRetriever(new VectorStoreDocumentRetriever(
                                                loveAppVectorStore,
                                                0.3,   // 最小相似度阈值（0~1），可按需要调整
                                                6,     // topK 返回文档数
                                                () -> null // 不使用过滤条件
                                        ))
                                        .build()
                        )
                )
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content:{}",content);
        return content;

    }


    /**
     * 使用阿里云百炼平台「恋爱大师」云知识库进行 RAG 对话
     * 说明：
     * - 顾问链采用 cloud retriever：loveAppRagCloudAdvisor（见 LoveAppRagCloudAdvisorConfig）
     * - 系统提示词中明确要求严格依据知识库内容作答，并在必要时引用课程链接/案例名称
     * - 仍保留对话记忆参数，以便结合上下文进行跟进问答
     */
    public String doChatWithCloudRag(String message, String chatId) {
        log.info("doChatWithCloudRag 调用 - chatId: {}, message: {}", chatId, message);
        if (chatId == null || chatId.trim().isEmpty()) {
            throw new IllegalArgumentException("chatId 不能为空");
        }

        String cloudRagSystemPrompt = loadSystemPrompt() +
                "\n\n## 云知识库使用规则（阿里云·恋爱大师）\n" +
                "1. 你将接收来自云端知识库的检索内容，必须严格基于其回答\n" +
                "2. 若知识库包含具体案例（如老陈、老张、老王、老李、老孙），需要在回答中引用\n" +
                "3. 若知识库给出课程链接，需在回答末尾完整保留\n" +
                "4. 当云知识库与常识冲突时，以云知识库为准\n" +
                "5. 优先生成结构化、可执行的建议清单\n" +
                "6. 直接回答用户问题，禁止寒暄、禁止反问用户、禁止要求用户先提供更多信息\n" +
                "7. 若检索到的知识库内容不足以回答，明确回复“我不知道该问题的答案”，并建议用户换个问法；不可编造\n" +
                "8. 输出格式严格如下：\n" +
                "   - 标题：一句话结论\n" +
                "   - 要点清单：3~6条可执行步骤（使用- 列表），必要时在条目中引用案例名\n" +
                "   - 参考：如存在课程链接或案例来源，完整给出链接\n";

        ChatResponse chatResponse = chatClient
                .prompt()
                .system(cloudRagSystemPrompt)
                .user(message)
                .advisors(spec -> spec
                        .param("chat_memory_conversation_id", chatId)
                        .param("chat_memory_retrieve_size_key", 10)
                        .advisors(
                                new My_loggerAdvisor(89),
                                // 使用云端 RAG 顾问（阿里云百炼平台·恋爱大师索引）
                                loveAppRagCloudAdvisor
                        )
                )
                .call()
                .chatResponse();

        String content = chatResponse.getResult().getOutput().getText();
        log.info("cloud rag content:{}", content);
        return content;
    }

    /**
     *
     *使用RAG检索增强服务，基于PGvector 向量存储
     */
    public String doChatWithPGSQL(String message, String chatId) {
        log.info("doChatWithPGSQL 调用 - chatId: {}, message: {}", chatId, message);
        if (chatId == null || chatId.trim().isEmpty()) {
            throw new IllegalArgumentException("chatId 不能为空");
        }

        String localRagSystemPrompt = loadSystemPrompt() +
                "\n\n## 本地向量库使用规则\n" +
                "1. 你将接收来自本地向量知识库的检索内容，必须严格基于其回答\n" +
                "2. 若知识库包含具体案例（如老陈、老张、老王、老李、老孙），需要在回答中引用\n" +
                "3. 当知识库内容与常识冲突时，以知识库为准\n" +
                "4. 优先生成结构化、可执行的建议清单\n" +
                "5. 直接回答用户问题，禁止寒暄、禁止反问用户、禁止要求用户先提供更多信息\n" +
                "6. 若检索到的知识库内容不足以回答，明确回复\"我不知道该问题的答案\"，并建议用户换个问法；不可编造\n" +
                "7. **绝对禁止编造链接！** 由于检索结果中可能不包含课程链接，如果检索到的文档片段中没有明确提到完整的课程链接（如 https://... 开头的URL），则不要输出\"参考：\"部分。只输出标题和要点清单即可。\n" +
                "8. 输出格式：\n" +
                "   - 标题：一句话结论\n" +
                "   - 要点清单：3~6条可执行步骤（使用- 列表），必要时在条目中引用案例名\n";

        ChatResponse chatResponse = chatClient
                .prompt()
                .system(localRagSystemPrompt)
                .user(message)
                .advisors(spec -> spec
                        .param("chat_memory_conversation_id", chatId)
                        .param("chat_memory_retrieve_size_key", 10)
                        .advisors(
                                RetrievalAugmentationAdvisor.builder()
                                        .documentRetriever(new VectorStoreDocumentRetriever(pgvectorVectorStore, 0.3, 6, () -> null))
                                        .build()
                        )
                )
                .call()
                .chatResponse();

        String content = chatResponse.getResult().getOutput().getText();
        log.info("local pgsql rag content:{}", content);
        return content;
    }


    public String doChatWithFactory(String message, String chatId) {
        log.info("doChatWithFactory 调用 - chatId: {}, message: {}", chatId, message);
        if (chatId == null || chatId.trim().isEmpty()) {
            throw new IllegalArgumentException("chatId 不能为空");
        }

        String cloudRagSystemPrompt = loadSystemPrompt() +
                "1. 你将接收来自云端知识库的检索内容，必须严格基于其回答\n" +
                "2. 若知识库包含具体案例（如老陈、老张、老王、老李、老孙），需要在回答中引用\n" +
                "3. 若知识库给出课程链接，需在回答末尾完整保留\n" +
                "4. 当云知识库与常识冲突时，以云知识库为准\n" +
                "5. 优先生成结构化、可执行的建议清单\n" +
                "6. 直接回答用户问题，禁止寒暄、禁止反问用户、禁止要求用户先提供更多信息\n" +
                "7. 若检索到的知识库内容不足以回答，明确回复“我不知道该问题的答案”，并建议用户换个问法；不可编造\n" +
                "8. 输出格式严格如下：\n" +
                "   - 标题：一句话结论\n" +
                "   - 要点清单：3~6条可执行步骤（使用- 列表），必要时在条目中引用案例名\n" +
                "   - 参考：如存在课程链接或案例来源，完整给出链接\n";

        ChatResponse chatResponse = chatClient
                .prompt()
                .system(cloudRagSystemPrompt)
                .user(message)
                .advisors(spec -> spec
                        .param("chat_memory_conversation_id", chatId)
                        .param("chat_memory_retrieve_size_key", 10)
                        .advisors(
                                new My_loggerAdvisor(89)
                        )
                        .advisors(
                                LoveAppRagCustomAdvisorFactory.createLoveAppRagCustomAdvisor(
                                        loveAppVectorStore,"单身"
                                )
                        )
                )
                .call()
                .chatResponse();

        String content = chatResponse.getResult().getOutput().getText();
        log.info("local rag content:{}", content);
        return content;
    }


    // LoveApp 调用工具的能力
    @Resource
    private ToolCallback[] allTools;

    public String doChatWithTools (String message,String chatId){
        // 添加参数校验和调试日志
        log.info("doChatWithTools 调用 - chatId: {}, message: {}", chatId, message);
        if (chatId == null || chatId.trim().isEmpty()) {
            throw new IllegalArgumentException("chatId 不能为空");
        }

        // 工具调用场景使用更中性的系统提示词，避免恋爱顾问角色干扰
        String toolSystemPrompt = "你是一个智能助手，可以帮助用户使用各种工具完成具体任务。" +
                "你可以使用提供的工具来帮助用户搜索信息、下载文件、抓取网页内容等。" +
                "直接执行用户的任务请求，不要添加无关的角色设定或开场白。";

         ChatResponse chatResponse= chatClient
                .prompt()
                .system(toolSystemPrompt)  // 使用工具专用的系统提示词
                .user(message)
                .advisors(spec -> spec
                        .param("chat_memory_conversation_id", chatId)
                        .param("chat_memory_retrieve_size_key",10)
                )
                // 开启日志
                .advisors(new My_loggerAdvisor(67))
                .toolCallbacks(allTools)
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content:{}",content);
        return content;

    }

    /**
     * AI 调用MCP服务
     */
    @Resource
    private ToolCallbackProvider toolCallbackProvider;

    public String doChatWithMCP (String message,String chatId){
        ToolCallback[] mcpCallbacks = toolCallbackProvider.getToolCallbacks();
        log.info("doChatWithMCP 调用 - chatId: {}, message: {}, MCP工具数量: {}", chatId, message, mcpCallbacks.length);
        if (chatId == null || chatId.trim().isEmpty()) {
            throw new IllegalArgumentException("chatId 不能为空");
        }

        ChatResponse chatResponse= chatClient
                .prompt()
                .system(loadSystemPrompt() + "\n\n## 重要规则\n当用户需要搜索图片时，跳过上述开场白和回复格式，直接调用searchImage工具为用户搜索图片，不要反问。")
                .user(message)
                .advisors(spec -> spec
                        .param("chat_memory_conversation_id", chatId)
                        .param("chat_memory_retrieve_size_key",10)
                )
                // 开启日志
                .advisors(new My_loggerAdvisor(67))
                .toolCallbacks(mcpCallbacks)
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content:{}",content);
        return content;

    }

    // ========== RAG 增强功能 ==========

    @Resource
    private GitHubDocumentReader gitHubDocumentReader;

    /**
     * 使用 GitHub 仓库文档进行 RAG 对话
     */
    public String doChatWithGitHubRag(String message, String chatId, String owner, String repo) {
        log.info("doChatWithGitHubRag 调用 - chatId: {}, repo: {}/{}", chatId, owner, repo);
        if (chatId == null || chatId.trim().isEmpty()) {
            throw new IllegalArgumentException("chatId 不能为空");
        }

        // 加载 GitHub 仓库文档并临时加入向量库
        List<org.springframework.ai.document.Document> githubDocs = gitHubDocumentReader.loadRepository(owner, repo);
        if (!githubDocs.isEmpty()) {
            pgvectorVectorStore.add(githubDocs);
            log.info("GitHub 文档已加入向量库，共 {} 条", githubDocs.size());
        }

        String githubPrompt = loadSystemPrompt() +
                "\n\n## GitHub 知识库使用规则\n" +
                "1. 你将收到来自 GitHub 仓库的文档内容（README、Issues、仓库信息）\n" +
                "2. 必须严格基于这些内容回答问题\n" +
                "3. 如果涉及 Issue 编号，在回答中引用\n" +
                "4. 不确定的内容不要编造";

        String rewrittenMessage = queryReweiter.doQueryReweiter(message);

        ChatResponse chatResponse = chatClient
                .prompt()
                .system(githubPrompt)
                .user(rewrittenMessage)
                .advisors(spec -> spec
                        .param("chat_memory_conversation_id", chatId)
                        .advisors(
                                RetrievalAugmentationAdvisor.builder()
                                        .documentRetriever(new VectorStoreDocumentRetriever(
                                                pgvectorVectorStore, 0.3, 6, () -> null))
                                        .build()
                        )
                )
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("github rag content:{}", content);
        return content;
    }

    /**
     * 增强版 RAG 对话（支持元信息过滤）
     */
    public String doChatWithEnhancedRag(RagRequest request) {
        log.info("doChatWithEnhancedRag 调用 - chatId: {}, status: {}, sourceType: {}, category: {}",
                request.getChatId(), request.getStatus(), request.getSourceType(), request.getCategory());
        if (request.getChatId() == null || request.getChatId().trim().isEmpty()) {
            throw new IllegalArgumentException("chatId 不能为空");
        }

        String rewrittenMessage = queryReweiter.doQueryReweiter(request.getMessage());

        // 根据参数动态构建过滤条件
        Advisor ragAdvisor = LoveAppRagCustomAdvisorFactory.createEnhancedRagAdvisor(
                pgvectorVectorStore,
                request.getStatus(),
                request.getSourceType(),
                request.getCategory(),
                0.3,
                6
        );

        ChatResponse chatResponse = chatClient
                .prompt()
                .system(buildRagSystemPrompt())
                .user(rewrittenMessage)
                .advisors(spec -> spec
                        .param("chat_memory_conversation_id", request.getChatId())
                        .advisors(ragAdvisor)
                )
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("enhanced rag content:{}", content);
        return content;
    }
}

