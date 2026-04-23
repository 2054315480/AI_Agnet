package com.qh.ai_agent.agent.customer_service;

import com.qh.ai_agent.Agent.Model.AgentState;
import com.qh.ai_agent.Agent.ToolCallAgent;
import com.qh.ai_agent.dialog.DialogStateManager;
import com.qh.ai_agent.dialog.model.DialogPhase;
import com.qh.ai_agent.dialog.model.DialogState;
import com.qh.ai_agent.intent.IntentClassifier;
import com.qh.ai_agent.intent.IntentConfig;
import com.qh.ai_agent.intent.model.CustomerIntent;
import com.qh.ai_agent.intent.model.IntentResult;
import com.qh.ai_agent.intent.model.SlotDefinition;
import com.qh.ai_agent.service.PromptTemplateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 智能客服 Agent v2：集成意图识别、槽位填充、澄清、指代消解、拒识转人工、出处追踪
 */
@Slf4j
public class CustomerServiceAgent extends ToolCallAgent {

    private final IntentClassifier intentClassifier;
    private final DialogStateManager dialogStateManager;
    private final IntentConfig intentConfig;
    private final PromptTemplateService promptTemplateService;
    private final ChatModel chatModel;

    private String sessionId;
    private DialogState dialogState;

    private static final double MIN_CONFIDENCE = 0.5;
    /** 转人工阈值：连续超范围轮数 */
    private static final int HANDOFF_OUT_OF_SCOPE_THRESHOLD = 2;
    /** 转人工阈值：对话最大轮次 */
    private static final int MAX_TURNS_BEFORE_HANDOFF = 15;

    public CustomerServiceAgent(ToolCallback[] availableTools,
                                ChatModel chatModel,
                                IntentClassifier intentClassifier,
                                DialogStateManager dialogStateManager,
                                IntentConfig intentConfig,
                                PromptTemplateService promptTemplateService) {
        super(availableTools, chatModel);
        this.chatModel = chatModel;
        this.intentClassifier = intentClassifier;
        this.dialogStateManager = dialogStateManager;
        this.intentConfig = intentConfig;
        this.promptTemplateService = promptTemplateService;
        this.setName("CustomerServiceAgent");
        this.setState(AgentState.RUNNING);
    }

    public void initSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            DialogState newState = dialogStateManager.createSession();
            this.sessionId = newState.getSessionId();
        } else {
            this.sessionId = sessionId;
        }
        this.dialogState = dialogStateManager.getOrCreateState(this.sessionId);

        String systemPrompt = promptTemplateService.loadTemplate("customer-service")
                .renderWithDefaults(Map.of());
        this.setSystemPrompt(systemPrompt);
        this.setNextStepPrompt("请根据用户的问题，选择合适的工具来查询信息或处理请求。如果工具返回的 matchType 是 rag_search，回答时必须标注来源出处；如果是 faq_direct，直接回答无需标注来源。如果已充分回答，直接给出最终答案。");
        this.setMaxStep(10);
    }

    @Override
    public String step() {
        try {
            String lastUserInput = getLastUserMessage();
            if (lastUserInput == null || lastUserInput.isBlank()) {
                return "无用户输入";
            }

            // 后续轮次直接走 think→act（意图识别仅在首轮执行）
            if (getCurrentStep() > 1) {
                boolean shouldAct = think();
                if (!shouldAct) {
                    setState(AgentState.FINISHED);
                    return "思考完成 - 无需行动";
                }
                return actAndExtractSuggestions();
            }

            // === 阶段二增强：指代消解 + 上下文补全 ===
            String resolvedInput = resolveReferences(lastUserInput);

            // === 意图识别 + 槽位提取 ===
            IntentResult intentResult = intentClassifier.classify(
                    resolvedInput,
                    dialogState != null ? dialogState.getFilledSlots() : null
            );

            // 更新对话状态
            dialogState = dialogStateManager.updateFromIntentResult(sessionId, intentResult);
            sendSseEvent("intent", formatIntentEvent(intentResult));

            // === 拒识与转人工检查 ===
            if (shouldHandoff(intentResult)) {
                String reason = buildHandoffReason(intentResult);
                sendSseEvent("handoff", reason);
                sendSseEvent("answer", "很抱歉，" + reason + "\n\n正在为您转接人工客服，请稍候...\n\n您也可以先浏览我们的常见问题。");
                setState(AgentState.FINISHED);
                return "转人工处理：" + reason;
            }

            // === 超范围但未达转人工阈值 ===
            if (intentResult.getIntent() == CustomerIntent.OUT_OF_SCOPE) {
                String outOfScopeReply = "抱歉，这个问题超出了我的服务范围。我可以帮您处理订单查询、产品咨询、退款退货、售后保修等问题。\n\n请问有以上相关的问题需要帮助吗？如需其他帮助，可以说「转人工」。";
                sendSseEvent("answer", outOfScopeReply);
                setState(AgentState.FINISHED);
                return "超范围回复";
            }

            // === 增强澄清 ===
            if (intentResult.isNeedsClarification() || needsClarification(intentResult)) {
                String clarification = buildClarification(intentResult);
                dialogState.setDialogPhase(DialogPhase.CLARIFYING);
                dialogStateManager.updateState(sessionId, dialogState);
                sendSseEvent("clarification", clarification);
                sendSseEvent("answer", clarification);
                setState(AgentState.FINISHED);
                return "澄清问题：" + clarification;
            }

            // === 闲聊 ===
            if (intentResult.getIntent() == CustomerIntent.CHITCHAT) {
                sendSseEvent("answer", "您好！我是智能客服，很高兴为您服务。请问有什么可以帮您的？您可以问我订单、产品、退换货、保修等相关问题。");
                setState(AgentState.FINISHED);
                return "寒暄回复";
            }

            // === 正常 think → act ===
            dialogState.setDialogPhase(DialogPhase.RETRIEVING);
            dialogStateManager.updateState(sessionId, dialogState);

            boolean shouldAct = think();
            if (!shouldAct) {
                setState(AgentState.FINISHED);
                return "思考完成 - 无需行动";
            }
            return actAndExtractSuggestions();

        } catch (Exception e) {
            log.error("[CustomerServiceAgent] step 执行失败", e);
            setState(AgentState.ERROR);
            return "步骤执行失败：" + e.getMessage();
        }
    }

    /**
     * 执行工具调用并从 searchKnowledgeBase 结果中提取推荐问题
     */
    private String actAndExtractSuggestions() {
        String result = act();
        // 从工具结果中解析推荐问题
        extractAndSendSuggestedQuestions(result);
        return result;
    }

    /**
     * 从工具结果 JSON 中提取 suggestedQuestions 并通过 SSE 推送
     */
    private void extractAndSendSuggestedQuestions(String toolResult) {
        if (toolResult == null || !toolResult.contains("suggestedQuestions")) return;

        try {
            // 简单 JSON 提取：找到 suggestedQuestions 数组
            int idx = toolResult.indexOf("\"suggestedQuestions\":[");
            if (idx < 0) return;
            int start = toolResult.indexOf("[", idx);
            int end = findMatchingBracket(toolResult, start);
            if (end < 0) return;

            String arrayStr = toolResult.substring(start, end + 1);
            // 提取引号内的字符串
            List<String> questions = new ArrayList<>();
            Pattern p = Pattern.compile("\"([^\"]+)\"");
            Matcher m = p.matcher(arrayStr);
            while (m.find()) {
                questions.add(m.group(1));
            }

            if (!questions.isEmpty()) {
                // 构建 JSON 数组推送
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < questions.size(); i++) {
                    if (i > 0) sb.append(",");
                    sb.append("\"").append(questions.get(i).replace("\"", "\\\"")).append("\"");
                }
                sb.append("]");
                sendSseEvent("suggested_questions", sb.toString());
            }
        } catch (Exception e) {
            log.warn("[CustomerServiceAgent] 提取推荐问题失败", e);
        }
    }

    private int findMatchingBracket(String s, int openPos) {
        int depth = 0;
        for (int i = openPos; i < s.length(); i++) {
            if (s.charAt(i) == '[') depth++;
            else if (s.charAt(i) == ']') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    // ==================== 指代消解 ====================

    /** 中文指代词 */
    private static final Pattern REFERENCE_PATTERN = Pattern.compile("(它|这个|那个|这|那|他|她|这件|那件|这台|那台)");
    /** 省略检测：用户输入很短（<6字）且不含完整语义 */
    private static final int SHORT_INPUT_THRESHOLD = 6;

    /**
     * 指代消解与上下文补全
     * 使用规则 + LLM 双通道处理
     */
    private String resolveReferences(String userInput) {
        if (dialogState == null) return userInput;

        String resolved = userInput;
        boolean hasReference = REFERENCE_PATTERN.matcher(userInput).find();
        boolean isShortInput = userInput.length() < SHORT_INPUT_THRESHOLD && !userInput.matches(".*[。？！].*");

        // 话题切换检测：如果意图明确变化，不需要消解
        if (hasReference || isShortInput) {
            // 有上下文信息可供消解
            if (!dialogState.getFilledSlots().isEmpty() || dialogState.getCurrentIntent() != null) {
                String contextInfo = buildContextForResolution();
                if (!contextInfo.isEmpty()) {
                    resolved = resolveWithLlm(userInput, contextInfo);
                    log.info("[ReferenceResolution] '{}' → '{}' (context: {})", userInput, resolved, contextInfo);
                }
            }
        }

        return resolved;
    }

    /** 构建消解用的上下文信息 */
    private String buildContextForResolution() {
        StringBuilder sb = new StringBuilder();
        if (dialogState.getCurrentIntent() != null) {
            sb.append("当前意图：").append(dialogState.getCurrentIntent().getDisplayName()).append("；");
        }
        if (!dialogState.getFilledSlots().isEmpty()) {
            sb.append("已知信息：");
            dialogState.getFilledSlots().forEach((k, v) -> sb.append(k).append("=").append(v).append("，"));
        }
        return sb.toString();
    }

    /** 使用 LLM 做指代消解 */
    private String resolveWithLlm(String userInput, String contextInfo) {
        try {
            String prompt = String.format(
                    "你是一个对话上下文消解助手。根据对话上下文信息，将用户输入中的指代词（它、这个、那个等）替换为具体实体，" +
                    "补全省略的信息。只输出消解后的完整用户输入，不要添加任何解释。\n\n" +
                    "上下文：%s\n用户输入：%s\n消解后：",
                    contextInfo, userInput
            );
            List<Message> messages = List.of(new UserMessage(prompt));
            ChatResponse response = chatModel.call(new Prompt(messages));
            String result = response.getResult().getOutput().getText();
            if (result != null && !result.isBlank() && result.length() > userInput.length()) {
                return result.trim();
            }
        } catch (Exception e) {
            log.warn("[ReferenceResolution] LLM 消解失败，使用原始输入", e);
        }
        return userInput;
    }

    // ==================== 拒识与转人工 ====================

    /**
     * 判断是否应转人工
     */
    private boolean shouldHandoff(IntentResult result) {
        // 1. 用户明确要求转人工
        if (result.getIntent() == CustomerIntent.REQUEST_HUMAN) {
            return true;
        }
        // 2. 连续多轮超范围
        if (dialogState != null && dialogState.getConsecutiveOutOfScope() >= HANDOFF_OUT_OF_SCOPE_THRESHOLD) {
            return true;
        }
        // 3. 对话轮次过长
        if (dialogState != null && dialogState.getTurnCount() > MAX_TURNS_BEFORE_HANDOFF) {
            return true;
        }
        // 4. DialogPhase 已标记 HANDOFF
        if (dialogState != null && dialogState.getDialogPhase() == DialogPhase.HANDOFF) {
            return true;
        }
        return false;
    }

    private String buildHandoffReason(IntentResult result) {
        if (result.getIntent() == CustomerIntent.REQUEST_HUMAN) {
            return "您要求转接人工客服。";
        }
        if (dialogState != null && dialogState.getConsecutiveOutOfScope() >= HANDOFF_OUT_OF_SCOPE_THRESHOLD) {
            return "您的问题可能需要人工客服进一步处理。";
        }
        if (dialogState != null && dialogState.getTurnCount() > MAX_TURNS_BEFORE_HANDOFF) {
            return "对话轮次较多，为您转接人工客服以更好地解决问题。";
        }
        return "这个问题超出了我的服务范围。";
    }

    // ==================== 增强澄清 ====================

    private boolean needsClarification(IntentResult result) {
        if (result.getIntent() == CustomerIntent.OUT_OF_SCOPE
                || result.getIntent() == CustomerIntent.CHITCHAT
                || result.getIntent() == CustomerIntent.REQUEST_HUMAN
                || result.getIntent() == CustomerIntent.POLICY_QUERY
                || result.getIntent() == CustomerIntent.PRODUCT_RECOMMEND) {
            return false;
        }
        if (result.getConfidence() < MIN_CONFIDENCE) {
            return true;
        }
        List<SlotDefinition> slotDefs = intentConfig.getSlotDefinitions(result.getIntent().name());
        List<SlotDefinition> missing = dialogState.getMissingSlots(slotDefs);
        return !missing.isEmpty();
    }

    private String buildClarification(IntentResult result) {
        // 置信度低 → 让用户确认意图
        if (result.getConfidence() < MIN_CONFIDENCE) {
            return "抱歉，我不太确定您的意思。请问您是想：\n" +
                    "1. 查询订单\n2. 咨询产品\n3. 申请退款/退货\n4. 了解售后保修政策\n5. 其他问题\n\n" +
                    "请告诉我您的具体需求，或直接说「转人工」联系人工客服。";
        }

        // 缺少槽位 → 追问
        List<SlotDefinition> slotDefs = intentConfig.getSlotDefinitions(result.getIntent().name());
        List<SlotDefinition> missing = dialogState.getMissingSlots(slotDefs);
        if (!missing.isEmpty()) {
            // 一次追问所有缺失槽位（提升效率）
            if (missing.size() == 1) {
                SlotDefinition missingSlot = missing.get(0);
                dialogState.setLastClarificationSlot(missingSlot.getSlotName());
                return intentConfig.getClarificationQuestion(missingSlot.getSlotName());
            } else {
                // 多个缺失槽位，逐个追问
                dialogState.setLastClarificationSlot(missing.get(0).getSlotName());
                StringBuilder sb = new StringBuilder("为了更好地帮助您，请提供以下信息：\n");
                for (int i = 0; i < missing.size(); i++) {
                    sb.append(i + 1).append(". ").append(missing.get(i).getDescription()).append("\n");
                }
                sb.append("\n您也可以一次告诉我所有信息。");
                return sb.toString();
            }
        }

        return "请提供更多信息以便我为您服务。";
    }

    // ==================== 工具方法 ====================

    private String formatIntentEvent(IntentResult result) {
        return "{\"intent\":\"" + result.getIntent().name()
                + "\",\"displayName\":\"" + result.getIntent().getDisplayName()
                + "\",\"confidence\":" + String.format("%.2f", result.getConfidence())
                + ",\"slots\":" + formatSlots(result.getSlots()) + "}";
    }

    private String formatSlots(Map<String, String> slots) {
        if (slots == null || slots.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> e : slots.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(e.getKey()).append("\":\"").append(e.getValue()).append("\"");
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    private String getLastUserMessage() {
        for (int i = getMessagesList().size() - 1; i >= 0; i--) {
            if (getMessagesList().get(i) instanceof UserMessage um) {
                return um.getText();
            }
        }
        return null;
    }

    public String getSessionId() { return sessionId; }

    @Override
    public SseEmitter runStream(String userPrompt) {
        if (this.sessionId == null) initSession(null);
        return super.runStream(userPrompt);
    }

    @Override
    public SseEmitter runStream(String userPrompt, List<Media> mediaList) {
        if (this.sessionId == null) initSession(null);
        return super.runStream(userPrompt, mediaList);
    }
}
