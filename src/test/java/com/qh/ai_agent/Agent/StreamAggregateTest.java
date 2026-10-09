package com.qh.ai_agent.Agent;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 流式输出聚合的纯函数单测：验证 token 级流式的文本拼接与工具调用识别。
 * 不依赖大模型，可直接跑。
 */
class StreamAggregateTest {

    private static ChatResponse textChunk(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    private static ChatResponse toolCallChunk(String name, String args) {
        AssistantMessage message = AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall("call_1", "function", name, args)))
                .build();
        return new ChatResponse(List.of(new Generation(message)));
    }

    @Test
    void aggregateTextShouldJoinAllChunks() {
        List<ChatResponse> chunks = List.of(
                textChunk("您"),
                textChunk("的订单"),
                textChunk("已发货"));
        assertEquals("您的订单已发货", ToolCallAgent.aggregateText(chunks));
    }

    @Test
    void aggregateTextShouldSkipBlankChunks() {
        List<ChatResponse> chunks = List.of(
                textChunk("订单"),
                textChunk(""),
                textChunk("已发货"));
        assertEquals("订单已发货", ToolCallAgent.aggregateText(chunks));
    }

    @Test
    void pickToolCallsShouldReturnLastAccumulatedCalls() {
        List<ChatResponse> chunks = List.of(
                textChunk("我来帮您查一下"),
                toolCallChunk("searchKnowledgeBase", "{\"query\":\"订单\"}"));
        assertEquals(1, ToolCallAgent.pickToolCalls(chunks).size());
        assertEquals("searchKnowledgeBase", ToolCallAgent.pickToolCalls(chunks).get(0).name());
    }

    @Test
    void pickToolCallsShouldBeEmptyForPureTextAnswer() {
        List<ChatResponse> chunks = List.of(
                textChunk("您好"),
                textChunk("，很高兴为您服务"));
        assertTrue(ToolCallAgent.pickToolCalls(chunks).isEmpty());
    }
}
