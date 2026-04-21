package com.qh.ai_agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qh.ai_agent.entity.Conversation;
import com.qh.ai_agent.entity.ConversationMessage;
import com.qh.ai_agent.mapper.ConversationMapper;
import com.qh.ai_agent.mapper.ConversationMessageMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class ConversationService {

    private final ConversationMapper conversationMapper;
    private final ConversationMessageMapper messageMapper;

    public ConversationService(ConversationMapper conversationMapper, ConversationMessageMapper messageMapper) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
    }

    public List<Conversation> listByUserId(Long userId) {
        return conversationMapper.selectList(
                new LambdaQueryWrapper<Conversation>()
                        .eq(Conversation::getUserId, userId)
                        .orderByDesc(Conversation::getUpdatedAt)
        );
    }

    public Conversation getById(String id) {
        return conversationMapper.selectById(id);
    }

    public Conversation create(String id, Long userId, String title, String agentType) {
        Conversation conv = new Conversation();
        conv.setId(id);
        conv.setUserId(userId);
        conv.setTitle(title);
        conv.setAgentType(agentType);
        conv.setCreatedAt(LocalDateTime.now());
        conv.setUpdatedAt(LocalDateTime.now());
        conversationMapper.insert(conv);
        return conv;
    }

    public void updateTitle(String id, String title) {
        Conversation conv = conversationMapper.selectById(id);
        if (conv != null) {
            conv.setTitle(title);
            conv.setUpdatedAt(LocalDateTime.now());
            conversationMapper.updateById(conv);
        }
    }

    @Transactional
    public void delete(String id) {
        messageMapper.delete(new LambdaQueryWrapper<ConversationMessage>()
                .eq(ConversationMessage::getConversationId, id));
        conversationMapper.deleteById(id);
    }

    public void addMessage(String conversationId, String role, String content) {
        ConversationMessage msg = new ConversationMessage();
        msg.setConversationId(conversationId);
        msg.setRole(role);
        msg.setContent(content);
        msg.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(msg);

        // 更新对话的 updated_at
        Conversation conv = conversationMapper.selectById(conversationId);
        if (conv != null) {
            conv.setUpdatedAt(LocalDateTime.now());
            conversationMapper.updateById(conv);
        }
    }

    public List<ConversationMessage> getMessages(String conversationId) {
        return messageMapper.findByConversationId(conversationId);
    }
}
