package com.qh.ai_agent.knowledge;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgDistanceType.COSINE_DISTANCE;
import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgIndexType.HNSW;

@Slf4j
@Configuration
@ConditionalOnProperty(name = "cs.vector.enabled", havingValue = "true")
public class CustomerServiceVectorStoreConfig {

    @Resource
    private KnowledgeBaseLoader knowledgeBaseLoader;

    @Bean("csVectorStore")
    public VectorStore csVectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel dashscopeEmbeddingModel) {
        String tableName = "cs_vector_store";

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
            log.warn("[CS-VectorStore] 创建表/索引失败（可能已存在）: {}", e.getMessage());
        }

        VectorStore vectorStore = PgVectorStore.builder(jdbcTemplate, dashscopeEmbeddingModel)
                .dimensions(1024)
                .distanceType(COSINE_DISTANCE)
                .indexType(HNSW)
                .initializeSchema(false)
                .schemaName("public")
                .vectorTableName(tableName)
                .maxDocumentBatchSize(10000)
                .build();

        List<Document> documents = knowledgeBaseLoader.loadAll();
        log.info("[CS-VectorStore] 开始向量化 {} 条文档...", documents.size());

        int batchSize = 10;
        for (int i = 0; i < documents.size(); i += batchSize) {
            int end = Math.min(i + batchSize, documents.size());
            List<Document> batch = documents.subList(i, end);
            vectorStore.add(batch);
        }

        log.info("[CS-VectorStore] 向量化完成，共写入 {} 条文档到 {}", documents.size(), tableName);
        return vectorStore;
    }
}
