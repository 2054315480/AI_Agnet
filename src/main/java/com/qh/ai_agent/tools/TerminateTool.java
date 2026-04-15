package com.qh.ai_agent.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 *  终止工具，让AI 智能体能够合理的终端任务
 */
@Component
public class TerminateTool {

    @Tool(description = " 如果你觉得任务完成了，或者完不成任务，那就终止这个任务")
    public String doTerminate() {
        return "任务结束";
    }
}
