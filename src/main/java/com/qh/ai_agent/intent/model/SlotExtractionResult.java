package com.qh.ai_agent.intent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 槽位提取结果（轻量级，不做完整意图分类）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlotExtractionResult {

    /** 提取到的槽位 key=slotName, value=slotValue */
    @Builder.Default
    private Map<String, String> extracted = new HashMap<>();

    /** 用户是否切换了话题 */
    @Builder.Default
    private boolean topicChange = false;

    /** LLM 原始响应（调试用） */
    private String rawResponse;

    /** 空结果 */
    public static SlotExtractionResult empty() {
        return SlotExtractionResult.builder()
                .extracted(new HashMap<>())
                .topicChange(false)
                .build();
    }

    /** 是否提取到有效槽位 */
    public boolean hasExtracted() {
        return extracted != null && !extracted.isEmpty();
    }
}
