package com.qh.ai_agent.Agent;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import org.springframework.ai.openai.OpenAiChatOptions;
import com.qh.ai_agent.Agent.Model.AgentState;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public  class ToolCallAgent extends ReActAgent{

    // 可用的工具
    private final ToolCallback[] availableTools;

    // 保存工具调用信息的响应结果
    private ChatResponse toolCallchatResponse;

    // 工具调用的管理者
    private final ToolCallingManager toolCallingManager;

    // ChatOptions（禁用自动工具执行）
    private  final ChatOptions chatOptions;

    // ChatModel 引用（直接调用模型，绕过 ChatClient 的自动工具执行）
    private final ChatModel chatModel;

    // 视觉模型名称（当消息包含图片时使用）
    private String visionModel;

    // 普通对话是否开启思考模式（部分模型如 MiniMax-M2.1 强制要求开启）
    private Boolean chatEnableThinking = true;
    /** 本轮 think 是否已经向前端推送过增量文本，工具调用轮需要回撤 */
    private boolean streamedDeltaThisTurn = false;

    /** 兜底打字机：每片字符数，模型端不支持流式时使用 */
    private static final int TYPEWRITER_CHUNK_SIZE = 12;

    /** 兜底打字机：每片之间的间隔毫秒数 */
    private static final long TYPEWRITER_CHUNK_DELAY_MS = 15L;

    public ToolCallAgent(ToolCallback[] availableTools, ChatModel chatModel, ToolCallingManager toolCallingManager) {
        super();
        this.availableTools = availableTools;
        this.chatModel = chatModel;
        this.toolCallingManager = toolCallingManager != null ? toolCallingManager : buildToolCallingManager(availableTools);
        this.chatOptions = OpenAiChatOptions.builder()
                .build();
    }

    /**
     * 便捷构造函数 - 自动创建 ToolCallingManager 并注册工具
     */
    public ToolCallAgent(ToolCallback[] availableTools, ChatModel chatModel) {
        this(availableTools, chatModel, null);
    }

    /**
     * 构建 ToolCallingManager，将可用工具注册到 ToolCallbackResolver
     */
    private static ToolCallingManager buildToolCallingManager(ToolCallback[] tools) {
        // 构建工具名到回调的映射
        Map<String, ToolCallback> toolMap = new HashMap<>();
        for (ToolCallback tool : tools) {
            toolMap.put(tool.getToolDefinition().name(), tool);
        }

        // 创建自定义的 ToolCallbackResolver
        ToolCallbackResolver resolver = toolName -> toolMap.get(toolName);

        return ToolCallingManager.builder()
                .toolCallbackResolver(resolver)
                .build();
    }



    /** 工具调用指纹记录：用于循环检测（同工具 + 同参数重复调用） */
    private final java.util.List<String> toolCallFingerprints = new java.util.ArrayList<>();

    /** 同签名最多允许出现次数，超过即判定为死循环 */
    private static final int MAX_SAME_CALL = 2;

    /** 计算调用指纹 */
    private static String fingerprint(String name, String args) {
        String a = args == null ? "" : args.replaceAll("\s+", "");
        return name + "|" + a;
    }

    @Override
    public boolean think() {

        // 校验提示词，拼接用户提示词
        if ((StrUtil.isNotBlank(getNextStepPrompt()))) {
            UserMessage userMessage = new UserMessage(getNextStepPrompt());
            getMessagesList().add(userMessage);
        }

        // 构建消息列表：系统提示词 + 对话历史
        List<Message> allMessages = new ArrayList<>();
        if (StrUtil.isNotBlank(getSystemPrompt())) {
            allMessages.add(new SystemMessage(getSystemPrompt()));
        }
        allMessages.addAll(getMessagesList());

        // 构建 ChatOptions：包含工具定义 + 禁用自动工具执行
        // 检测消息中是否包含图片，如有则切换到视觉模型
        boolean hasMedia = getMessagesList().stream()
                .filter(msg -> msg instanceof UserMessage)
                .map(msg -> (UserMessage) msg)
                .anyMatch(msg -> msg.getMedia() != null && !msg.getMedia().isEmpty());

        OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder()
                .internalToolExecutionEnabled(false)
                .toolCallbacks(Arrays.asList(availableTools));

        if (hasMedia && visionModel != null) {
            optionsBuilder.model(visionModel);
        }

        OpenAiChatOptions options = optionsBuilder.build();

        Prompt prompt = new Prompt(allMessages, options);
        try {
            // 直接调用 ChatModel，获取包含工具调用的原始响应（不会被自动执行）
            // 【改造】SSE 模式下优先 token 级流式，最后聚合成完整响应
            com.qh.ai_agent.trace.TraceContext.Span llmSpan =
                    com.qh.ai_agent.trace.TraceCollector.begin("llm");
            ModelTurn turn;
            try {
                turn = callModelWithStreaming(prompt);
                com.qh.ai_agent.trace.MetricsRegistry.incLlmCalls();
                com.qh.ai_agent.trace.TraceCollector.end(llmSpan, java.util.Map.of(
                        "toolCalls", turn.toolCalls() == null ? 0 : turn.toolCalls().size(),
                        "chars", turn.text() == null ? 0 : turn.text().length()));
            } catch (Exception e) {
                com.qh.ai_agent.trace.TraceCollector.fail(llmSpan, e.getClass().getSimpleName());
                throw e;
            }
            this.toolCallchatResponse = turn.response();

            // 获取需要调用的工具列表
            List<AssistantMessage.ToolCall> toolCallList = turn.toolCalls();

            // 输出提示消息
            String result = turn.text();
            log.info("[ToolThink] agent={} | step={} | thinking={}", getName(), getCurrentStep(),
                    result != null ? result.substring(0, Math.min(result.length(), 200)) : "null");

            if (toolCallList == null || toolCallList.isEmpty()) {
                // 没有工具调用：任务完成，收口最终答案
                if (StrUtil.isNotBlank(result)) {
                    getMessagesList().add(new AssistantMessage(result));
                }
                emitFinalAnswer(result);
                onFinalAnswer(result);
                return false;
            } else {
                // 本轮是工具调用轮：之前推的增量不是最终答案，先回撤并归入思考过程
                if (streamedDeltaThisTurn) {
                    sendSseEvent("thinking", "模型先给出中间说明，随后决定调用工具。");
                    sendSseEvent("answer_reset", "");
                    streamedDeltaThisTurn = false;
                }
                log.info("[ToolThink] agent={} | step={} | selectedTools={}", getName(), getCurrentStep(), toolCallList.size());
                String toolCallInfo = toolCallList.stream()
                        .map(toolcall -> String.format("工具名称：%s, 参数: %s ", toolcall.name(), toolcall.arguments()))
                        .collect(Collectors.joining("\n"));
                log.info(toolCallInfo);
                // 循环检测：同一工具 + 同一参数连续超过阈值 -> 打断并转人工
                int repeats = 0;
                for (AssistantMessage.ToolCall tc : toolCallList) {
                    String fp = fingerprint(tc.name(), tc.arguments());
                    long same = toolCallFingerprints.stream().filter(fp::equals).count();
                    if (same >= MAX_SAME_CALL) {
                        repeats++;
                    }
                }
                if (repeats > 0) {
                    String msg = "检测到重复调用相同工具且参数未变化，已停止以避免死循环。建议转人工处理。";
                    log.warn("[LoopGuard] agent={} step={} 触发循环检测", getName(), getCurrentStep());
                    sendSseEvent("thinking", msg);
                    sendSseEvent("answer", msg);
                    getMessagesList().add(new AssistantMessage(msg));
                    return false;
                }
                for (AssistantMessage.ToolCall tc : toolCallList) {
                    toolCallFingerprints.add(fingerprint(tc.name(), tc.arguments()));
                }

                sendSseEvent("tool_call", "决定使用 " + toolCallList.size() + " 个工具:\n" + toolCallInfo);

                // 注意：不在此处添加 assistantMessage，act() 的 executeToolCalls 会负责添加
                return true;
            }
        } catch (Exception e) {
            log.error(getName() + "的思考过程有问题", e);
            String errorMsg = "处理时发生了错误: " + e.getClass().getSimpleName() + " - " + e.getMessage();
            getMessagesList().add(new AssistantMessage(errorMsg));
            sendSseEvent("answer", "抱歉，" + errorMsg);
            return false;
        }

    }

    @Override
    public String act() {

        if (toolCallchatResponse == null || !toolCallchatResponse.hasToolCalls()) {
            return "没有需要调用的工具";
        }

        // 记录工具调用详情
        AssistantMessage assistantMessage = toolCallchatResponse.getResult().getOutput();
        List<AssistantMessage.ToolCall> toolCalls = assistantMessage.getToolCalls();
        for (AssistantMessage.ToolCall tc : toolCalls) {
            log.info("[ToolExec] agent={} | tool={} | params={}", getName(), tc.name(), tc.arguments());
        }

        // 执行工具并计时
        long startTime = System.currentTimeMillis();
        Prompt prompt = new Prompt(getMessagesList(), this.chatOptions);
        ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, toolCallchatResponse);
        long elapsed = System.currentTimeMillis() - startTime;

        // 记录消息上下文（conversationHistory 包含助手消息和工具响应）
        setMessagesList(toolExecutionResult.conversationHistory());

        ToolResponseMessage toolResponseMessage = (ToolResponseMessage) CollUtil.getLast(toolExecutionResult.conversationHistory());

        // 记录每个工具的执行结果
        for (ToolResponseMessage.ToolResponse response : toolResponseMessage.getResponses()) {
            String resultStr = response.responseData().toString();
            int previewLen = Math.min(resultStr.length(), 300);
            log.info("[ToolExec] agent={} | tool={} | status=OK | time={}ms | result={}",
                    getName(), response.name(), elapsed, resultStr.substring(0, previewLen));
        }

        boolean terminateToolCalled = toolResponseMessage.getResponses().stream()
                .anyMatch(response -> response.name().equals("doTerminate"));

        // 更改状态
        if(terminateToolCalled){
            setState(AgentState.FINISHED);
        }
        String results = toolResponseMessage.getResponses().stream()
                .map(response -> "工具" + response.name() + " 返回结果" + response.responseData())
                .collect(Collectors.joining("\n"));
        log.info("[ToolExec] agent={} | step={} | totalTools={} | totalTime={}ms", getName(), getCurrentStep(), toolCalls.size(), elapsed);
        sendSseEvent("tool_result", results);
        return results;
    }




    // ==================== 流式输出支持 ====================

    /** 一次模型调用的聚合结果：原始响应含 tool calls、完整文本、工具调用列表 */
    private record ModelTurn(ChatResponse response, String text, List<AssistantMessage.ToolCall> toolCalls) {}

    /**
     * 调用模型：SSE 模式下优先 token 级流式，增量文本实时推给前端；
     * 非 SSE 模式或流式不可用时退回一次性调用。
     * 两条路径都返回完整响应，think/act 的后续逻辑完全复用。
     */
    private ModelTurn callModelWithStreaming(Prompt prompt) {
        this.streamedDeltaThisTurn = false;

        // 没有 SSE 通道时（run 同步调用、单元测试）直接一次性调用
        if (getCurrentSseEmitter() == null) {
            return toTurn(chatModel.call(prompt));
        }

        try {
            List<ChatResponse> chunks = chatModel.stream(prompt)
                    .doOnNext(chunk -> {
                        String delta = extractText(chunk);
                        if (StrUtil.isNotBlank(delta)) {
                            sendSseEvent("answer_delta", delta);
                        }
                    })
                    .collectList()
                    .block();

            if (chunks == null || chunks.isEmpty()) {
                throw new IllegalStateException("流式响应为空");
            }

            // 纯函数聚合，便于单测：文本拼接 + 取最后一个携带 tool calls 的分片
            String fullText = aggregateText(chunks);
            List<AssistantMessage.ToolCall> toolCalls = pickToolCalls(chunks);
            streamedDeltaThisTurn = StrUtil.isNotBlank(fullText);

            if (StrUtil.isBlank(fullText) && toolCalls.isEmpty()) {
                throw new IllegalStateException("流式响应既没有文本也没有工具调用");
            }

            ChatResponse response = chunks.get(chunks.size() - 1);
            if (!toolCalls.isEmpty()) {
                for (ChatResponse chunk : chunks) {
                    if (!extractToolCalls(chunk).isEmpty()) {
                        response = chunk;
                    }
                }
            }
            return new ModelTurn(response, fullText, toolCalls);
        } catch (Exception e) {
            log.warn("[Stream] 流式调用不可用，降级为一次性调用: {}", e.getMessage());
            return toTurn(chatModel.call(prompt));
        }
    }

    /** 把一次性调用响应包装成 ModelTurn */
    private ModelTurn toTurn(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            throw new IllegalStateException("模型返回为空");
        }
        AssistantMessage message = response.getResult().getOutput();
        List<AssistantMessage.ToolCall> calls = message.getToolCalls();
        return new ModelTurn(response, message.getText(), calls != null ? calls : List.of());
    }

    /**
     * 推送最终答案：
     * 1. 已经流式推过增量，补一个 answer 收口事件，前端用完整文本覆盖，保证最终一致；
     * 2. 没有流式推过，即模型端不支持流式，按片推送 answer_delta，保证前端仍有打字机效果。
     */
    private void emitFinalAnswer(String text) {
        String safe = text != null ? text : "（无文本输出）";
        if (streamedDeltaThisTurn) {
            sendSseEvent("answer", safe);
            return;
        }
        if (getCurrentSseEmitter() != null && safe.length() > TYPEWRITER_CHUNK_SIZE) {
            for (int i = 0; i < safe.length(); i += TYPEWRITER_CHUNK_SIZE) {
                String part = safe.substring(i, Math.min(safe.length(), i + TYPEWRITER_CHUNK_SIZE));
                sendSseEvent("answer_delta", part);
                try {
                    Thread.sleep(TYPEWRITER_CHUNK_DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        sendSseEvent("answer", safe);
    }

    /** 取一个流式分片的文本增量，result/output 可能为空 */
    static String extractText(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return null;
        }
        return response.getResult().getOutput().getText();
    }

    /** 取一个流式分片的工具调用，result/output 可能为空 */
    static List<AssistantMessage.ToolCall> extractToolCalls(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return List.of();
        }
        List<AssistantMessage.ToolCall> calls = response.getResult().getOutput().getToolCalls();
        return calls != null ? calls : List.of();
    }

    /**
     * 最终答案产出后的回调，流式与非流式都会触发，由子类覆写做状态维护。
     * @param answerText 最终答案文本，可能为 null
     */
    /** 聚合流式分片的完整文本，纯函数便于单测 */
    static String aggregateText(List<ChatResponse> chunks) {
        StringBuilder sb = new StringBuilder();
        for (ChatResponse chunk : chunks) {
            String delta = extractText(chunk);
            if (StrUtil.isNotBlank(delta)) {
                sb.append(delta);
            }
        }
        return sb.toString();
    }

    /** 取最后一个携带工具调用的分片，OpenAI 兼容实现会把 tool call 分片合并累积 */
    static List<AssistantMessage.ToolCall> pickToolCalls(List<ChatResponse> chunks) {
        List<AssistantMessage.ToolCall> toolCalls = List.of();
        for (ChatResponse chunk : chunks) {
            List<AssistantMessage.ToolCall> calls = extractToolCalls(chunk);
            if (!calls.isEmpty()) {
                toolCalls = calls;
            }
        }
        return toolCalls;
    }

    protected void onFinalAnswer(String answerText) {
        // 默认空实现，由子类覆写
    }

}
