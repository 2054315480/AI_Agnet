package com.qh.ai_agent.controller;

import com.qh.ai_agent.Agent.customer_service.CustomerServiceAgent;
import com.qh.ai_agent.Agent.customer_service.CustomerServiceTools;
import com.qh.ai_agent.dialog.DialogStateManager;
import com.qh.ai_agent.dialog.model.DialogState;
import com.qh.ai_agent.dto.BatchTestResult;
import com.qh.ai_agent.dto.BatchTestResult.QuestionTestResult;
import com.qh.ai_agent.dto.BatchTestResult.SearchHit;
import com.qh.ai_agent.intent.IntentClassifier;
import com.qh.ai_agent.intent.IntentConfig;
import com.qh.ai_agent.knowledge.KnowledgeSearchService;
import com.qh.ai_agent.knowledge.KnowledgeSearchService.KnowledgeSearchResult;
import com.qh.ai_agent.knowledge.parser.KbEntry;
import com.qh.ai_agent.knowledge.parser.KbParser;
import com.qh.ai_agent.knowledge.parser.KbParserFactory;
import com.qh.ai_agent.mapper.DialogAnalyticsMapper;
import com.qh.ai_agent.service.AnalyticsService;
import com.qh.ai_agent.service.ConversationService;
import com.qh.ai_agent.service.PromptTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 智能客服 API
 */
@Slf4j
@RestController
@RequestMapping("/customer-service")
@RequiredArgsConstructor
public class CustomerServiceController {

    private final ChatModel openAiChatModel;
    private final CustomerServiceTools customerServiceTools;
    private final IntentClassifier intentClassifier;
    private final DialogStateManager dialogStateManager;
    private final IntentConfig intentConfig;
    private final PromptTemplateService promptTemplateService;
    private final ConversationService conversationService;
    private final AnalyticsService analyticsService;
    private final DialogAnalyticsMapper dialogAnalyticsMapper;
    private final KbParserFactory kbParserFactory;
    private final KnowledgeSearchService knowledgeSearchService;

    /**
     * 客服对话（SSE 流式）
     */
    @GetMapping("/chat")
    public SseEmitter chat(@RequestParam String message,
                           @RequestParam(required = false) String sessionId,
                           @org.springframework.security.core.annotation.AuthenticationPrincipal
                           com.qh.ai_agent.entity.User currentUser) {
        log.info("[CS-Controller] 收到消息: {}, sessionId: {}", message, sessionId);

        ToolCallback[] tools = ToolCallbacks.from(customerServiceTools);

        CustomerServiceAgent agent = new CustomerServiceAgent(
                tools, openAiChatModel,
                intentClassifier, dialogStateManager, intentConfig, promptTemplateService
        );
        agent.initSession(sessionId);

        final String sid = agent.getSessionId();

        // 关键修复：在 runStream 之前注册完成回调，确保 async 任务完成后必定触发持久化
        // 之前用 SseEmitter.onCompletion 存在竞态条件：async 任务可能在回调注册前就完成
        // 两段式落库：请求进来先写用户消息（中途断开也不丢），本轮结束后再补写回复与分析
        persistUserTurn(sid, message, currentUser != null ? currentUser.getId() : null);
        agent.setOnCompleteHook(() -> persistAssistantTurn(sid, agent));

        SseEmitter emitter = agent.runStream(message);

        try {
            emitter.send(SseEmitter.event().data("{\"type\":\"session_id\",\"content\":\"" + sid + "\"}"));
        } catch (Exception e) {
            log.warn("发送 sessionId 失败", e);
        }

        return emitter;
    }

    /**
     * 批量测试 RAG 知识库检索效果。
     * 上传与知识库同格式的文件（MD/DOCX/PDF），解析出所有 QA 对，
     * 逐个用问题检索知识库，对比预期答案是否命中。
     */
    @PostMapping("/batch-test")
    public BatchTestResult batchTest(@RequestParam("file") MultipartFile file,
                                     @RequestParam(value = "domain", defaultValue = "test") String domain) {
        long startTotal = System.currentTimeMillis();
        String filename = file.getOriginalFilename();
        log.info("[CS-BatchTest] 开始批量测试: file={}, domain={}, size={}", filename, domain, file.getSize());

        // 解析上传文件
        KbParser parser = kbParserFactory.getParser(filename);
        if (parser == null) {
            throw new IllegalArgumentException("不支持的文件类型: " + filename + "，仅支持 .md / .docx / .pdf");
        }

        List<KbEntry> entries;
        try {
            Path tmpFile = Files.createTempFile("batch-test-", "." + extractExt(filename));
            file.transferTo(tmpFile.toFile());
            entries = parser.parse(tmpFile.toFile(), domain);
            Files.deleteIfExists(tmpFile);
        } catch (IOException e) {
            throw new RuntimeException("文件解析失败: " + e.getMessage(), e);
        }

        log.info("[CS-BatchTest] 解析完成: {} 条 QA", entries.size());

        // 逐条检索
        List<QuestionTestResult> results = new ArrayList<>();
        for (KbEntry entry : entries) {
            long startQ = System.currentTimeMillis();
            String query = entry.getQuestion();
            if (query == null || query.isBlank()) continue;

            KnowledgeSearchService.HybridSearchResult sr = knowledgeSearchService.hybridSearch(query, 3);
            List<SearchHit> hits = sr.results().stream()
                    .map(r -> new SearchHit(r.content(), r.source(), r.kbId(), r.docType(), r.category(), r.relevance()))
                    .toList();

            // 判断是否命中预期条目：预期 kbId 出现在搜索结果中
            String expectedKbId = entry.getKbId();
            boolean matched = expectedKbId != null && !expectedKbId.isBlank()
                    && sr.results().stream().anyMatch(r -> expectedKbId.equals(r.kbId()));

            double topRelevance = sr.results().isEmpty() ? 0.0 : sr.results().get(0).relevance();
            long qElapsed = System.currentTimeMillis() - startQ;

            results.add(new QuestionTestResult(
                    query,
                    entry.getAnswer(),
                    expectedKbId,
                    entry.getDomain(),
                    entry.getCategory(),
                    hits,
                    matched,
                    topRelevance,
                    qElapsed
            ));
        }

        long totalElapsed = System.currentTimeMillis() - startTotal;
        log.info("[CS-BatchTest] 测试完成: {} 条, 命中 {}/{}, 总耗时 {}ms",
                results.size(),
                results.stream().filter(QuestionTestResult::matched).count(),
                results.size(),
                totalElapsed);

        return new BatchTestResult(filename, results.size(), totalElapsed, results);
    }

    private String extractExt(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(dot) : "";
    }

    /**
     * 获取当前对话状态（调试用）
     */
    @GetMapping("/session/{sessionId}/state")
    public DialogState getSessionState(@PathVariable String sessionId) {
        return dialogStateManager.getState(sessionId);
    }

    /** 用用户第一句话生成会话标题（压缩空白，超长截断） */
    private String buildConversationTitle(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return "客服对话";
        }
        String title = userMessage.replaceAll("\\s+", " ").trim();
        if (title.length() > 20) {
            title = title.substring(0, 20) + "...";
        }
        return title;
    }

    /** 第一段：请求进入时立即写用户消息，避免中途断开导致整轮丢失 */
    private void persistUserTurn(String sessionId, String userMessage, Long userId) {
        try {
            // 确保会话存在：用用户第一句话做标题，避免分析页列表里全是"客服对话"
            // 同时绑定当前登录用户，否则会话在侧边栏列表里查不到
            if (conversationService.getById(sessionId) == null) {
                conversationService.create(sessionId, userId, buildConversationTitle(userMessage), "customer_service");
            }
            conversationService.addMessage(sessionId, "user", userMessage);
            log.info("[CS-Controller] 用户消息已落库: sessionId={}", sessionId);
        } catch (Exception e) {
            log.error("[CS-Controller] 用户消息落库失败: sessionId={}", sessionId, e);
        }
    }

    /** 第二段：本轮结束后写助手回复（含意图/槽位/置信度/出处）并更新分析聚合 */
    private void persistAssistantTurn(String sessionId, CustomerServiceAgent agent) {
        try {

            // 从 DialogState 获取意图信息
            DialogState state = dialogStateManager.getState(sessionId);
            String intent = null;
            java.util.Map<String, String> slots = null;
            Double confidence = null;
            boolean isHandoff = false;
            boolean isClarification = false;

            if (state != null) {
                if (state.getCurrentIntent() != null) {
                    intent = state.getCurrentIntent().name();
                }
                if (state.getFilledSlots() != null && !state.getFilledSlots().isEmpty()) {
                    slots = state.getFilledSlots();
                }
                confidence = state.getConfidenceScore();
                // 双重判断转人工：DialogPhase + REQUEST_HUMAN 意图（防止状态漏设）
                isHandoff = (state.getDialogPhase() != null &&
                        "HANDOFF".equals(state.getDialogPhase().name()))
                        || "REQUEST_HUMAN".equals(intent);
                // 澄清阶段包括 CLARIFYING 和 SLOT_FILLING（槽位填充追问也属于澄清）
                isClarification = state.isSlotFillingOrClarifying();
            } else {
                log.warn("[CS-Controller] DialogState 为空！sessionId={}，意图数据将丢失", sessionId);
            }

            // 从 Agent 消息列表获取最后的 AI 回复
            String assistantReply = getLastAssistantMessage(agent);
            if (assistantReply != null) {
                // 出处落库：供分析页/会话抽屉在重新加载后仍能看到引用来源
                java.util.Map<String, Object> sources = null;
                java.util.List<String> sourceList = agent.getCurrentSources();
                if (sourceList != null && !sourceList.isEmpty()) {
                    sources = java.util.Map.of("items", sourceList);
                }
                conversationService.addMessage(sessionId, "assistant", assistantReply,
                        intent, slots, confidence, isHandoff, isClarification, sources);
            }

            // 直接 SQL upsert 聚合分析（绕过 ORM，确保可靠写入）
            dialogAnalyticsMapper.upsertAnalyticsDirect(sessionId);

            log.info("[CS-Controller] 助手回复与分析已落库: sessionId={}, intent={}, isHandoff={}, sources={}",
                    sessionId, intent, isHandoff, sourceList(agent));
        } catch (Exception e) {
            log.error("[CS-Controller] 助手回复落库失败: sessionId={}", sessionId, e);
        }
    }

    private int sourceList(CustomerServiceAgent agent) {
        return agent.getCurrentSources().size();
    }

    private String getLastAssistantMessage(CustomerServiceAgent agent) {
        for (int i = agent.getMessagesList().size() - 1; i >= 0; i--) {
            if (agent.getMessagesList().get(i) instanceof
                    org.springframework.ai.chat.messages.AssistantMessage am) {
                return am.getText();
            }
        }
        return null;
    }

    /**
     * 关闭会话：状态机置为 CLOSED；下次再发消息会自动重新初始化。
     */
    @PostMapping("/session/{sessionId}/close")
    public Map<String, Object> closeSession(@PathVariable String sessionId) {
        log.info("[CS-Controller] 关闭会话: {}", sessionId);
        dialogStateManager.closeSession(sessionId);
        return Map.of("sessionId", sessionId, "closed", true);
    }

}
