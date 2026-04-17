package com.qh.ai_agent.config;

import com.qh.ai_agent.chatmemory.FileBasedChatMemory;
import com.qh.ai_agent.chatmemory.MySqlChatMemory;
import com.qh.ai_agent.chatmemory.RedisChatMemory;
import com.qh.ai_agent.mapper.ChatMemoryMapper;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * 聊天记忆配置类
 * 根据配置选择使用文件存储、MySQL 数据库存储或 Redis 存储
 */
@Configuration
public class ChatMemoryConfig {

    @Value("${chat.memory.type:file}")
    private String chatMemoryType;

    @Value("${chat.memory.file-dir:./chat-memory}")
    private String chatMemoryFileDir;

    @Value("${chat.memory.redis.key-prefix:chat:memory:}")
    private String redisKeyPrefix;

    @Value("${chat.memory.redis.ttl-hours:24}")
    private long redisTtlHours;

    /**
     * 文件存储方式的聊天记忆 Bean
     * 当 chat.memory.type 配置为 "file" 时生效
     */
    @Bean
    @ConditionalOnProperty(name = "chat.memory.type", havingValue = "file", matchIfMissing = true)
    public ChatMemory fileBasedChatMemory() {
        return new FileBasedChatMemory(chatMemoryFileDir);
    }

    /**
     * MySQL 存储方式的聊天记忆 Bean
     * 当 chat.memory.type 配置为 "mysql" 时生效
     */
    @Bean
    @ConditionalOnProperty(name = "chat.memory.type", havingValue = "mysql")
    public ChatMemory mySqlChatMemory(ChatMemoryMapper chatMemoryMapper) {
        return new MySqlChatMemory(chatMemoryMapper);
    }

    /**
     * Redis 存储方式的聊天记忆 Bean
     * 当 chat.memory.type 配置为 "redis" 时生效
     */
    @Bean
    @ConditionalOnProperty(name = "chat.memory.type", havingValue = "redis")
    public ChatMemory redisChatMemory(RedisTemplate<String, Object> redisTemplate) {
        return new RedisChatMemory(redisTemplate, redisKeyPrefix, redisTtlHours);
    }
}
