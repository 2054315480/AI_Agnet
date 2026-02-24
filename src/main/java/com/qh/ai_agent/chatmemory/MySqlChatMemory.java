package com.qh.ai_agent.chatmemory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qh.ai_agent.entity.ChatMemoryEntity;
import com.qh.ai_agent.mapper.ChatMemoryMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 基于 MyBatis-Plus 的 MySQL 聊天记忆实现
 * 提供对话消息的持久化存储和检索功能
 */
@Slf4j
public class MySqlChatMemory implements ChatMemory {

    private final ChatMemoryMapper chatMemoryMapper;

    /**
     * 构造函数，注入 ChatMemoryMapper
     *
     * @param chatMemoryMapper MyBatis-Plus Mapper
     */
    public MySqlChatMemory(ChatMemoryMapper chatMemoryMapper) {
        this.chatMemoryMapper = chatMemoryMapper;
    }

    /**
     * 批量添加消息到指定会话
     *
     * @param conversationId 会话 ID
     * @param messages 消息列表
     */
    @Override
    @Transactional
    public void add(String conversationId, List<Message> messages) {
        log.debug("Adding {} messages to conversation: {}", messages.size(), conversationId);
        List<ChatMemoryEntity> entities = messages.stream()
                .map(message -> {
                    ChatMemoryEntity entity = new ChatMemoryEntity();
                    entity.setConversationId(conversationId);
                    entity.setRole(message.getMessageType().name());
                    entity.setMessageType(message.getMessageType());
                    entity.setCreatedAt(LocalDateTime.now());

                    String content = extractText(message);
                    entity.setContent(content);
                    return entity;
                })
                .collect(Collectors.toList());

        // 使用 MyBatis-Plus 的批量插入方法
        chatMemoryMapper.insertBatch(entities);
        log.debug("Successfully added {} messages", entities.size());
    }

    /**
     * 添加单条消息到指定会话
     *
     * @param conversationId 会话 ID
     * @param message 消息对象
     */
    @Override
    @Transactional
    public void add(String conversationId, Message message) {
        log.debug("Adding single message to conversation: {}, type: {}",
                conversationId, message.getMessageType());
        ChatMemoryEntity entity = new ChatMemoryEntity();
        entity.setConversationId(conversationId);
        entity.setRole(message.getMessageType().name());
        entity.setMessageType(message.getMessageType());
        entity.setCreatedAt(LocalDateTime.now());

        String content = extractText(message);
        entity.setContent(content);

        // 使用 MyBatis-Plus 的插入方法
        chatMemoryMapper.insert(entity);
        log.debug("Successfully added message");
    }

    /**
     * 获取指定会话的所有消息
     *
     * @param conversationId 会话 ID
     * @return 消息列表
     */
    @Override
    public List<Message> get(String conversationId) {
        log.debug("Retrieving messages for conversation: {}", conversationId);
        // 使用自定义查询方法
        List<ChatMemoryEntity> entities = chatMemoryMapper.findByConversationIdOrderByCreatedAtAsc(conversationId);
        log.debug("Found {} messages", entities.size());
        return entities.stream()
                .map(this::convertToMessage)
                .collect(Collectors.toList());
    }

    /**
     * 清除指定会话的所有消息
     *
     * @param conversationId 会话 ID
     */
    @Override
    @Transactional
    public void clear(String conversationId) {
        log.debug("Clearing conversation: {}", conversationId);
        // 使用 MyBatis-Plus 的 LambdaQueryWrapper 构建删除条件
        LambdaQueryWrapper<ChatMemoryEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatMemoryEntity::getConversationId, conversationId);
        chatMemoryMapper.delete(wrapper);
        log.debug("Successfully cleared conversation");
    }

    /**
     * 从消息对象中提取文本内容
     *
     * @param message 消息对象
     * @return 文本内容
     */
    private String extractText(Message message) {
        return switch (message.getMessageType()) {
            case USER -> ((UserMessage) message).getText();
            case ASSISTANT -> ((AssistantMessage) message).getText();
            case SYSTEM -> ((SystemMessage) message).getText();
            default -> "";
        };
    }

    /**
     * 将实体对象转换为消息对象
     *
     * @param entity 聊天记忆实体
     * @return 消息对象
     */
    private Message convertToMessage(ChatMemoryEntity entity) {
        return switch (entity.getMessageType()) {
            case USER -> new UserMessage(entity.getContent());
            case ASSISTANT -> new AssistantMessage(entity.getContent());
            case SYSTEM -> new SystemMessage(entity.getContent());
            default -> new UserMessage(entity.getContent());
        };
    }
}
