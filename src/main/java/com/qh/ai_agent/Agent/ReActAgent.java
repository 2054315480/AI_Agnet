package com.qh.ai_agent.Agent;


import com.qh.ai_agent.Agent.Model.AgentState;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public abstract class ReActAgent extends BaseAgent{

    /**
     *   处理当前状态并且决定下一步的行动
     * @return 是否需要执行行动，true 需要，false 不需要
     */

    public  abstract  boolean think();


    /**
     *  执行决定的行动
     * @return  行动执行结果
     */
    public abstract String act();

    @Override
    public  String step() {
        try {
            // 先思考
            sendSse("  正在思考...");
            boolean shouldAct = think();
            if (!shouldAct) {
                // 没有工具需要调用，设置状态为完成
                setState(AgentState.FINISHED);
                sendSse("  思考完成 - 任务已结束");
                return "思考完成 - 无需行动";
            }
            sendSse("  正在执行行动...");
            return act();
        }catch (Exception e){
            // 记录异常日志
            e.printStackTrace();
            setState(AgentState.ERROR);
            return "步骤执行失败："+e.getMessage();
        }
    }
}
