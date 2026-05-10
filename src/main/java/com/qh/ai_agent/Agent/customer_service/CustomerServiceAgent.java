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
import com.qh.ai_agent.intent.model.SlotExtractionResult;
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
    /** 当前用户原始问题（用于注入搜索指令） */
    private String currentUserQuery;

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
        this.setNextStepPrompt("请根据用户的问题，选择合适的工具来查询信息或处理请求。" +
                "核心原则：知识库最优先！任何用户问题都必须先用 searchKnowledgeBase 搜索知识库。" +
                "铁律：工具返回的 results[].content 字段内容必须逐字原样输出给用户，一字不改！" +
                "你只是搬运工，不是编辑——禁止对知识库内容做任何改写、总结、归纳、重组、合并、美化。" +
                "调用 searchKnowledgeBase 时，必须使用用户的完整原始问题作为查询词，禁止简化为关键词！" +
                "所有回答必须标注来源出处，格式为 [来源：文档名] 和 [条目编号:kbId值]。" +
                "如果工具返回了 matchType=faq_direct，说明是 FAQ 精确匹配，直接引用答案并标注来源。" +
                "如果工具返回了 matchType=hybrid_search，说明是知识库语义检索，直接引用答案并标注来源。" +
                "重要：知识库内容中如出现 {{xxx}} 格式的模板占位符，请原样输出，不要解释为数据缺失或无法获取。" +
                "回答流程：1)先输出知识库原文 → 2)标注来源 → 3)如确需用户补充信息（如订单号），在最后轻声追问。" +
                "严禁在未搜索知识库的情况下，直接向用户索取订单号、产品名等信息！" +
                "如果知识库未找到相关内容，如实告知用户并建议转人工。" +
                "如果已充分回答，直接给出最终答案。");
        this.setMaxStep(10);
    }

    @Override
    public String step() {
        try {
            String lastUserInput = getLastUserMessage();
            if (lastUserInput == null || lastUserInput.isBlank()) {
                return "无用户输入";
            }
            this.currentUserQuery = lastUserInput;

            // 后续轮次直接走 think→act（意图识别仅在首轮执行）
            if (getCurrentStep() > 1) {
                injectSearchQueryHint();
                boolean shouldAct = think();
                if (!shouldAct) {
                    setState(AgentState.FINISHED);
                    return "思考完成 - 无需行动";
                }
                return actAndExtractSuggestions();
            }

            // === 槽位填充模式：当处于 SLOT_FILLING / CLARIFYING 阶段时，跳过完整意图识别 ===
            if (dialogState != null && dialogState.isSlotFillingOrClarifying()
                    && dialogState.getCurrentIntent() != null) {
                log.info("[SlotFilling] 进入槽位填充模式，当前意图={}, 阶段={}",
                        dialogState.getCurrentIntent(), dialogState.getDialogPhase());

                List<SlotDefinition> slotDefs = intentConfig.getSlotDefinitions(
                        dialogState.getCurrentIntent().name());

                // 使用轻量级槽位提取
                SlotExtractionResult extractionResult = intentClassifier.extractSlots(
                        lastUserInput,
                        dialogState.getLastBotMessage(),
                        dialogState.getCurrentIntent(),
                        dialogState.getFilledSlots(),
                        dialogState.getPendingSlots()
                );

                if (extractionResult.isTopicChange()) {
                    // 用户切换话题，跳出槽位填充，走正常意图识别
                    log.info("[SlotFilling] 检测到话题切换，进入正常意图识别");
                    dialogState.setDialogPhase(DialogPhase.GREETING);
                    dialogStateManager.updateState(sessionId, dialogState);
                    // 继续往下走正常意图识别流程
                } else if (extractionResult.hasExtracted()) {
                    // 合并提取到的槽位
                    dialogState.getFilledSlots().putAll(extractionResult.getExtracted());
                    dialogState.updatePendingSlots(slotDefs);
                    dialogState.setTurnCount(dialogState.getTurnCount() + 1);
                    dialogStateManager.updateState(sessionId, dialogState);

                    log.info("[SlotFilling] 提取到槽位: {}, 剩余待填充: {}",
                            extractionResult.getExtracted(), dialogState.getPendingSlots());

                    List<SlotDefinition> missing = dialogState.getMissingSlots(slotDefs);
                    if (missing.isEmpty()) {
                        // 所有槽位已填满 → 进入 think→act
                        dialogState.setDialogPhase(DialogPhase.RETRIEVING);
                        dialogStateManager.updateState(sessionId, dialogState);
                        sendSseEvent("intent", formatIntentEvent(IntentResult.builder()
                                .intent(dialogState.getCurrentIntent())
                                .confidence(dialogState.getConfidenceScore())
                                .slots(dialogState.getFilledSlots())
                                .build()));
                        injectSearchQueryHint();
                        boolean shouldAct = think();
                        if (!shouldAct) {
                            setState(AgentState.FINISHED);
                            return "思考完成 - 无需行动";
                        }
                        return actAndExtractSuggestions();
                    } else {
                        // 还有缺失槽位 → 继续追问
                        dialogState.setDialogPhase(DialogPhase.SLOT_FILLING);
                        dialogStateManager.updateState(sessionId, dialogState);
                        String clarification = buildClarificationForMissing(missing);
                        sendAnswerAndSaveContext(clarification);
                        setState(AgentState.FINISHED);
                        return "槽位填充 - 继续追问：" + clarification;
                    }
                } else {
                    // 未提取到有效槽位，继续追问
                    dialogState.setTurnCount(dialogState.getTurnCount() + 1);
                    dialogStateManager.updateState(sessionId, dialogState);
                    String clarification = buildClarificationForMissing(
                            dialogState.getMissingSlots(slotDefs));
                    sendAnswerAndSaveContext(clarification);
                    setState(AgentState.FINISHED);
                    return "槽位填充 - 未提取到信息，重新追问";
                }
            }

            // === 阶段二增强：指代消解 + 上下文补全 ===
            String resolvedInput = resolveReferences(lastUserInput);

            // === 意图识别 + 槽位提取（传入对话状态） ===
            IntentResult intentResult = intentClassifier.classify(
                    resolvedInput,
                    dialogState != null ? dialogState.getFilledSlots() : null,
                    dialogState
            );

            // 更新对话状态
            dialogState = dialogStateManager.updateFromIntentResult(sessionId, intentResult);
            sendSseEvent("intent", formatIntentEvent(intentResult));

            // === 仅用户明确要求人工时立即转接，不搜索知识库 ===
            if (intentResult.getIntent() == CustomerIntent.REQUEST_HUMAN) {
                dialogState.setDialogPhase(DialogPhase.HANDOFF);
                dialogState.setHandoffTriggered(true);
                dialogStateManager.updateState(sessionId, dialogState);
                sendSseEvent("handoff", "用户要求转人工");
                sendAnswerAndSaveContext("正在为您转接人工客服，请稍候...");
                setState(AgentState.FINISHED);
                return "用户明确要求转人工";
            }

            // === 闲聊直接回复 ===
            if (intentResult.getIntent() == CustomerIntent.CHITCHAT) {
                sendAnswerAndSaveContext("您好！我是智能客服，很高兴为您服务。请问有什么可以帮您的？您可以问我订单、产品、退换货、保修等相关问题。");
                setState(AgentState.FINISHED);
                return "寒暄回复";
            }

            // === 知识库最优先：所有其他意图（包括 OUT_OF_SCOPE）都先走 think→act 搜索知识库 ===
            // 如果知识库有匹配 → 返回内容；如果知识库无匹配 → LLM 自行判断是否建议转人工
            dialogState.setDialogPhase(DialogPhase.RETRIEVING);
            dialogStateManager.updateState(sessionId, dialogState);

            injectSearchQueryHint();
            boolean shouldAct = think();
            if (!shouldAct) {
                setState(AgentState.FINISHED);
                return "思考完成 - 无需行动";
            }
            return actAndExtractSuggestions();

        } catch (Exception e) {
            log.error("[CustomerServiceAgent] step 执行失败", e);
            setState(AgentState.ERROR);
            sendSseEvent("answer", "抱歉，处理您的请求时发生了错误：" + e.getMessage());
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
        if (dialogState.getLastBotMessage() != null) {
            sb.append("系统上次提问：").append(dialogState.getLastBotMessage()).append("；");
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

    /**
     * 判断是否需要追问澄清。
     * 设计原则：知识库最优先。几乎所有意图都先走 think→act 搜索知识库，
     * LLM 看到知识库内容后自行判断是否需要追问。仅置信度过低时追问。
     */
    private boolean needsClarification(IntentResult result) {
        // 知识库优先：不再因缺少槽位而阻断搜索。让 LLM 先查知识库，再自行决定是否追问。
        // 仅当置信度过低（LLM 也不确定用户意图）时才追问。
        if (result.getConfidence() < MIN_CONFIDENCE) {
            return true;
        }
        // 所有明确意图都直接走 think→act，知识库搜索优先
        return false;
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

    /**
     * 在 think() 之前，将用户原始问题作为搜索指令注入到消息列表，
     * 强制 LLM 使用完整原始提问作为知识库查询词，防止简化为关键词导致匹配错误。
     */
    private void injectSearchQueryHint() {
        if (currentUserQuery == null || currentUserQuery.isBlank()) return;
        // 避免重复注入
        String hint = "【知识库搜索指令】调用 searchKnowledgeBase 时，查询词必须使用以下用户的原始完整问题，一字不改：「"
                + currentUserQuery + "」";
        // 检查是否已有相同指令
        for (int i = getMessagesList().size() - 1; i >= 0; i--) {
            String text = getMessagesList().get(i).getText();
            if (text != null && text.contains("【知识库搜索指令】")) {
                return; // 已注入，跳过
            }
        }
        getMessagesList().add(new UserMessage(hint));
        log.info("[CustomerServiceAgent] 注入搜索指令: {}", currentUserQuery);
    }

    /**
     * 发送 answer 事件并保存到 lastBotMessage（用于多轮上下文）
     * 同时将回复加入 messagesList，确保持久化能读取到
     */
    private void sendAnswerAndSaveContext(String content) {
        sendSseEvent("answer", content);
        // 加入 messagesList，确保持久化能读取到 assistant 回复
        getMessagesList().add(new AssistantMessage(content));
        if (dialogState != null) {
            dialogState.setLastBotMessage(content);
            dialogStateManager.updateState(sessionId, dialogState);
        }
    }

    /**
     * 为缺失槽位构建追问消息（槽位填充模式专用）
     */
    private String buildClarificationForMissing(List<SlotDefinition> missing) {
        if (missing == null || missing.isEmpty()) {
            return "请提供更多信息。";
        }
        if (missing.size() == 1) {
            SlotDefinition slot = missing.get(0);
            dialogState.setLastClarificationSlot(slot.getSlotName());
            return intentConfig.getClarificationQuestion(slot.getSlotName());
        }
        dialogState.setLastClarificationSlot(missing.get(0).getSlotName());
        StringBuilder sb = new StringBuilder("为了更好地帮助您，还需要以下信息：\n");
        for (int i = 0; i < missing.size(); i++) {
            sb.append(i + 1).append(". ").append(missing.get(i).getDescription()).append("\n");
        }
        sb.append("\n您也可以一次告诉我所有信息。");
        return sb.toString();
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
