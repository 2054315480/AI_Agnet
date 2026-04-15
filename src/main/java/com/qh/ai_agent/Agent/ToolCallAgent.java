package com.qh.ai_agent.Agent;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.dashscope.aigc.conversation.ConversationResult;
import com.qh.ai_agent.Agent.Model.AgentState;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
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

    // 禁用Spring ai 的内置工具调用机制，自己进行维护选项和消息上下文
    private  final ChatOptions chatOptions;

    public ToolCallAgent(ToolCallback[] availableTools, ToolCallingManager toolCallingManager) {
        super();
        this.availableTools = availableTools;
        this.toolCallingManager = toolCallingManager != null ? toolCallingManager : ToolCallingManager.builder().build();
        this.chatOptions = DashScopeChatOptions.builder()
                .build();
    }

    /**
     * 便捷构造函数 - 自动创建 ToolCallingManager
     */
    public ToolCallAgent(ToolCallback[] availableTools) {
        this(availableTools, null);
    }



    @Override
    public boolean think() {

        // 校验提示词，拼接用户提示词
        if ((StrUtil.isNotBlank(getNextStepPrompt()))) {
            UserMessage userMessage = new UserMessage(getNextStepPrompt());
            getMessagesList().add(userMessage);
        }

        // 调用AI大模型，获取工具调用结果
        List<Message> messagesList = getMessagesList();
        Prompt prompt = new Prompt(messagesList, this.chatOptions);
        try {
            ChatResponse chatResponse = getChatClient().prompt(prompt)
                    .system(getSystemPrompt())
                    .toolCallbacks(availableTools)  // 修复: 使用 toolCallbacks 而不是 tools
                    .call()
                    .chatResponse();

            this.toolCallchatResponse = chatResponse;
            // 解析工具调用的结果，获取需要调用的工具

            // 助手消息
            AssistantMessage assistantMessage = chatResponse.getResult().getOutput();

            // 获取需要调用的工具列表
            List<AssistantMessage.ToolCall> toolCallList = assistantMessage.getToolCalls();

            // 输出提示消息
            String result = assistantMessage.getText();
            log.info(getName() + "的思考" + result);
            log.info(getName() + "选择了" + toolCallList.size() + "个工具来使用");
            String toolCallInfo = toolCallList.stream()
                    .map(toolcall -> String.format("工具名称：%s, 参数: %s ", toolcall.name(), toolcall.arguments()))
                    .collect(Collectors.joining("\n"));
            log.info(toolCallInfo);
            if (toolCallInfo.isEmpty()) {
                // 只有不调用工具返回false
                getMessagesList().add(assistantMessage);
                return false;
            } else {
                return true;
            }
        } catch (Exception e) {
            log.error(getName() + "的思考过程有问题", e);  // 打印完整异常堆栈
            getMessagesList().add(new AssistantMessage("处理时发生了错误: " + e.getClass().getSimpleName() + " - " + e.getMessage()));
            return false;
        }

    }

    @Override
    public String act() {

        /**
         *
         *  处理工具调用的基础代理类，具有实现了think 和act 的方法
         *  可以创建实例的父类
         */
        if (toolCallchatResponse == null || !toolCallchatResponse.hasToolCalls()) {
            return "没有需要调用的工具";
        }
        // 调用工具
        Prompt prompt = new Prompt(getMessagesList(), this.chatOptions);
        ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, toolCallchatResponse);
        // 记录消息上下文conversationHistory包含了助手消息和消息上下文
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
        return results;
    }



}
