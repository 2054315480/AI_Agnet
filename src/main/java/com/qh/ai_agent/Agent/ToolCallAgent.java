package com.qh.ai_agent.Agent;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
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

    public ToolCallAgent(ToolCallback[] availableTools, ChatModel chatModel, ToolCallingManager toolCallingManager) {
        super();
        this.availableTools = availableTools;
        this.chatModel = chatModel;
        this.toolCallingManager = toolCallingManager != null ? toolCallingManager : buildToolCallingManager(availableTools);
        this.chatOptions = DashScopeChatOptions.builder()
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
        DashScopeChatOptions options = DashScopeChatOptions.builder()
                .internalToolExecutionEnabled(false)
                .toolCallbacks(Arrays.asList(availableTools))
                .build();

        Prompt prompt = new Prompt(allMessages, options);
        try {
            // 直接调用 ChatModel，获取包含工具调用的原始响应（不会被自动执行）
            ChatResponse chatResponse = chatModel.call(prompt);

            this.toolCallchatResponse = chatResponse;

            // 助手消息
            AssistantMessage assistantMessage = chatResponse.getResult().getOutput();

            // 获取需要调用的工具列表
            List<AssistantMessage.ToolCall> toolCallList = assistantMessage.getToolCalls();

            // 输出提示消息
            String result = assistantMessage.getText();
            log.info(getName() + "的思考: {}", result);

            if (toolCallList == null || toolCallList.isEmpty()) {
                // 没有工具调用，任务完成 —— 发送最终答案
                getMessagesList().add(assistantMessage);
                sendSseEvent("answer", result != null ? result : "（无文本输出）");
                return false;
            } else {
                log.info(getName() + "选择了" + toolCallList.size() + "个工具来使用");
                String toolCallInfo = toolCallList.stream()
                        .map(toolcall -> String.format("工具名称：%s, 参数: %s ", toolcall.name(), toolcall.arguments()))
                        .collect(Collectors.joining("\n"));
                log.info(toolCallInfo);
                sendSseEvent("tool_call", "决定使用 " + toolCallList.size() + " 个工具:\n" + toolCallInfo);

                // 将助手消息（含工具调用）加入消息列表，供 act() 使用
                getMessagesList().add(assistantMessage);
                return true;
            }
        } catch (Exception e) {
            log.error(getName() + "的思考过程有问题", e);
            getMessagesList().add(new AssistantMessage("处理时发生了错误: " + e.getClass().getSimpleName() + " - " + e.getMessage()));
            return false;
        }

    }

    @Override
    public String act() {

        if (toolCallchatResponse == null || !toolCallchatResponse.hasToolCalls()) {
            return "没有需要调用的工具";
        }

        // 调用工具
        Prompt prompt = new Prompt(getMessagesList(), this.chatOptions);
        ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, toolCallchatResponse);

        // 记录消息上下文（conversationHistory 包含助手消息和工具响应）
        setMessagesList(toolExecutionResult.conversationHistory());

        ToolResponseMessage toolResponseMessage = (ToolResponseMessage) CollUtil.getLast(toolExecutionResult.conversationHistory());
        boolean terminateToolCalled = toolResponseMessage.getResponses().stream()
                .anyMatch(response -> response.name().equals("doTerminate"));

        // 更改状态
        if(terminateToolCalled){
            setState(AgentState.FINISHED);
        }
        String results = toolResponseMessage.getResponses().stream()
                .map(response -> "工具" + response.name() + " 返回结果" + response.responseData())
                .collect(Collectors.joining("\n"));
        log.info(results);
        sendSseEvent("tool_result", results);
        return results;
    }



}
