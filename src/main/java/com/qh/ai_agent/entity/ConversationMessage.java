package com.qh.ai_agent.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@TableName(value = "conversation_messages", autoResultMap = true)
public class ConversationMessage {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("conversation_id")
    private String conversationId;

    @TableField("role")
    private String role;

    @TableField("content")
    private String content;

    @TableField("image_url")
    private String imageUrl;

    /** 识别到的意图（如 ORDER_QUERY, PRODUCT_INFO 等） */
    @TableField("intent")
    private String intent;

    /** 提取的槽位 JSON（如 {"order_id":"ABC123"}） */
    @TableField(value = "slots", typeHandler = JacksonTypeHandler.class)
    private Map<String, String> slots;

    /** 意图识别置信度 0.0~1.0 */
    @TableField("confidence")
    private Double confidence;

    /** 是否触发转人工 */
    @TableField("is_handoff")
    private Boolean isHandoff;

    /** 是否触发澄清追问 */
    @TableField("is_clarification")
    private Boolean isClarification;

    /** 知识库来源出处 JSON */
    @TableField(value = "sources", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> sources;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
