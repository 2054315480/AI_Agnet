package com.qh.ai_agent.intent;

import com.qh.ai_agent.intent.model.CustomerIntent;
import com.qh.ai_agent.intent.model.IntentResult;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 意图关键词预筛测试：不依赖大模型，直接验证关键词规则。
 */
class IntentKeywordTest {

    private IntentClassifier classifier() {
        IntentConfig config = new IntentConfig();
        config.init();
        return new IntentClassifier(null, config, null);
    }

    @Test
    void explicitHandoffShouldBeRequestHuman() {
        IntentResult r = classifier().tryKeywordClassification("麻烦转人工", new HashMap<>());
        assertEquals(CustomerIntent.REQUEST_HUMAN, r.getIntent());
    }

    @Test
    void weakHandoffKeywordShouldWorkWithoutNegativeContext() {
        IntentResult r = classifier().tryKeywordClassification("我要人工", new HashMap<>());
        assertEquals(CustomerIntent.REQUEST_HUMAN, r.getIntent());
    }

    @Test
    void askingForHotlineShouldNotBeTreatedAsHandoff() {
        IntentResult r = classifier().tryKeywordClassification("你们客服电话是多少", new HashMap<>());
        assertNotEquals(CustomerIntent.REQUEST_HUMAN, r == null ? null : r.getIntent());
    }

    @Test
    void artificialIntelligenceShouldNotBeTreatedAsHandoff() {
        IntentResult r = classifier().tryKeywordClassification("人工智能是什么", new HashMap<>());
        assertNotEquals(CustomerIntent.REQUEST_HUMAN, r == null ? null : r.getIntent());
    }

    @Test
    void unrelatedInputShouldBeOutOfScopeOrNoKeywordHit() {
        // 域外问题允许两条路径：被域外规则拦成 OUT_OF_SCOPE，或关键词无命中交给大模型
        IntentResult r = classifier().tryKeywordClassification("给我讲个笑话", new HashMap<>());
        assertTrue(r == null || r.getIntent() == CustomerIntent.OUT_OF_SCOPE,
                "无关输入不应被判成业务意图，实际=" + (r == null ? "null" : r.getIntent()));
    }
}
