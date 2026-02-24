package com.qh.ai_agent.config;

import com.qh.ai_agent.chatmemory.FileBasedChatMemory;
import com.qh.ai_agent.chatmemory.MySqlChatMemory;
import com.qh.ai_agent.mapper.ChatMemoryMapper;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 聊天记忆配置类
 * 根据配置选择使用文件存储或 MySQL 数据库存储
 */
@Configuration
public class ChatMemoryConfig {

    @Value("${chat.memory.type:mysql}")
    private String chatMemoryType;

    @Value("${chat.memory.file-dir:./chat-memory}")
    private String chatMemoryFileDir;

    /**
     * 文件存储方式的聊天记忆 Bean
     * 当 chat.memory.type 配置为 "file" 时生效
     *
     * @return 文件存储的聊天记忆实例
     */
    @Bean
    @ConditionalOnProperty(name = "chat.memory.type", havingValue = "file", matchIfMissing = false)
    public ChatMemory fileBasedChatMemory() {
        return new FileBasedChatMemory(chatMemoryFileDir);
    }

    /**
     * MySQL 存储方式的聊天记忆 Bean
     * 当 chat.memory.type 配置为 "mysql" 时生效（默认值）
     *
     * @param chatMemoryMapper MyBatis-Plus Mapper
     * @return MySQL 存储的聊天记忆实例
     */
    @Bean
    @ConditionalOnProperty(name = "chat.memory.type", havingValue = "mysql", matchIfMissing = true)
    public ChatMemory mySqlChatMemory(ChatMemoryMapper chatMemoryMapper) {
        return new MySqlChatMemory(chatMemoryMapper);
    }
}
