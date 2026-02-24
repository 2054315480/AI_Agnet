package com.qh.ai_agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.ai.chat.messages.MessageType;

import java.time.LocalDateTime;

/**
 * 聊天记忆实体类
 * 使用 MyBatis-Plus 注解映射数据库表
 */
@TableName("chat_memory")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMemoryEntity {

    /**
     * 主键 ID，自增
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 会话 ID
     */
    @TableField("conversation_id")
    private String conversationId;

    /**
     * 角色（USER/ASSISTANT/SYSTEM）
     */
    @TableField("role")
    private String role;

    /**
     * 消息内容（使用 LONGTEXT 类型存储长文本）
     */
    @TableField("content")
    private String content;

    /**
     * 消息类型枚举
     */
    @TableField("message_type")
    private MessageType messageType;

    /**
     * 创建时间
     */
    @TableField("created_at")
    private LocalDateTime createdAt;
}
