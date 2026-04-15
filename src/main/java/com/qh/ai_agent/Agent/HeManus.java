package com.qh.ai_agent.Agent;


import com.qh.ai_agent.Agent.Model.AgentState;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;


/**
 *  秋鹤的超级智能体，可以有着自主规划的能力，可以直接使用
 */
@Component
public   class HeManus extends ToolCallAgent{


    public HeManus(ToolCallback[] allTools, ChatModel dashscopeChatModel) {
        super(allTools, dashscopeChatModel);
        this.setName("HeManus");
        // 设置状态为 RUNNING 以便可以运行
        this.setState(AgentState.RUNNING);
        String SystemPrompt ="You are HeManus, an all-capable AI assistant, aimed at solving any task presented by the user.\n" +
                "        You have various tools at your disposal that you can call upon to efficiently complete complex requests.";
        this.setSystemPrompt(SystemPrompt);
        String NewSystemPrompt ="Based on user needs, proactively select the most appropriate tool or combination of tools.\n" +
                "        For complex tasks, you can break down the problem and use different tools step by step to solve it.\n" +
                "        After using each tool, clearly explain the execution results and suggest the next steps.\n" +
                "        If you want to stop the interaction at any point, use the `terminate` tool/function call.";
        this.setNextStepPrompt(NewSystemPrompt);
        this.setMaxStep(20);
    }
}
