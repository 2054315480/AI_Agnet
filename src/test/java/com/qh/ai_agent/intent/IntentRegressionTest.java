package com.qh.ai_agent.intent;

import com.qh.ai_agent.intent.model.CustomerIntent;
import com.qh.ai_agent.intent.model.IntentResult;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 意图回归集（对应验收清单 P5-3）。
 *
 * 线上实测：`1+1等于几`、`你们公司的老板是谁` 被判成 POLICY_QUERY，
 * 然后去检索知识库并引用无关文档。这里固定 30 条域外样本 + 10 条业务反例，
 * 保证域外拦截规则不会误伤业务问题。
 */
class IntentRegressionTest {

    private IntentClassifier classifier() {
        IntentConfig config = new IntentConfig();
        config.init();
        return new IntentClassifier(null, config, null);
    }

    /** 30 条域外样本：必须被判为 OUT_OF_SCOPE，且不能被关键词预筛判成业务意图 */
    private static final List<String> OUT_OF_DOMAIN = List.of(
            "1+1等于几",
            "3*5等于多少",
            "100-37等于几",
            "今天天气怎么样",
            "明天会下雨吗",
            "北京气温多少度",
            "帮我写一首诗",
            "写一篇作文",
            "写个小说",
            "推荐一部好看的电影",
            "有什么好看的电视剧",
            "推荐几首好听的歌",
            "你们公司的老板是谁",
            "你们创始人是谁",
            "公司的CEO是谁",
            "你是谁",
            "你叫什么名字",
            "你会什么",
            "给我讲讲星座",
            "帮我算算命",
            "推荐几只股票",
            "买彩票能中奖吗",
            "1加1等于几",
            "2的平方是多少",
            "讲个笑话",
            "今天几号",
            "现在几点了",
            "帮我翻译这句话",
            "2的平方根是多少"
    );

    /** 10 条业务反例：不能被域外规则误伤 */
    private static final List<String> IN_DOMAIN = List.of(
            "退货政策是什么",
            "我想退款",
            "我的订单到哪了",
            "怎么取消订单",
            "耳机坏了能保修吗",
            "配送要多久",
            "发票怎么开",
            "宽带怎么换运营商",
            "信用卡怎么注销",
            "理赔需要什么材料"
    );

    @Test
    void outOfDomainSamplesShouldNotBeBusinessIntent() {
        IntentClassifier c = classifier();
        int failed = 0;
        for (String q : OUT_OF_DOMAIN) {
            IntentResult r = c.tryKeywordClassification(q, new HashMap<>());
            boolean ok = r != null && r.getIntent() == CustomerIntent.OUT_OF_SCOPE;
            if (!ok) {
                failed++;
                System.out.println("[FAIL] 域外样本未拦截: " + q + " -> " + (r == null ? "null" : r.getIntent()));
            }
        }
        assertEquals(0, failed, "有 " + failed + " 条域外样本未被拦截");
    }

    @Test
    void inDomainSamplesShouldNotBeBlocked() {
        IntentClassifier c = classifier();
        for (String q : IN_DOMAIN) {
            assertFalse(c.looksOutOfDomain(q), "业务问题被域外规则误伤: " + q);
        }
    }

    /** 规则必须覆盖的样本（规则兜底，不依赖大模型） */
    @Test
    void outOfDomainSamplesShouldBeDetectedByRule() {
        IntentClassifier c = classifier();
        for (String q : OUT_OF_DOMAIN) {
            if (LLM_ONLY.contains(q)) {
                continue;
            }
            assertTrue(c.looksOutOfDomain(q), "域外规则未识别: " + q);
        }
    }

    /** 纯语义型域外样本：规则不强求覆盖，但关键词预筛不能把它们判成业务意图 */
    private static final List<String> LLM_ONLY = List.of(
            "地球为什么是圆的",
            "讲个笑话"
    );

    @Test
    void semanticsOnlySamplesShouldNotBecomeBusinessIntent() {
        IntentClassifier c = classifier();
        for (String q : LLM_ONLY) {
            IntentResult r = c.tryKeywordClassification(q, new HashMap<>());
            assertTrue(r == null || r.getIntent() == CustomerIntent.OUT_OF_SCOPE,
                    "语义型域外样本被误判成业务意图: " + q);
        }
    }
}
