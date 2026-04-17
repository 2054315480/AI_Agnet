package com.qh.ai_agent.chatmemory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于 Redis 的聊天记忆实现
 * 使用 Redis 存储对话消息，支持 TTL 自动过期
 *
 * 存储结构：
 * - Key: {keyPrefix}{conversationId}
 * - Value: JSON 序列化的消息列表
 * - TTL: 可配置的过期时间
 */
@Slf4j
public class RedisChatMemory implements ChatMemory {

    private final RedisTemplate<String, Object> redisTemplate;
    private final String keyPrefix;
    private final long ttlHours;
    private final ObjectMapper objectMapper;

    public RedisChatMemory(RedisTemplate<String, Object> redisTemplate,
                           String keyPrefix, long ttlHours) {
        this.redisTemplate = redisTemplate;
        this.keyPrefix = keyPrefix;
        this.ttlHours = ttlHours;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public void add(String conversationId, Message message) {
        log.debug("Adding single message to Redis conversation: {}", conversationId);
        List<Map<String, String>> dtoList = loadFromRedis(conversationId);
        dtoList.add(toDto(message));
        saveToRedis(conversationId, dtoList);
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        log.debug("Adding {} messages to Redis conversation: {}", messages.size(), conversationId);
        List<Map<String, String>> dtoList = loadFromRedis(conversationId);
        for (Message message : messages) {
            dtoList.add(toDto(message));
        }
        saveToRedis(conversationId, dtoList);
    }

    @Override
    public List<Message> get(String conversationId) {
        log.debug("Retrieving messages from Redis conversation: {}", conversationId);
        List<Map<String, String>> dtoList = loadFromRedis(conversationId);
        List<Message> messages = new ArrayList<>();
        for (Map<String, String> dto : dtoList) {
            messages.add(fromDto(dto));
        }
        log.debug("Found {} messages in Redis conversation: {}", messages.size(), conversationId);
        return messages;
    }

    @Override
    public void clear(String conversationId) {
        String key = keyPrefix + conversationId;
        redisTemplate.delete(key);
        log.debug("Cleared Redis chat memory for conversation: {}", conversationId);
    }

    /**
     * 从 Redis 加载消息 DTO 列表
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, String>> loadFromRedis(String conversationId) {
        String key = keyPrefix + conversationId;
        try {
            Object raw = redisTemplate.opsForValue().get(key);
            if (raw == null) {
                return new ArrayList<>();
            }
            // 如果 raw 已经是 List（Jackson 反序列化后的结果）
            if (raw instanceof List) {
                List<?> list = (List<?>) raw;
                List<Map<String, String>> result = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof Map) {
                        result.add((Map<String, String>) item);
                    }
                }
                return result;
            }
            // 如果是 String，手动反序列化
            if (raw instanceof String) {
                return objectMapper.readValue((String) raw,
                        new TypeReference<List<Map<String, String>>>() {});
            }
            return new ArrayList<>();
        } catch (Exception e) {
            log.error("Failed to load messages from Redis for conversation: {}", conversationId, e);
            return new ArrayList<>();
        }
    }

    /**
     * 保存消息 DTO 列表到 Redis
     */
    private void saveToRedis(String conversationId, List<Map<String, String>> dtoList) {
        String key = keyPrefix + conversationId;
        try {
            redisTemplate.opsForValue().set(key, dtoList, Duration.ofHours(ttlHours));
            log.debug("Saved {} messages to Redis for conversation: {}",
                    dtoList.size(), conversationId);
        } catch (Exception e) {
            log.error("Failed to save messages to Redis for conversation: {}", conversationId, e);
        }
    }

    /**
     * 将 Message 转换为 DTO Map
     */
    private Map<String, String> toDto(Message message) {
        Map<String, String> dto = new HashMap<>();
        dto.put("role", message.getMessageType().name());
        String content = switch (message.getMessageType()) {
            case USER -> ((UserMessage) message).getText();
            case ASSISTANT -> ((AssistantMessage) message).getText();
            case SYSTEM -> ((SystemMessage) message).getText();
            default -> "";
        };
        dto.put("content", content);
        return dto;
    }

    /**
     * 从 DTO Map 还原为 Message 对象
     */
    private Message fromDto(Map<String, String> dto) {
        String role = dto.get("role");
        String content = dto.getOrDefault("content", "");
        if ("USER".equals(role)) {
            return new UserMessage(content);
        } else if ("ASSISTANT".equals(role)) {
            return new AssistantMessage(content);
        } else if ("SYSTEM".equals(role)) {
            return new SystemMessage(content);
        }
        return new UserMessage(content);
    }
}
