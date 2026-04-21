package com.qh.ai_agent.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("conversations")
public class Conversation {
    @TableId(type = IdType.INPUT)
    private String id;

    @TableField("user_id")
    private Long userId;

    @TableField("title")
    private String title;

    @TableField("agent_type")
    private String agentType;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
