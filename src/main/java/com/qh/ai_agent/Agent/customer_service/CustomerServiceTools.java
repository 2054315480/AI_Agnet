package com.qh.ai_agent.Agent.customer_service;

import com.qh.ai_agent.knowledge.KnowledgeSearchService;
import com.qh.ai_agent.knowledge.KnowledgeSearchService.HybridSearchResult;
import com.qh.ai_agent.knowledge.QueryRewriteService;
import com.qh.ai_agent.knowledge.KnowledgeSearchService.KnowledgeSearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 客服专用工具集
 * queryOrder/queryProduct/cancelOrder/submitRefund — 演示模式，不返回编造数据，引导使用知识库
 * searchKnowledgeBase — 知识库检索（FAQ 直匹配 + RAG 语义检索）
 * transferToHuman — 转接人工客服
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomerServiceTools {

    private final KnowledgeSearchService knowledgeSearchService;

    private final QueryRewriteService queryRewriteService;

    @Tool(description = "查询订单状态和物流信息。当用户询问订单状态、物流、配送进度时使用。注意：当前为演示模式，无真实订单数据，请结合 searchKnowledgeBase 查找订单相关的 FAQ 指引。")
    public String queryOrder(@ToolParam(description = "订单编号") String orderId) {
        log.info("[CS-Tool] queryOrder: {} (无真实数据，返回查询指引)", orderId);
        return """
        {
          "status": "no_data",
          "order_id": "%s",
          "message": "当前系统为演示模式，未查询到该订单的真实数据。请使用 searchKnowledgeBase 工具搜索「订单查询」「物流查询」等相关 FAQ，向用户提供查询指引（如通过APP查询、拨打客服热线等）。",
          "suggestion": "请告知用户当前无法直接查询订单，并引导用户通过以下方式自助查询：1）登录中国移动APP查看 2）拨打10086查询 3）发送CXDD至10086短信查询"
        }
        """.formatted(orderId);
    }

    @Tool(description = "查询产品详细信息。当用户询问产品参数、价格、功能时使用。请优先使用 searchKnowledgeBase 搜索知识库中的产品信息。")
    public String queryProduct(@ToolParam(description = "产品名称或型号") String productName) {
        log.info("[CS-Tool] queryProduct: {} (无真实数据，建议搜索知识库)", productName);
        return """
        {
          "status": "no_data",
          "product_name": "%s",
          "message": "当前系统无真实产品数据库。请使用 searchKnowledgeBase 工具搜索该产品的相关信息。如果知识库中有相关内容，基于知识库回答；如果没有，如实告知用户暂时无法查询该产品详情，并建议联系10086人工客服。"
        }
        """.formatted(productName);
    }

    @Tool(description = "在知识库中搜索匹配的问题和答案。当用户提问涉及FAQ、政策说明、退换货规则、配送规则等常见问题时使用。请传入用户的完整原始提问（不要简化为关键词），搜索范围包含银行/电信/电商三个行业的完整知识库。")
    public String searchKnowledgeBase(@ToolParam(description = "用户的完整原始提问（不要简化为关键词）") String query) {
        String originalQuery = query == null ? "" : query;
        String rewrittenQuery = originalQuery;
        List<String> matchedSynonyms = List.of();
        try {
            QueryRewriteService.QueryRewriteResult rewriteResult =
                    queryRewriteService.rewrite(originalQuery);
            if (rewriteResult != null && rewriteResult.rewrittenQuery() != null
                    && !rewriteResult.rewrittenQuery().isBlank()) {
                rewrittenQuery = rewriteResult.rewrittenQuery();
            }
            if (rewriteResult != null && rewriteResult.matchedSynonyms() != null) {
                matchedSynonyms = rewriteResult.matchedSynonyms();
            }
        } catch (Exception e) {
            // 查询改写只是增强项，失败时继续使用原始 query，不能阻断知识库检索。
            log.warn("[CS-Tool] 查询改写失败，沿用原查询: {}", e.getMessage());
        }
        log.info("[CS-Tool] searchKnowledgeBase: original='{}', rewritten='{}', matchedSynonyms={}",
                originalQuery, rewrittenQuery, matchedSynonyms);
        HybridSearchResult searchResult = knowledgeSearchService.hybridSearch(rewrittenQuery, 3);

        StringBuilder sb = new StringBuilder();
        sb.append("{\"matchType\":\"").append(searchResult.isFaqDirectMatch() ? "faq_direct" : "hybrid_search").append("\"");

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
                // 清洗 Q:/A: 标记，避免模型照抄内部格式
                sb.append("\"content\":").append(escapeJson(
                        com.qh.ai_agent.service.AnswerFormatter.clean(r.content())));
                if (r.source() != null && !r.source().isEmpty()) {
                    sb.append(",\"source\":\"").append(r.source()).append("\"");
                }
                if (r.kbId() != null && !r.kbId().isEmpty()) {
                    sb.append(",\"kbId\":\"").append(r.kbId()).append("\"");
                }
                sb.append(",\"docType\":\"").append(r.docType()).append("\"");
                sb.append(",\"category\":\"").append(r.category()).append("\"");
                sb.append(",\"relevance\":").append(String.format("%.2f", r.relevance()));
                sb.append("}");
            }
            sb.append("]");
        }

        sb.append(",\"query\":").append(escapeJson(originalQuery));
        sb.append(",\"rewrittenQuery\":").append(escapeJson(rewrittenQuery));
        sb.append(",\"matchedSynonyms\":[");
        for (int i = 0; i < matchedSynonyms.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(escapeJson(matchedSynonyms.get(i)));
        }
        sb.append("]}");
        return sb.toString();
    }

    @Tool(description = "取消订单。当用户要求取消订单时使用。注意：当前为演示模式，请结合 searchKnowledgeBase 查找取消订单的流程指引。")
    public String cancelOrder(@ToolParam(description = "要取消的订单编号") String orderId,
                              @ToolParam(description = "取消原因") String reason) {
        log.info("[CS-Tool] cancelOrder: {}, reason: {} (无真实数据，返回指引)", orderId, reason);
        return """
        {
          "status": "no_data",
          "order_id": "%s",
          "message": "当前系统为演示模式，无法执行真实的取消操作。请使用 searchKnowledgeBase 搜索「取消订单」相关 FAQ，向用户说明取消流程。如果知识库中有取消订单的指引，按照指引回答；如果没有，建议用户拨打10086或登录APP操作。"
        }
        """.formatted(orderId);
    }

    @Tool(description = "提交退款申请。当用户要求退款时使用。注意：当前为演示模式，请结合 searchKnowledgeBase 查找退款流程指引。")
    public String submitRefund(@ToolParam(description = "订单编号") String orderId,
                               @ToolParam(description = "退款原因") String reason) {
        log.info("[CS-Tool] submitRefund: {}, reason: {} (无真实数据，返回指引)", orderId, reason);
        return """
        {
          "status": "no_data",
          "order_id": "%s",
          "message": "当前系统为演示模式，无法执行真实的退款操作。请使用 searchKnowledgeBase 搜索「退款」「退换货」相关 FAQ，向用户说明退款流程和注意事项。如果知识库中有退款指引，按照指引回答；如果没有，建议用户拨打10086或登录APP申请退款。"
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
