package com.qh.ai_agent.intent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 意图识别结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntentResult {

    /** 识别出的意图 */
    private CustomerIntent intent;

    /** 置信度 0.0 ~ 1.0 */
    private double confidence;

    /** 提取到的槽位 key=slotName, value=slotValue */
    private Map<String, String> slots;

    /** LLM 原始响应（调试用） */
    private String rawResponse;

    /** 是否需要澄清 */
    private boolean needsClarification;

    /** 澄清问题（当 needsClarification=true 时） */
    private String clarificationQuestion;
}
