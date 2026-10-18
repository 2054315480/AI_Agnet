package com.qh.ai_agent.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 对话护栏测试：违禁词拦截 + 敏感信息脱敏（活链路的输入/输出保护）。
 */
class ChatGuardServiceTest {

    private ChatGuardService guard() {
        return new ChatGuardService(new BannedWordService("垃圾,暴力"), new SensitiveInfoService());
    }

    @Test
    void bannedWordInputShouldBeBlocked() {
        ChatGuardService.GuardResult r = guard().guardInput("你是个垃圾客服");
        assertFalse(r.allowed());
        assertEquals("banned_word", r.reason());
    }

    @Test
    void phoneNumberInInputShouldBeMasked() {
        ChatGuardService.GuardResult r = guard().guardInput("我的手机号是13812341234，帮我查订单");
        assertTrue(r.allowed());
        assertTrue(r.text().contains("138****1234"), "输入侧手机号应脱敏，实际=" + r.text());
    }

    @Test
    void sensitiveInfoInOutputShouldBeMasked() {
        String out = guard().guardOutput("已为您登记，联系电话13812341234，邮箱zhangsan@example.com");
        assertTrue(out.contains("138****1234"), "输出侧手机号应脱敏，实际=" + out);
        assertFalse(out.contains("zhangsan@example.com"), "输出侧邮箱应脱敏，实际=" + out);
    }

    @Test
    void normalTextShouldPassThrough() {
        String out = guard().guardOutput("退货政策是7天无理由。");
        assertEquals("退货政策是7天无理由。", out);
    }
}
