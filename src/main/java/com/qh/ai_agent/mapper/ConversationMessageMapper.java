package com.qh.ai_agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qh.ai_agent.entity.ConversationMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ConversationMessageMapper extends BaseMapper<ConversationMessage> {

    @Select("SELECT * FROM conversation_messages WHERE conversation_id = #{conversationId} ORDER BY created_at ASC")
    List<ConversationMessage> findByConversationId(String conversationId);
}
