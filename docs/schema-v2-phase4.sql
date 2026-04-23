-- Phase 4: 管理员角色 + FAQ 管理
-- 添加用户角色字段
ALTER TABLE users ADD COLUMN IF NOT EXISTS role VARCHAR(20) DEFAULT 'user';

-- 创建客服向量库表（由 CustomerServiceVectorStoreConfig 自动创建，此处作为备份）
CREATE TABLE IF NOT EXISTS cs_vector_store (
    id UUID PRIMARY KEY,
    content TEXT,
    metadata JSONB,
    embedding vector(1024)
);

CREATE INDEX IF NOT EXISTS cs_vector_store_embedding_idx ON cs_vector_store
USING hnsw (embedding vector_cosine_ops);
