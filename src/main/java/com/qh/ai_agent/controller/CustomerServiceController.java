package com.qh.ai_agent.controller;

import com.qh.ai_agent.agent.customer_service.CustomerServiceAgent;
import com.qh.ai_agent.agent.customer_service.CustomerServiceTools;
import com.qh.ai_agent.dialog.DialogStateManager;
import com.qh.ai_agent.dialog.model.DialogState;
import com.qh.ai_agent.intent.IntentClassifier;
import com.qh.ai_agent.intent.IntentConfig;
import com.qh.ai_agent.service.AnalyticsService;
import com.qh.ai_agent.service.ConversationService;
import com.qh.ai_agent.service.PromptTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * 智能客服 API
 */
@Slf4j
@RestController
@RequestMapping("/customer-service")
@RequiredArgsConstructor
public class CustomerServiceController {

    private final ChatModel dashscopeChatModel;
    private final CustomerServiceTools customerServiceTools;
    private final IntentClassifier intentClassifier;
    private final DialogStateManager dialogStateManager;
    private final IntentConfig intentConfig;
    private final PromptTemplateService promptTemplateService;
    private final ConversationService conversationService;
    private final AnalyticsService analyticsService;

    /**
     * 客服对话（SSE 流式）
     */
    @GetMapping("/chat")
    public SseEmitter chat(@RequestParam String message,
                           @RequestParam(required = false) String sessionId) {
        log.info("[CS-Controller] 收到消息: {}, sessionId: {}", message, sessionId);

        ToolCallback[] tools = ToolCallbacks.from(customerServiceTools);

        CustomerServiceAgent agent = new CustomerServiceAgent(
                tools, dashscopeChatModel,
                intentClassifier, dialogStateManager, intentConfig, promptTemplateService
        );
        agent.initSession(sessionId);

        SseEmitter emitter = agent.runStream(message);

        final String sid = agent.getSessionId();
        try {
            emitter.send(SseEmitter.event().data("{\"type\":\"session_id\",\"content\":\"" + sid + "\"}"));
        } catch (Exception e) {
            log.warn("发送 sessionId 失败", e);
        }

        // 异步持久化：对话结束后保存消息和分析数据
        emitter.onCompletion(() -> persistDialogData(sid, message, agent));
        emitter.onTimeout(() -> persistDialogData(sid, message, agent));

        return emitter;
    }

    /**
     * 获取当前对话状态（调试用）
     */
    @GetMapping("/session/{sessionId}/state")
    public DialogState getSessionState(@PathVariable String sessionId) {
        return dialogStateManager.getState(sessionId);
    }

    /** 持久化对话数据（用户消息 + AI 回复 + 分析字段） */
    private void persistDialogData(String sessionId, String userMessage, CustomerServiceAgent agent) {
        try {
            // 确保会话存在
            if (conversationService.getById(sessionId) == null) {
                conversationService.create(sessionId, null, "客服对话", "customer_service");
            }

            // 保存用户消息
            conversationService.addMessage(sessionId, "user", userMessage);

            // 从 DialogState 获取意图信息
            DialogState state = dialogStateManager.getState(sessionId);
            String intent = null;
            String slots = null;
            Double confidence = null;
            boolean isHandoff = false;
            boolean isClarification = false;

            if (state != null) {
                if (state.getCurrentIntent() != null) {
                    intent = state.getCurrentIntent().name();
                }
                if (state.getFilledSlots() != null && !state.getFilledSlots().isEmpty()) {
                    slots = state.getFilledSlots().toString();
                }
                confidence = state.getConfidenceScore();
                isHandoff = state.getDialogPhase() != null &&
                        "HANDOFF".equals(state.getDialogPhase().name());
                isClarification = state.getDialogPhase() != null &&
                        "CLARIFYING".equals(state.getDialogPhase().name());
            }

            // 从 Agent 消息列表获取最后的 AI 回复
            String assistantReply = getLastAssistantMessage(agent);
            if (assistantReply != null) {
                conversationService.addMessage(sessionId, "assistant", assistantReply,
                        intent, slots, confidence, isHandoff, isClarification, null);
            }

            // 更新会话分析聚合
            analyticsService.updateAnalytics(sessionId);

            log.info("[CS-Controller] 对话数据持久化完成: sessionId={}, intent={}", sessionId, intent);
        } catch (Exception e) {
            log.error("[CS-Controller] 持久化对话数据失败: sessionId={}", sessionId, e);
        }
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
}
