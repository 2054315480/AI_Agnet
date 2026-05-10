package com.qh.ai_agent.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qh.ai_agent.entity.Conversation;
import com.qh.ai_agent.entity.ConversationMessage;
import com.qh.ai_agent.entity.DialogAnalytics;
import com.qh.ai_agent.mapper.ConversationMapper;
import com.qh.ai_agent.mapper.ConversationMessageMapper;
import com.qh.ai_agent.mapper.DialogAnalyticsMapper;
import com.qh.ai_agent.model.SessionQueryDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final ConversationMapper conversationMapper;
    private final ConversationMessageMapper messageMapper;
    private final DialogAnalyticsMapper analyticsMapper;

    /** 总览统计 */
    public Map<String, Object> getOverview() {
        Map<String, Object> overview = new LinkedHashMap<>();

        // 总会话数
        long totalConversations = conversationMapper.selectCount(null);
        overview.put("totalConversations", totalConversations);

        // 意图分布
        List<Map<String, Object>> intentDist = messageMapper.countByIntent();
        overview.put("intentDistribution", intentDist);

        // 转人工率
        int handoffConvos = messageMapper.countHandoffConversations();
        double handoffRate = totalConversations > 0 ? (double) handoffConvos / totalConversations : 0;
        overview.put("handoffRate", String.format("%.2f", handoffRate));
        overview.put("handoffConversations", handoffConvos);

        // 澄清触发次数
        int clarificationCount = messageMapper.countClarificationMessages();
        overview.put("clarificationCount", clarificationCount);

        // 平均置信度
        Double avgConf = messageMapper.avgConfidence();
        overview.put("avgConfidence", avgConf != null ? String.format("%.2f", avgConf) : "N/A");

        // 平均轮次
        List<Map<String, Object>> turnCounts = messageMapper.countTurnsByConversation();
        double avgTurns = turnCounts.isEmpty() ? 0 :
                turnCounts.stream().mapToInt(m -> ((Number) m.get("turn_count")).intValue()).average().orElse(0);
        overview.put("avgTurns", String.format("%.1f", avgTurns));

        return overview;
    }

    /** 意图分布统计 */
    public List<Map<String, Object>> getIntentDistribution() {
        return messageMapper.countByIntent();
    }

    /** 排序字段白名单（防 SQL 注入） */
    private static final Set<String> ALLOWED_SORT_COLUMNS = Set.of(
            "c.created_at", "da.total_turns", "da.avg_confidence", "da.clarification_count"
    );

    /** 会话列表（分页 + 排序 + 筛选） */
    public Map<String, Object> getSessionList(SessionQueryDTO query) {
        // 参数校验
        int page = Math.max(query.getPage() == null ? 1 : query.getPage(), 1);
        int size = Math.min(Math.max(query.getSize() == null ? 15 : query.getSize(), 1), 100);

        // 排序字段白名单校验
        String sortBy = sanitizeSortBy(query.getSortBy());
        String sortOrder = "asc".equalsIgnoreCase(query.getSortOrder()) ? "ASC" : "DESC";

        log.info("[Analytics] 会话列表查询: page={}, size={}, sortBy={}, sortOrder={}, intent={}, handoff={}, clarification={}",
                page, size, sortBy, sortOrder, query.getIntent(), query.getHandoff(), query.getClarification());

        // 执行分页查询（单条 SQL，利用 dialog_analytics 预聚合数据）
        IPage<Map<String, Object>> pageParam = new Page<>(page, size);
        IPage<Map<String, Object>> result = analyticsMapper.selectSessionPage(
                pageParam, sortBy, sortOrder,
                query.getIntent(), query.getHandoff(), query.getClarification()
        );

        log.info("[Analytics] 会话列表查询结果: total={}, 当前页记录数={}", result.getTotal(), result.getRecords().size());

        // 格式化 avg_confidence
        List<Map<String, Object>> content = result.getRecords().stream().map(row -> {
            Map<String, Object> item = new LinkedHashMap<>(row);
            Object avgConf = row.get("avg_confidence");
            if (avgConf instanceof Number) {
                item.put("avg_confidence", String.format("%.2f", ((Number) avgConf).doubleValue()));
            } else if (avgConf == null) {
                item.put("avg_confidence", "N/A");
            }
            return item;
        }).collect(Collectors.toList());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("content", content);
        response.put("total", result.getTotal());
        response.put("page", result.getCurrent());
        response.put("size", result.getSize());
        response.put("totalPages", result.getPages());
        return response;
    }

    /** 排序字段白名单校验 */
    private String sanitizeSortBy(String sortBy) {
        if (sortBy == null) return "c.created_at";
        String column = "da." + sortBy;
        if (ALLOWED_SORT_COLUMNS.contains(column)) return column;
        if (ALLOWED_SORT_COLUMNS.contains(sortBy)) return sortBy;
        return "c.created_at";
    }

    /** 单会话详情（含完整消息和分析） */
    public Map<String, Object> getSessionDetail(String conversationId) {
        Conversation conv = conversationMapper.selectById(conversationId);
        if (conv == null) return Map.of("error", "会话不存在");

        List<ConversationMessage> messages = messageMapper.findByConversationId(conversationId);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("conversationId", conv.getId());
        detail.put("title", conv.getTitle());
        detail.put("agentType", conv.getAgentType());
        detail.put("createdAt", conv.getCreatedAt());

        // 消息列表（含意图/槽位等分析字段）
        List<Map<String, Object>> messageList = messages.stream().map(msg -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", msg.getId());
            m.put("role", msg.getRole());
            m.put("content", msg.getContent());
            m.put("intent", msg.getIntent());
            m.put("slots", msg.getSlots());
            m.put("confidence", msg.getConfidence());
            m.put("isHandoff", msg.getIsHandoff());
            m.put("isClarification", msg.getIsClarification());
            m.put("sources", msg.getSources());
            m.put("createdAt", msg.getCreatedAt());
            return m;
        }).collect(Collectors.toList());
        detail.put("messages", messageList);

        return detail;
    }

    /**
     * 回填缺失的 dialog_analytics 记录（启动时自动修复历史数据）
     * 针对有 conversation_messages 但没有 dialog_analytics 的会话
     */
    public int backfillMissingAnalytics() {
        // 使用直接 SQL 查找缺失 analytics 的会话，避免 selectList(null) 受分页拦截器的影响
        List<String> missingIds = analyticsMapper.findConversationIdsWithoutAnalytics();
        if (missingIds.isEmpty()) {
            log.info("[Analytics] 回填检查: 所有会话已有 analytics 记录");
            return 0;
        }

        log.info("[Analytics] 回填开始: 发现 {} 条缺失 analytics 的会话", missingIds.size());
        int backfilled = 0;
        for (String convId : missingIds) {
            try {
                updateAnalytics(convId);
                backfilled++;
                log.info("[Analytics] 回填 analytics 记录: sessionId={}", convId);
            } catch (Exception e) {
                log.error("[Analytics] 回填 analytics 失败: sessionId={}", convId, e);
            }
        }
        log.info("[Analytics] 回填完成: 共处理 {} / {} 条缺失记录", backfilled, missingIds.size());
        return backfilled;
    }

    /** 会话结束后更新聚合分析表 */
    public void updateAnalytics(String conversationId) {
        List<ConversationMessage> messages = messageMapper.findByConversationId(conversationId);

        DialogAnalytics analytics = analyticsMapper.findByConversationId(conversationId);
        if (analytics == null) {
            analytics = new DialogAnalytics();
            analytics.setConversationId(conversationId);
            analytics.setCreatedAt(LocalDateTime.now());
        }

        long userTurns = messages.stream().filter(m -> "user".equals(m.getRole())).count();
        analytics.setTotalTurns((int) userTurns);

        // 意图分布
        Map<String, Long> intentDist = messages.stream()
                .filter(m -> m.getIntent() != null)
                .collect(Collectors.groupingBy(ConversationMessage::getIntent, Collectors.counting()));
        analytics.setIntentDistribution(intentDist.isEmpty() ? null : intentDist.toString());

        // 双重判断转人工：is_handoff 标记 + REQUEST_HUMAN 意图（防止 isHandoff 漏设）
        boolean hasHandoff = messages.stream().anyMatch(m ->
                Boolean.TRUE.equals(m.getIsHandoff()) || "REQUEST_HUMAN".equals(m.getIntent()));
        analytics.setHandoffTriggered(hasHandoff);

        long clarCount = messages.stream().filter(m -> Boolean.TRUE.equals(m.getIsClarification())).count();
        analytics.setClarificationCount((int) clarCount);

        OptionalDouble avgConf = messages.stream()
                .filter(m -> m.getConfidence() != null)
                .mapToDouble(ConversationMessage::getConfidence)
                .average();
        analytics.setAvgConfidence(avgConf.isPresent() ? avgConf.getAsDouble() : null);

        analytics.setResolved(!hasHandoff);
        analytics.setUpdatedAt(LocalDateTime.now());

        if (analytics.getId() == null) {
            analyticsMapper.insert(analytics);
            log.info("[Analytics] 新增 analytics 记录: sessionId={}, handoff={}, clarCount={}, turns={}",
                    conversationId, hasHandoff, clarCount, userTurns);
        } else {
            analyticsMapper.updateById(analytics);
            log.info("[Analytics] 更新 analytics 记录: sessionId={}, handoff={}, clarCount={}, turns={}",
                    conversationId, hasHandoff, clarCount, userTurns);
        }
    }
}
