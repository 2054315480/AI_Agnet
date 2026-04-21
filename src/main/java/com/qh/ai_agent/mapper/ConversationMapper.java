package com.qh.ai_agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qh.ai_agent.entity.Conversation;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ConversationMapper extends BaseMapper<Conversation> {
}
