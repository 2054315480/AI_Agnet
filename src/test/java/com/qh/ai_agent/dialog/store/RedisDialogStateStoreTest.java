package com.qh.ai_agent.dialog.store;

import com.qh.ai_agent.dialog.model.DialogPhase;
import com.qh.ai_agent.dialog.model.DialogState;
import com.qh.ai_agent.intent.model.CustomerIntent;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Redis 会话存储测试：验证 DialogState 的 JSON 序列化往返。
 * 本机 6379 没有 Redis 时自动跳过，不影响其它用例。
 */
class RedisDialogStateStoreTest {

    @Test
    void dialogStateShouldRoundTripAsJson() throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper mapper = RedisDialogStateStore.jsonMapper();
        DialogState state = DialogState.builder()
                .sessionId("json-1")
                .dialogPhase(DialogPhase.CLARIFYING)
                .currentIntent(CustomerIntent.ORDER_REFUND)
                .build();
        state.getFilledSlots().put("order_id", "A1");

        String json = mapper.writeValueAsString(state);
        assertFalse(json.contains("slotFillingOrClarifying"), "派生属性不应被序列化（否则反序列化会失败）");

        DialogState back = mapper.readValue(json, DialogState.class);
        assertEquals(DialogPhase.CLARIFYING, back.getDialogPhase());
        assertEquals(CustomerIntent.ORDER_REFUND, back.getCurrentIntent());
        assertEquals("A1", back.getFilledSlots().get("order_id"));

        // 兼容历史上已经写进 Redis 的旧格式（含未知字段）
        DialogState legacy = mapper.readValue("{\"sessionId\":\"legacy-1\",\"slotFillingOrClarifying\":true}", DialogState.class);
        assertEquals("legacy-1", legacy.getSessionId());
    }

    @Test
    void roundTripThroughRedis() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory("localhost", 6379);
        factory.afterPropertiesSet();
        StringRedisTemplate template = new StringRedisTemplate(factory);
        template.afterPropertiesSet();
        try {
            template.hasKey("cs:session:__health_check__");
        } catch (Exception e) {
            Assumptions.abort("本机 Redis 不可用，跳过该用例: " + e.getMessage());
        }

        RedisDialogStateStore store = new RedisDialogStateStore(template, 30, 100);
        String sid = "test-" + System.currentTimeMillis();
        DialogState state = DialogState.builder()
                .sessionId(sid)
                .dialogPhase(DialogPhase.SLOT_FILLING)
                .currentIntent(CustomerIntent.ORDER_QUERY)
                .confidenceScore(0.91)
                .consecutiveOutOfScope(1)
                .build();
        state.getFilledSlots().put("order_id", "A123456");
        state.getPendingSlots().add("refund_reason");

        store.put(sid, state);

        DialogState loaded = store.get(sid);
        assertNotNull(loaded, "应从 Redis 读回会话状态");
        assertEquals(DialogPhase.SLOT_FILLING, loaded.getDialogPhase());
        assertEquals(CustomerIntent.ORDER_QUERY, loaded.getCurrentIntent());
        assertEquals("A123456", loaded.getFilledSlots().get("order_id"));
        assertEquals(1, loaded.getPendingSlots().size());
        assertEquals(0.91, loaded.getConfidenceScore(), 0.001);
        assertEquals(1, loaded.getConsecutiveOutOfScope());

        store.remove(sid);
        assertNull(store.get(sid), "删除后应读不到");
        factory.destroy();
    }
}
