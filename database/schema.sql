-- ============================================================
-- AI智能客服系统 - 数据库建表脚本 (PostgreSQL + pgvector)
-- 生成时间: 2026-04-27
-- 兼容版本: PostgreSQL 14+
-- ============================================================

-- 首先启用 pgvector 扩展（需要提前安装）
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ============================================================
-- 1. 用户表
-- ============================================================
CREATE TABLE IF NOT EXISTS users (
    id              BIGSERIAL       PRIMARY KEY,
    username        VARCHAR(50)     NOT NULL UNIQUE,
    password_hash   VARCHAR(255)    NOT NULL,
    nickname        VARCHAR(100),
    avatar          VARCHAR(500),
    role            VARCHAR(20)     NOT NULL DEFAULT 'user',
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
-- 2. 会话表
-- ============================================================
CREATE TABLE IF NOT EXISTS conversations (
    id              VARCHAR(64)     PRIMARY KEY,
    user_id         BIGINT          REFERENCES users(id),
    title           VARCHAR(200),
    agent_type      VARCHAR(20)     NOT NULL DEFAULT 'love',
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_conversations_user_id ON conversations(user_id);
CREATE INDEX IF NOT EXISTS idx_conversations_created_at ON conversations(created_at DESC);

-- ============================================================
-- 3. 对话消息表（含客服分析字段）
-- ============================================================
CREATE TABLE IF NOT EXISTS conversation_messages (
    id              BIGSERIAL       PRIMARY KEY,
    conversation_id VARCHAR(64)     NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    role            VARCHAR(20)     NOT NULL,
    content         TEXT,
    image_url       VARCHAR(500),
    -- 客服分析字段
    intent          VARCHAR(50),
    slots           JSONB,
    confidence      DOUBLE PRECISION,
    is_handoff      BOOLEAN         NOT NULL DEFAULT FALSE,
    is_clarification BOOLEAN        NOT NULL DEFAULT FALSE,
    sources         JSONB,
    -- 时间戳
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_conversation_messages_conv_id ON conversation_messages(conversation_id);
CREATE INDEX IF NOT EXISTS idx_messages_intent ON conversation_messages(intent);

-- ============================================================
-- 4. 对话分析聚合表
-- ============================================================
CREATE TABLE IF NOT EXISTS dialog_analytics (
    id                  BIGSERIAL       PRIMARY KEY,
    conversation_id     VARCHAR(64)     REFERENCES conversations(id) ON DELETE CASCADE,
    total_turns         INTEGER         NOT NULL DEFAULT 0,
    intent_distribution JSONB,
    handoff_triggered   BOOLEAN         NOT NULL DEFAULT FALSE,
    clarification_count INTEGER         NOT NULL DEFAULT 0,
    avg_confidence      DOUBLE PRECISION,
    resolved            BOOLEAN,
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_dialog_analytics_conversation ON dialog_analytics(conversation_id);
CREATE INDEX IF NOT EXISTS idx_dialog_analytics_handoff ON dialog_analytics(handoff_triggered) WHERE handoff_triggered = TRUE;
CREATE INDEX IF NOT EXISTS idx_dialog_analytics_clarification ON dialog_analytics(clarification_count) WHERE clarification_count > 0;

-- ============================================================
-- 5. 聊天记忆表（对话历史持久化，可选）
-- ============================================================
CREATE TABLE IF NOT EXISTS chat_memory (
    id              BIGSERIAL       PRIMARY KEY,
    conversation_id VARCHAR(64)     NOT NULL,
    role            VARCHAR(20)     NOT NULL,
    content         TEXT,
    message_type    VARCHAR(20),
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_chat_memory_conv_id ON chat_memory(conversation_id);

-- ============================================================
-- 6. 通用知识库向量存储表（RAG检索）
-- ============================================================
CREATE TABLE IF NOT EXISTS vector_store (
    id          UUID            PRIMARY KEY DEFAULT uuid_generate_v4(),
    content     TEXT,
    metadata    JSONB,
    embedding   VECTOR(1024)
);

CREATE INDEX IF NOT EXISTS vector_store_embedding_idx ON vector_store
    USING hnsw (embedding vector_cosine_ops);

-- ============================================================
-- 7. 客服知识库向量存储表（客服专用RAG检索）
-- ============================================================
CREATE TABLE IF NOT EXISTS cs_vector_store (
    id          UUID            PRIMARY KEY DEFAULT uuid_generate_v4(),
    content     TEXT,
    metadata    JSONB,
    embedding   VECTOR(1024)
);

CREATE INDEX IF NOT EXISTS cs_vector_store_embedding_idx ON cs_vector_store
    USING hnsw (embedding vector_cosine_ops);

-- ============================================================
-- 完成
-- ============================================================
