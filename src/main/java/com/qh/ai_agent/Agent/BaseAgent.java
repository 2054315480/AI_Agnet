package com.qh.ai_agent.Agent;


import cn.hutool.core.util.StrUtil;
import com.qh.ai_agent.Agent.Model.AgentState;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * 抽象基础代理类，用于管理代理状态和执行流程
 *
 * 提供状态转换、内存管理和基于步骤的执行循环的基础功能。
 * 子类必须实现step方法。
 */

@Data
@Slf4j
public abstract class BaseAgent {

    //核心属性
    private String Name;

    //
    private String systemPrompt;
    private String nextStepPrompt;

    //代理状态
    private AgentState state = AgentState.IDLE;

    // 执行步骤控制
    private int currentStep = 0;
    private int maxStep = 10;

    //LLM大模型
    private ChatClient chatClient;

    //Memory 记忆，自主维护上下文
    private List<Message> messagesList = new ArrayList<>();

    /**
     *
     *  运行代理
     * @parm userPrompt 用户提示词
     * @return 执行结果
     */

    public  String run(String userPrompt){
        // 基础校验
        if(this.state == AgentState.IDLE){
            throw new RuntimeException("Can't run agent because state is IDLE" + this.state);
        }
        if(StrUtil.isBlank(userPrompt)){
            throw new RuntimeException("Can't run agent because user prompt is blank");
        }

        // 执行，更改状态
        this.state = AgentState.RUNNING;
        // 记录上下文
        messagesList.add(new UserMessage(userPrompt));

        // 保存结果列表
        List<String> results = new ArrayList<>();

        try {
            for (int i = 0; i < maxStep && state != AgentState.FINISHED; i++) {
                int stepNumber = i+1;
                currentStep = stepNumber;
                log.info("step number: {}/{}", stepNumber,maxStep);

                // 单步执行
                String stepResult = step();
                String result = "Step  " + stepNumber + ": " + stepResult;
                results.add(result);
            }
            // 检查是否超出步骤限制
            if(currentStep == maxStep){
                state = AgentState.FINISHED;
                results.add("Terminated : Reched max steps (" + maxStep + ")");
            }
            return String.join("\n", results);
        } catch (Exception e) {
            state = AgentState.ERROR;
            log.error("error executing agent ",e);
            return "执行错误" + e.getMessage();
        }finally {
            this.cleanup(); // 清理资源
        }
        // 执行循环

    }

    /**
     *
     * 定义单个步骤
     */
    public  abstract String step();

    protected void cleanup(){
        // 可以重写子类实现，清理资源
    }


}
