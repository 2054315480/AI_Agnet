package com.qh.ai_agent.service;

import com.qh.ai_agent.entity.Conversation;
import com.qh.ai_agent.entity.ConversationMessage;
import com.qh.ai_agent.entity.DialogAnalytics;
import com.qh.ai_agent.mapper.ConversationMapper;
import com.qh.ai_agent.mapper.ConversationMessageMapper;
import com.qh.ai_agent.mapper.DialogAnalyticsMapper;
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

    /** 会话列表（带分析摘要） */
    public List<Map<String, Object>> getSessionList() {
        List<Conversation> conversations = conversationMapper.selectList(null);

        return conversations.stream().map(conv -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("conversationId", conv.getId());
            item.put("title", conv.getTitle());
            item.put("agentType", conv.getAgentType());
            item.put("createdAt", conv.getCreatedAt());

            List<ConversationMessage> messages = messageMapper.findByConversationId(conv.getId());
            long userTurns = messages.stream().filter(m -> "user".equals(m.getRole())).count();
            item.put("turnCount", userTurns);

            boolean hasHandoff = messages.stream().anyMatch(m -> Boolean.TRUE.equals(m.getIsHandoff()));
            item.put("handoffTriggered", hasHandoff);

            long clarifications = messages.stream().filter(m -> Boolean.TRUE.equals(m.getIsClarification())).count();
            item.put("clarificationCount", clarifications);

            OptionalDouble avgConf = messages.stream()
                    .filter(m -> m.getConfidence() != null)
                    .mapToDouble(ConversationMessage::getConfidence)
                    .average();
            item.put("avgConfidence", avgConf.isPresent() ? String.format("%.2f", avgConf.getAsDouble()) : "N/A");

            // 提取主要意图
            String topIntent = messages.stream()
                    .filter(m -> m.getIntent() != null && "assistant".equals(m.getRole()))
                    .map(ConversationMessage::getIntent)
                    .filter(Objects::nonNull)
                    .collect(Collectors.groupingBy(i -> i, Collectors.counting()))
                    .entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(null);
            item.put("topIntent", topIntent);

            return item;
        }).collect(Collectors.toList());
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

        boolean hasHandoff = messages.stream().anyMatch(m -> Boolean.TRUE.equals(m.getIsHandoff()));
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
        } else {
            analyticsMapper.updateById(analytics);
        }
    }
}
