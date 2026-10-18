package com.qh.ai_agent.intent;

import com.qh.ai_agent.intent.model.CustomerIntent;
import com.qh.ai_agent.intent.model.IntentResult;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
    void unrelatedInputShouldHaveNoKeywordHit() {
        assertNull(classifier().tryKeywordClassification("给我讲个笑话", new HashMap<>()));
    }
}
