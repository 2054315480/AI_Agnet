package com.qh.ai_agent.intent;

import com.qh.ai_agent.intent.model.SlotDefinition;
import com.qh.ai_agent.intent.model.SlotDefinition.SlotType;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.util.*;

/**
 * 意图与槽位配置
 */
@Configuration
public class IntentConfig {

    /** 每个意图对应的槽位定义 */
    private final Map<String, List<SlotDefinition>> slotDefinitions = new HashMap<>();

    /** 每个槽位的澄清问题模板 */
    private final Map<String, String> clarificationTemplates = new HashMap<>();

    @PostConstruct
    public void init() {
        // 订单号
        SlotDefinition orderId = SlotDefinition.builder()
                .slotName("order_id").slotType(SlotType.STRING)
                .description("订单编号，通常为纯数字或字母数字组合")
                .required(true).regex("(?:订单号?|order)[:\\s]*(\\w+)")
                .build();

        // 产品名
        SlotDefinition productName = SlotDefinition.builder()
                .slotName("product_name").slotType(SlotType.STRING)
                .description("产品名称或型号")
                .required(true).build();

        // 退款原因
        SlotDefinition refundReason = SlotDefinition.builder()
                .slotName("refund_reason").slotType(SlotType.ENUM)
                .description("退款原因")
                .required(true).enumValues(List.of("质量问题", "不想要了", "发错货", "描述不符", "其他"))
                .build();

        // 问题类型
        SlotDefinition issueType = SlotDefinition.builder()
                .slotName("issue_type").slotType(SlotType.ENUM)
                .description("问题类型")
                .required(true).enumValues(List.of("物流问题", "质量问题", "服务态度", "虚假宣传", "其他"))
                .build();

        // 查询类型
        SlotDefinition queryType = SlotDefinition.builder()
                .slotName("query_type").slotType(SlotType.ENUM)
                .description("查询类型").required(false)
                .enumValues(List.of("物流状态", "订单状态", "配送时间", "其他"))
                .build();

        // 注册意图-槽位映射
        slotDefinitions.put("ORDER_QUERY", List.of(orderId, queryType));
        slotDefinitions.put("ORDER_CANCEL", List.of(orderId));
        slotDefinitions.put("ORDER_REFUND", List.of(orderId, refundReason));
        slotDefinitions.put("PRODUCT_INFO", List.of(productName));
        slotDefinitions.put("PRODUCT_RECOMMEND", List.of());
        slotDefinitions.put("COMPLAINT", List.of(issueType));
        slotDefinitions.put("AFTER_SALE", List.of(issueType));
        slotDefinitions.put("POLICY_QUERY", List.of());
        slotDefinitions.put("CHITCHAT", List.of());
        slotDefinitions.put("REQUEST_HUMAN", List.of());
        slotDefinitions.put("CLARIFICATION", List.of());
        slotDefinitions.put("OUT_OF_SCOPE", List.of());

        // 澄清问题模板
        clarificationTemplates.put("order_id", "请问您的订单号是多少？");
        clarificationTemplates.put("product_name", "请问您想了解哪个产品？");
        clarificationTemplates.put("refund_reason", "请问退款的原因是什么？（质量问题/不想要了/发错货/描述不符/其他）");
        clarificationTemplates.put("issue_type", "请问您遇到的是哪类问题？（物流问题/质量问题/服务态度/虚假宣传/其他）");
    }

    public List<SlotDefinition> getSlotDefinitions(String intentName) {
        return slotDefinitions.getOrDefault(intentName, Collections.emptyList());
    }

    public String getClarificationQuestion(String slotName) {
        return clarificationTemplates.getOrDefault(slotName, "请提供更多信息。");
    }

    public Map<String, List<SlotDefinition>> getAllSlotDefinitions() {
        return Collections.unmodifiableMap(slotDefinitions);
    }
}
