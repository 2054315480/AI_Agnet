package com.qh.ai_agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qh.ai_agent.entity.DialogAnalytics;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface DialogAnalyticsMapper extends BaseMapper<DialogAnalytics> {

    @Select("SELECT * FROM dialog_analytics ORDER BY created_at DESC")
    List<DialogAnalytics> findAllOrderByCreatedDesc();

    @Select("SELECT * FROM dialog_analytics WHERE conversation_id = #{conversationId}")
    DialogAnalytics findByConversationId(String conversationId);
}
