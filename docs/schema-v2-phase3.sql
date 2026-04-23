-- =====================================================
-- Phase 3: 数据持久化与分析 — 数据库迁移脚本
-- 在已有 Love_Agent 数据库上执行
-- =====================================================

-- 1. 扩展 conversation_messages 表，新增客服分析字段
ALTER TABLE conversation_messages ADD COLUMN IF NOT EXISTS intent VARCHAR(50);
ALTER TABLE conversation_messages ADD COLUMN IF NOT EXISTS slots JSONB;
ALTER TABLE conversation_messages ADD COLUMN IF NOT EXISTS confidence FLOAT;
ALTER TABLE conversation_messages ADD COLUMN IF NOT EXISTS is_handoff BOOLEAN DEFAULT FALSE;
ALTER TABLE conversation_messages ADD COLUMN IF NOT EXISTS is_clarification BOOLEAN DEFAULT FALSE;
ALTER TABLE conversation_messages ADD COLUMN IF NOT EXISTS sources JSONB;

-- 索引：按意图类型查询
CREATE INDEX IF NOT EXISTS idx_messages_intent ON conversation_messages(intent);

-- 2. 新建会话分析聚合表
CREATE TABLE IF NOT EXISTS dialog_analytics (
    id                  BIGSERIAL PRIMARY KEY,
    conversation_id     VARCHAR(64) REFERENCES conversations(id) ON DELETE CASCADE,
    total_turns         INT DEFAULT 0,
    intent_distribution JSONB,
    handoff_triggered   BOOLEAN DEFAULT FALSE,
    clarification_count INT DEFAULT 0,
    avg_confidence      FLOAT,
    resolved            BOOLEAN,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_analytics_conversation ON dialog_analytics(conversation_id);
