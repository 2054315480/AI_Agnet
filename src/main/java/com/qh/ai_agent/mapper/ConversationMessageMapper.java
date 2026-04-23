package com.qh.ai_agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qh.ai_agent.entity.ConversationMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface ConversationMessageMapper extends BaseMapper<ConversationMessage> {

    @Select("SELECT * FROM conversation_messages WHERE conversation_id = #{conversationId} ORDER BY created_at ASC")
    List<ConversationMessage> findByConversationId(String conversationId);

    /** 意图分布统计 */
    @Select("SELECT intent, COUNT(*) as count FROM conversation_messages " +
            "WHERE intent IS NOT NULL GROUP BY intent ORDER BY count DESC")
    List<Map<String, Object>> countByIntent();

    /** 转人工次数 */
    @Select("SELECT COUNT(DISTINCT conversation_id) FROM conversation_messages WHERE is_handoff = true")
    int countHandoffConversations();

    /** 澄清触发次数 */
    @Select("SELECT COUNT(*) FROM conversation_messages WHERE is_clarification = true")
    int countClarificationMessages();

    /** 平均置信度 */
    @Select("SELECT AVG(confidence) FROM conversation_messages WHERE confidence IS NOT NULL")
    Double avgConfidence();

    /** 按 conversation 分组统计轮次 */
    @Select("SELECT conversation_id, COUNT(*) as turn_count FROM conversation_messages " +
            "WHERE role = 'user' GROUP BY conversation_id")
    List<Map<String, Object>> countTurnsByConversation();
}
