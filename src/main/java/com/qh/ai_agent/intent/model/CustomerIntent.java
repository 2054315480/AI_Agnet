package com.qh.ai_agent.intent.model;

import lombok.Getter;

/**
 * 客服意图枚举，每个意图携带中文描述和必填槽位
 */
@Getter
public enum CustomerIntent {

    ORDER_QUERY("订单查询", "查询订单状态、物流信息", new String[]{"order_id"}),
    ORDER_CANCEL("取消订单", "取消已有订单", new String[]{"order_id"}),
    ORDER_REFUND("退款申请", "申请退款或退货", new String[]{"order_id", "refund_reason"}),
    PRODUCT_INFO("产品咨询", "咨询产品参数、功能、价格等信息", new String[]{"product_name"}),
    PRODUCT_RECOMMEND("产品推荐", "根据需求推荐合适的产品", new String[]{}),
    COMPLAINT("投诉建议", "投诉服务质量或提出建议", new String[]{"issue_type"}),
    AFTER_SALE("售后服务", "维修、换货、保修等售后问题", new String[]{"issue_type"}),
    POLICY_QUERY("政策查询", "退换货政策、保修政策、配送政策等", new String[]{}),
    CHITCHAT("闲聊", "寒暄、感谢、打招呼等非业务对话", new String[]{}),
    REQUEST_HUMAN("转人工", "用户要求转接人工客服", new String[]{}),
    CLARIFICATION("用户澄清", "用户回答系统的追问", new String[]{}),
    OUT_OF_SCOPE("超出范围", "不属于客服范围的问题", new String[]{});

    private final String displayName;
    private final String description;
    private final String[] requiredSlots;

    CustomerIntent(String displayName, String description, String[] requiredSlots) {
        this.displayName = displayName;
        this.description = description;
        this.requiredSlots = requiredSlots;
    }
}
