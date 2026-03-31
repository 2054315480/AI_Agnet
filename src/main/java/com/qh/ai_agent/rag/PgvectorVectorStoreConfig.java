package com.qh.ai_agent.rag;

import jakarta.annotation.Resource;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgDistanceType.COSINE_DISTANCE;
import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgIndexType.HNSW;

@Configuration
public class PgvectorVectorStoreConfig {

    @Resource
    private LoveAppDocumentLoder loveAppDocumentLoder;

    @Bean
    public VectorStore pgvectorVectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel dashscopeEmbeddingModel) {

        String tableName = "vector_store";

        // 手动创建表（确保表在添加数据前存在）
        String createTableSql = String.format("""
            CREATE TABLE IF NOT EXISTS %s (
                id UUID PRIMARY KEY,
                content TEXT,
                metadata JSONB,
                embedding vector(1024)
            );
            """, tableName);

        String createIndexSql = String.format("""
            CREATE INDEX IF NOT EXISTS %s_embedding_idx ON %s
            USING hnsw (embedding vector_cosine_ops);
            """, tableName, tableName);

        try {
            jdbcTemplate.execute(createTableSql);
            jdbcTemplate.execute(createIndexSql);
        } catch (Exception e) {
            System.err.println("创建表或索引失败: " + e.getMessage());
            // 如果表已存在可能没问题，继续执行
        }

        VectorStore vectorStore = PgVectorStore.builder(jdbcTemplate, dashscopeEmbeddingModel)
                .dimensions(1024)                    // 与当前 DashScope Embedding 模型输出维度一致
                .distanceType(COSINE_DISTANCE)       // Optional: defaults to COSINE_DISTANCE
                .indexType(HNSW)                     // Optional: defaults to HNSW
                .initializeSchema(false)             // 已手动创建表，设为 false
                .schemaName("public")                // Optional: defaults to "public"
                .vectorTableName(tableName)          // 使用新的表名，避免已存在表的维度冲突
                .maxDocumentBatchSize(10000)         // Optional: defaults to 10000
                .build();
        List< Document> documents =loveAppDocumentLoder.loadMarkdown();

        // 分批添加，每批最多 10 条（阿里云 DashScope Embedding API 限制）
        int batchSize = 10;
        for (int i = 0; i < documents.size(); i += batchSize) {
            int end = Math.min(i + batchSize, documents.size());
            List<Document> batch = documents.subList(i, end);
            vectorStore.add(batch);
        }

        return vectorStore;
    }
}
