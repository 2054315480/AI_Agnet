package com.qh.ai_agent.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("dialog_analytics")
public class DialogAnalytics {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("conversation_id")
    private String conversationId;

    @TableField("total_turns")
    private Integer totalTurns;

    /** 意图分布 JSON，如 {"ORDER_QUERY":3,"PRODUCT_INFO":2} */
    @TableField("intent_distribution")
    private String intentDistribution;

    @TableField("handoff_triggered")
    private Boolean handoffTriggered;

    @TableField("clarification_count")
    private Integer clarificationCount;

    @TableField("avg_confidence")
    private Double avgConfidence;

    @TableField("resolved")
    private Boolean resolved;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
