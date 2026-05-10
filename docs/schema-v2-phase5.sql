-- Phase 5: 对话分析分页支持 — 索引 + 历史数据回填

-- 时间排序索引
CREATE INDEX IF NOT EXISTS idx_conversations_created_at ON conversations(created_at DESC);

-- 转人工筛选部分索引（PostgreSQL 特有，只索引 true 的行）
CREATE INDEX IF NOT EXISTS idx_dialog_analytics_handoff ON dialog_analytics(handoff_triggered) WHERE handoff_triggered = true;

-- 澄清筛选部分索引
CREATE INDEX IF NOT EXISTS idx_dialog_analytics_clarification ON dialog_analytics(clarification_count) WHERE clarification_count > 0;

-- 回填历史数据：为没有 analytics 记录的 conversations 补充 dialog_analytics 行
INSERT INTO dialog_analytics (conversation_id, total_turns, handoff_triggered, clarification_count, avg_confidence, resolved, created_at, updated_at)
SELECT
    c.id,
    COALESCE((SELECT COUNT(*) FROM conversation_messages m WHERE m.conversation_id = c.id AND m.role = 'user'), 0),
    EXISTS (SELECT 1 FROM conversation_messages m WHERE m.conversation_id = c.id AND m.is_handoff = true),
    COALESCE((SELECT COUNT(*) FROM conversation_messages m WHERE m.conversation_id = c.id AND m.is_clarification = true), 0),
    (SELECT AVG(m.confidence) FROM conversation_messages m WHERE m.conversation_id = c.id AND m.confidence IS NOT NULL),
    NOT EXISTS (SELECT 1 FROM conversation_messages m WHERE m.conversation_id = c.id AND m.is_handoff = true),
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM conversations c
WHERE NOT EXISTS (SELECT 1 FROM dialog_analytics da WHERE da.conversation_id = c.id);
