package com.qh.ai_agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.qh.ai_agent.entity.DialogAnalytics;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface DialogAnalyticsMapper extends BaseMapper<DialogAnalytics> {

    @Select("SELECT * FROM dialog_analytics ORDER BY created_at DESC")
    List<DialogAnalytics> findAllOrderByCreatedDesc();

    @Select("SELECT * FROM dialog_analytics WHERE conversation_id = #{conversationId}")
    DialogAnalytics findByConversationId(String conversationId);

    /** 查找缺少 dialog_analytics 记录的会话 ID（避免 selectList 受分页拦截器影响） */
    @Select("SELECT c.id FROM conversations c WHERE NOT EXISTS (SELECT 1 FROM dialog_analytics da WHERE da.conversation_id = c.id)")
    List<String> findConversationIdsWithoutAnalytics();

    /**
     * 直接 SQL upsert 聚合分析数据（从 conversation_messages 实时计算）
     * 用在对话结束后立即持久化，确保可靠性
     */
    @Insert("INSERT INTO dialog_analytics (conversation_id, total_turns, handoff_triggered, " +
            "clarification_count, avg_confidence, resolved, created_at, updated_at) " +
            "SELECT " +
            "  #{conversationId}, " +
            "  COUNT(DISTINCT CASE WHEN role = 'user' THEN id END), " +
            "  COALESCE(bool_or(is_handoff), false) OR COALESCE(bool_or(intent = 'REQUEST_HUMAN'), false), " +
            "  COUNT(DISTINCT CASE WHEN is_clarification THEN id END), " +
            "  AVG(confidence) FILTER (WHERE confidence IS NOT NULL), " +
            "  NOT (COALESCE(bool_or(is_handoff), false) OR COALESCE(bool_or(intent = 'REQUEST_HUMAN'), false)), " +
            "  NOW(), NOW() " +
            "FROM conversation_messages WHERE conversation_id = #{conversationId} " +
            "ON CONFLICT (conversation_id) DO UPDATE SET " +
            "  total_turns = EXCLUDED.total_turns, " +
            "  handoff_triggered = EXCLUDED.handoff_triggered, " +
            "  clarification_count = EXCLUDED.clarification_count, " +
            "  avg_confidence = EXCLUDED.avg_confidence, " +
            "  resolved = EXCLUDED.resolved, " +
            "  updated_at = NOW()")
    void upsertAnalyticsDirect(String conversationId);

    /**
     * 分页查询会话列表（LEFT JOIN conversations + dialog_analytics）
     * 使用 LEFT JOIN 确保没有 dialog_analytics 记录的会话也能被筛选和显示
     * sortBy 和 sortOrder 由 Service 层白名单校验后传入
     */
    @Select("<script>" +
            "SELECT c.id AS conversation_id, c.title, c.agent_type, c.created_at, " +
            "  COALESCE(da.total_turns, 0) AS total_turns, " +
            "  COALESCE(da.handoff_triggered, false) AS handoff_triggered, " +
            "  COALESCE(da.clarification_count, 0) AS clarification_count, " +
            "  COALESCE(da.avg_confidence, 0) AS avg_confidence, " +
            "  da.intent_distribution, " +
            "  (SELECT m.intent FROM conversation_messages m " +
            "    WHERE m.conversation_id = c.id AND m.intent IS NOT NULL " +
            "    GROUP BY m.intent ORDER BY COUNT(*) DESC LIMIT 1) AS top_intent " +
            "FROM conversations c " +
            "LEFT JOIN dialog_analytics da ON da.conversation_id = c.id " +
            "<where> " +
            "  <if test='intent != null and intent != \"\"'> " +
            "    AND EXISTS (SELECT 1 FROM conversation_messages m WHERE m.conversation_id = c.id AND m.intent = #{intent}) " +
            "  </if> " +
            "  <if test='handoff != null'> " +
            "    AND COALESCE(da.handoff_triggered, false) = #{handoff} " +
            "  </if> " +
            "  <if test='clarification != null'> " +
            "    <if test='clarification == true'> " +
            "      AND COALESCE(da.clarification_count, 0) &gt; 0 " +
            "    </if> " +
            "    <if test='clarification == false'> " +
            "      AND COALESCE(da.clarification_count, 0) = 0 " +
            "    </if> " +
            "  </if> " +
            "</where> " +
            "ORDER BY ${sortBy} ${sortOrder}" +
            "</script>")
    IPage<Map<String, Object>> selectSessionPage(
            IPage<?> page,
            @Param("sortBy") String sortBy,
            @Param("sortOrder") String sortOrder,
            @Param("intent") String intent,
            @Param("handoff") Boolean handoff,
            @Param("clarification") Boolean clarification
    );
}
