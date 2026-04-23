package com.qh.ai_agent.intent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 槽位定义
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlotDefinition {

    public enum SlotType {
        STRING, NUMBER, DATE, ENUM
    }

    /** 槽位名称 */
    private String slotName;

    /** 槽位类型 */
    private SlotType slotType;

    /** 槽位描述（用于 Prompt 中提示 LLM） */
    private String description;

    /** 是否必填 */
    private boolean required;

    /** 枚举值列表（仅 slotType=ENUM 时有效） */
    private List<String> enumValues;

    /** 正则表达式（用于预筛提取，可选） */
    private String regex;
}
