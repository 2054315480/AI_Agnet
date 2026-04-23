package com.qh.ai_agent.agent.customer_service;

import com.qh.ai_agent.knowledge.KnowledgeSearchService;
import com.qh.ai_agent.knowledge.KnowledgeSearchService.KnowledgeSearchResult;
import com.qh.ai_agent.knowledge.KnowledgeSearchService.SearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 客服专用工具集
 * [MOCK] queryOrder/queryProduct/cancelOrder/submitRefund 使用硬编码模拟数据
 *       searchKnowledgeBase 已接入 KnowledgeSearchService（底层仍为模拟数据）
 *       transferToHuman 不涉及数据
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomerServiceTools {

    private final KnowledgeSearchService knowledgeSearchService;

    @Tool(description = "根据订单号查询订单状态和物流信息。当用户询问订单状态、物流、配送进度时使用。")
    public String queryOrder(@ToolParam(description = "订单编号") String orderId) {
        log.info("[CS-Tool] queryOrder: {}", orderId);
        // [MOCK] 模拟订单数据
        return """
        {
          "order_id": "%s",
          "status": "已发货",
          "logistics": "顺丰快递 SF1234567890",
          "current_location": "北京分拨中心",
          "estimated_delivery": "预计明天下午送达",
          "order_time": "2026-04-20 10:30:00",
          "items": ["智能手表Pro x1", "蓝牙耳机 x1"],
          "total_amount": "¥1,299.00"
        }
        """.formatted(orderId);
    }

    @Tool(description = "根据产品名称查询产品详细信息。当用户询问产品参数、功能、价格、规格时使用。")
    public String queryProduct(@ToolParam(description = "产品名称或型号") String productName) {
        log.info("[CS-Tool] queryProduct: {}", productName);
        // [MOCK] 模拟产品数据
        return """
        {
          "product_name": "%s",
          "price": "¥799.00",
          "specs": {
            "屏幕": "1.4英寸AMOLED",
            "电池": "300mAh，续航7天",
            "防水": "IP68",
            "传感器": "心率、血氧、加速度"
          },
          "warranty": "一年质保",
          "in_stock": true,
          "rating": 4.8,
          "reviews_count": 12580
        }
        """.formatted(productName);
    }

    @Tool(description = "在知识库中搜索匹配的问题和答案。当用户提问涉及FAQ、政策说明、退换货规则、配送规则等常见问题时使用。")
    public String searchKnowledgeBase(@ToolParam(description = "搜索关键词或问题描述") String query) {
        log.info("[CS-Tool] searchKnowledgeBase: {}", query);
        SearchResult searchResult = knowledgeSearchService.search(query, 3);

        StringBuilder sb = new StringBuilder();
        sb.append("{\"matchType\":\"").append(searchResult.isFaqDirectMatch() ? "faq_direct" : "rag_search").append("\"");

        // 推荐问题
        sb.append(",\"suggestedQuestions\":[");
        List<String> suggested = searchResult.suggestedQuestions();
        for (int i = 0; i < suggested.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(escapeJson(suggested.get(i)));
        }
        sb.append("]");

        // 搜索结果
        List<KnowledgeSearchResult> results = searchResult.results();
        if (results.isEmpty()) {
            sb.append(",\"results\":[],\"message\":\"知识库中未找到相关内容，建议转人工处理。\"");
        } else {
            sb.append(",\"results\":[");
            for (int i = 0; i < results.size(); i++) {
                KnowledgeSearchResult r = results.get(i);
                if (i > 0) sb.append(",");
                sb.append("{");
                sb.append("\"content\":").append(escapeJson(r.content()));
                if (r.source() != null && !r.source().isEmpty()) {
                    sb.append(",\"source\":\"").append(r.source()).append("\"");
                }
                sb.append(",\"docType\":\"").append(r.docType()).append("\"");
                sb.append(",\"category\":\"").append(r.category()).append("\"");
                sb.append(",\"relevance\":").append(String.format("%.2f", r.relevance()));
                sb.append("}");
            }
            sb.append("]");
        }

        sb.append(",\"query\":\"").append(query).append("\"}");
        return sb.toString();
    }

    @Tool(description = "取消指定订单。当用户明确要求取消订单时使用。")
    public String cancelOrder(@ToolParam(description = "要取消的订单编号") String orderId,
                              @ToolParam(description = "取消原因") String reason) {
        log.info("[CS-Tool] cancelOrder: {}, reason: {}", orderId, reason);
        // [MOCK] 模拟取消结果
        return """
        {
          "order_id": "%s",
          "cancel_status": "成功",
          "message": "订单已取消，退款将在3-5个工作日内原路返回。",
          "refund_amount": "¥1,299.00"
        }
        """.formatted(orderId);
    }

    @Tool(description = "提交退款申请。当用户要求退款时使用。")
    public String submitRefund(@ToolParam(description = "订单编号") String orderId,
                               @ToolParam(description = "退款原因") String reason) {
        log.info("[CS-Tool] submitRefund: {}, reason: {}", orderId, reason);
        // [MOCK] 模拟退款结果
        return """
        {
          "order_id": "%s",
          "refund_status": "已提交",
          "ticket_id": "RF-20260422-001",
          "message": "退款申请已提交，预计1-2个工作日审核。审核通过后退款将在3-5个工作日内原路返回。",
          "refund_amount": "¥1,299.00"
        }
        """.formatted(orderId);
    }

    @Tool(description = "转接人工客服。当用户明确要求转人工，或问题超出AI服务范围时使用。")
    public String transferToHuman(@ToolParam(description = "转人工原因") String reason) {
        log.info("[CS-Tool] transferToHuman: {}", reason);
        return """
        {
          "status": "transferring",
          "message": "正在为您转接人工客服，请稍候...",
          "queue_position": 2,
          "estimated_wait": "约2分钟"
        }
        """;
    }

    private String escapeJson(String text) {
        return "\"" + text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t") + "\"";
    }
}
