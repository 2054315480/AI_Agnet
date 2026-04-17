package com.qh.ai_agent.Agent;


import com.qh.ai_agent.Agent.Model.AgentState;
import com.qh.ai_agent.service.PromptTemplateService;
import com.qh.ai_agent.service.SensitiveInfoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.content.Media;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 *  秋鹤的超级智能体，可以有着自主规划的能力，可以直接使用
 */
@Slf4j
@Component
public class HeManus extends ToolCallAgent{

    private final SensitiveInfoService sensitiveInfoService;

    public HeManus(ToolCallback[] allTools, ChatModel dashscopeChatModel,
                   PromptTemplateService promptTemplateService,
                   SensitiveInfoService sensitiveInfoService) {
        super(allTools, dashscopeChatModel);
        this.sensitiveInfoService = sensitiveInfoService;
        this.setName("HeManus");
        // 设置状态为 RUNNING 以便可以运行
        this.setState(AgentState.RUNNING);

        // 从模板文件加载系统提示词
        Map<String, Object> vars = new HashMap<>();
        vars.put("agent_name", "HeManus");
        vars.put("max_steps", "20");
        String systemPrompt = promptTemplateService.loadTemplate("he-manus")
                .renderWithDefaults(vars);
        this.setSystemPrompt(systemPrompt);

        String nextStepPrompt = "Based on user needs, proactively select the most appropriate tool or combination of tools.\n" +
                "        For complex tasks, you can break down the problem and use different tools step by step to solve it.\n" +
                "        After using each tool, clearly explain the execution results and suggest the next steps.\n" +
                "        If you want to stop the interaction at any point, use the `terminate` tool/function call.";
        this.setNextStepPrompt(nextStepPrompt);
        this.setMaxStep(20);
    }

    /**
     * 对用户输入进行敏感信息脱敏
     */
    private String maskInput(String userPrompt) {
        if (sensitiveInfoService != null && sensitiveInfoService.containsSensitiveInfo(userPrompt)) {
            String masked = sensitiveInfoService.maskSensitiveInfo(userPrompt);
            log.info("Manus 智能体：检测到敏感信息，已自动脱敏");
            return masked;
        }
        return userPrompt;
    }

    @Override
    public String run(String userPrompt) {
        return super.run(maskInput(userPrompt));
    }

    @Override
    public String run(String userPrompt, List<Media> mediaList) {
        return super.run(maskInput(userPrompt), mediaList);
    }

    @Override
    public SseEmitter runStream(String userPrompt) {
        return super.runStream(maskInput(userPrompt));
    }

    @Override
    public SseEmitter runStream(String userPrompt, List<Media> mediaList) {
        return super.runStream(maskInput(userPrompt), mediaList);
    }
}
