package com.qh.ai_agent.Agent;

import com.qh.ai_agent.Agent.Model.AgentState;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;


@SpringBootTest
class HeManusTest {

    @Resource
    private HeManus heManus;

    /**
     * 简单测试 - 不依赖网络
     * 测试 Agent 基本功能是否正常
     */
    @Test
    public void testBasicFunctionality() {
        // 使用简单的提示词，不触发网络搜索
        String userPrompt = "你好，请介绍一下你自己";
        String answer = heManus.run(userPrompt);
        Assertions.assertNotNull(answer, "Agent 应该返回一个回答");
        System.out.println("=== Agent 回答 ===");
        System.out.println(answer);
    }

    /**
     * 完整功能测试 - 需要网络连接
     * 使用 @Disabled 注解默认跳过，需要时手动运行
     *
     * 运行前确保：
     * 1. 网络连接正常，或
     * 2. 已配置代理（application-local.yml 中 http.client.proxy-enabled=true）
     */
    @Test
    public void testFullFunctionality() {
        String userPrompt = "我的另一半在南昌市红谷滩区，请我帮我十公里内适合约会的地方，然后" +
                "并且结合一些网络图片，制定一份详细的约会计划" +
                "并且用PDF的格式输出";
        String answer = heManus.run(userPrompt);
        Assertions.assertNotNull(answer, "Agent 应该返回一个回答");
        System.out.println("=== Agent 回答 ===");
        System.out.println(answer);
    }

    /**
     * 测试 Agent 初始化
     */
    @Test
    public void testInitialization() {
        Assertions.assertNotNull(heManus, "HeManus 应该被正确注入");
        Assertions.assertEquals("HeManus", heManus.getName(), "Agent 名称应该是 HeManus");
        Assertions.assertEquals(AgentState.RUNNING, heManus.getState(), "Agent 状态应该是 RUNNING");
    }
}
