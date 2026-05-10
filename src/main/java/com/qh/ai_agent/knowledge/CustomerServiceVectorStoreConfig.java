package com.qh.ai_agent.knowledge;

import com.qh.ai_agent.knowledge.parser.KbEntry;
import com.qh.ai_agent.knowledge.parser.UnifiedKbLoader;
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

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgDistanceType.COSINE_DISTANCE;
import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgIndexType.HNSW;

@Slf4j
@Configuration
@ConditionalOnProperty(name = "cs.vector.enabled", havingValue = "true")
public class CustomerServiceVectorStoreConfig {

    @Resource
    private UnifiedKbLoader unifiedKbLoader;

    /** 并发嵌入线程数 */
    private static final int EMBEDDING_PARALLELISM = 4;

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

        // 检查是否已有向量数据（持久化：重启不重复嵌入）
        Integer existingCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + tableName, Integer.class);
        if (existingCount != null && existingCount > 0) {
            log.info("[CS-VectorStore] 向量库已有 {} 条数据，跳过重新嵌入（如需重建请先清空表）", existingCount);
            return vectorStore;
        }

        // 仅加载比赛知识库文档（data/data/knowledge_base/）
        List<KbEntry> competitionEntries = unifiedKbLoader.loadAll();
        List<Document> documents = convertToDocuments(competitionEntries);
        log.info("[CS-VectorStore] 比赛知识库: {} 条文档", documents.size());

        if (documents.isEmpty()) {
            log.warn("[CS-VectorStore] 未加载到任何比赛知识库文档，启动后检索将返回空结果");
            return vectorStore;
        }

        log.info("[CS-VectorStore] 开始并行向量化 {} 条文档（并发数={}）...", documents.size(), EMBEDDING_PARALLELISM);

        // 并行嵌入：将文档分成 N 组，每组用独立线程调用 embedding API
        ExecutorService executor = Executors.newFixedThreadPool(EMBEDDING_PARALLELISM);
        int groupSize = (int) Math.ceil((double) documents.size() / EMBEDDING_PARALLELISM);
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int g = 0; g < EMBEDDING_PARALLELISM; g++) {
            int start = g * groupSize;
            int end = Math.min(start + groupSize, documents.size());
            if (start >= end) break;
            List<Document> group = documents.subList(start, end);
            futures.add(CompletableFuture.runAsync(() -> {
                int batchSize = 10;
                for (int i = 0; i < group.size(); i += batchSize) {
                    int batchEnd = Math.min(i + batchSize, group.size());
                    List<Document> batch = group.subList(i, batchEnd);
                    vectorStore.add(batch);
                }
            }, executor));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        executor.shutdown();
        log.info("[CS-VectorStore] 并行向量化完成，共写入 {} 条文档到 {}", documents.size(), tableName);
        return vectorStore;
    }

    /**
     * 将比赛 KbEntry 列表转换为 Spring AI Document 列表。
     * metadata 中包含 kb_id、question、domain、category、source 等字段，供检索时溯源。
     */
    private List<Document> convertToDocuments(List<KbEntry> entries) {
        List<Document> docs = new ArrayList<>();
        for (KbEntry entry : entries) {
            Map<String, Object> metadata = new HashMap<>();
            // 安全写入，避免 null 值导致 Spring AI PgVector 报错
            putIfNotNull(metadata, "kb_id", entry.getKbId());
            putIfNotNull(metadata, "question", entry.getQuestion());
            putIfNotNull(metadata, "domain", entry.getDomain());
            putIfNotNull(metadata, "category", entry.getCategory());
            putIfNotNull(metadata, "source", entry.getSourceFile());
            metadata.put("doc_type", "COMPETITION_KB");
            metadata.put("chunk_index", entry.getChunkIndex());

            // 向量化用 Q+A 拼接文本
            String q = entry.getQuestion() != null ? entry.getQuestion() : "";
            String a = entry.getAnswer() != null ? entry.getAnswer() : "";
            String content = "Q: " + q + "\nA: " + a;
            Document doc = new Document(content, metadata);
            docs.add(doc);
        }
        return docs;
    }

    private void putIfNotNull(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }
}
