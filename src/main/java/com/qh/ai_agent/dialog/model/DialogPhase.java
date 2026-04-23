package com.qh.ai_agent.dialog.model;

/**
 * 对话阶段枚举
 */
public enum DialogPhase {
    /** 寒暄 */
    GREETING,
    /** 意图收集 */
    INTENT_COLLECT,
    /** 槽位填充 */
    SLOT_FILLING,
    /** 知识检索中 */
    RETRIEVING,
    /** 回答中 */
    ANSWERING,
    /** 澄清中 */
    CLARIFYING,
    /** 转人工 */
    HANDOFF,
    /** 已结束 */
    CLOSED
}
