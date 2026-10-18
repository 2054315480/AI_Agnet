package com.qh.ai_agent.knowledge;

import com.qh.ai_agent.knowledge.retriever.RetrievalResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * pgvector 直查 DAO。
 *
 * <p>使用 JdbcTemplate + EmbeddingModel 直接查询 cs_vector_store，
 * 通过 pgvector 的余弦距离运算符 {@code <=>} 计算真实余弦相似度：
 * {@code similarity = 1 - (embedding <=> queryVector)}。</p>
 *
 * <p>该类不依赖 VectorStore.similaritySearch 返回的排名信息，避免用位置假分参与 RRF 融合。</p>
 */
@Slf4j
@Component
public class PgVectorSearchDao {

    /** 与 CustomerServiceVectorStoreConfig 中的建表定义保持一致。 */
    private static final String TABLE_NAME = "cs_vector_store";
    private static final int EXPECTED_DIMENSIONS = 1024;

    private final JdbcTemplate jdbcTemplate;

    /**
     * 只注入 DashScope 的嵌入模型；若当前环境未提供该 Bean，
     * isAvailable() 返回 false，由 HybridRetriever 降级到 VectorStore。
     */
    @Autowired(required = false)
    @Qualifier("dashscopeEmbeddingModel")
    private EmbeddingModel embeddingModel;

    public PgVectorSearchDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 判断直查路径是否具备执行条件。
     */
    public boolean isAvailable() {
        return jdbcTemplate != null && embeddingModel != null;
    }

    /**
     * 执行 pgvector 向量检索。
     *
     * @param query               改写后的查询文本
     * @param topK                最多返回条数
     * @param similarityThreshold 余弦相似度下限
     * @param docType             metadata.doc_type 过滤值，空表示不过滤
     * @return 带真实余弦相似度的检索结果
     */
    public List<RetrievalResult> search(String query, int topK,
                                        double similarityThreshold, String docType) {
        if (!isAvailable()) {
            throw new IllegalStateException("PgVectorSearchDao 不可用：JdbcTemplate 或 EmbeddingModel 缺失");
        }
        if (query == null || query.isBlank() || topK <= 0) {
            return List.of();
        }

        float[] embedding = embeddingModel.embed(query);
        if (embedding == null || embedding.length == 0) {
            throw new IllegalStateException("查询向量为空");
        }
        if (embedding.length != EXPECTED_DIMENSIONS) {
            throw new IllegalStateException("查询向量维度不匹配，期望 "
                    + EXPECTED_DIMENSIONS + "，实际 " + embedding.length);
        }

        String vectorLiteral = toPgVectorLiteral(embedding);
        boolean hasDocType = docType != null && !docType.isBlank();

        // 只在配置了 doc_type 时追加 JSONB 过滤条件，空值保持不过滤。
        StringBuilder sql = new StringBuilder(String.format("""
                SELECT content,
                       COALESCE(metadata->>'kb_id', '') AS kb_id,
                       COALESCE(metadata->>'question', '') AS question,
                       COALESCE(metadata->>'domain', '') AS domain,
                       COALESCE(metadata->>'category', '') AS category,
                       COALESCE(metadata->>'source', '') AS source_file,
                       1 - (embedding <=> ?::vector) AS similarity
                FROM %s
                WHERE 1 - (embedding <=> ?::vector) >= ?
                """, TABLE_NAME));
        if (hasDocType) {
            sql.append(" AND metadata->>'doc_type' = ?\n");
        }
        sql.append(" ORDER BY embedding <=> ?::vector\n LIMIT ?");

        List<Object> params = new ArrayList<>();
        params.add(vectorLiteral);
        params.add(similarityThreshold);
        if (hasDocType) {
            params.add(docType.trim());
        }
        params.add(vectorLiteral);
        params.add(topK);

        RowMapper<RetrievalResult> rowMapper = (rs, rowNum) -> RetrievalResult.builder()
                .kbId(emptyToNull(rs.getString("kb_id")))
                .question(rs.getString("question"))
                .answer(rs.getString("content"))
                .domain(rs.getString("domain"))
                .category(rs.getString("category"))
                .sourceFile(rs.getString("source_file"))
                .score(rs.getDouble("similarity"))
                .source(RetrievalResult.Source.VECTOR)
                .build();

        List<RetrievalResult> results = jdbcTemplate.query(
                sql.toString(), rowMapper, params.toArray());

        log.debug("[PgVectorSearchDao] pgvector 直查: topK={}, threshold={}, docType='{}', hits={}",
                topK, similarityThreshold, hasDocType ? docType.trim() : "", results.size());
        return results;
    }
    /**
     * 将 float[] 转为 PostgreSQL pgvector 文本字面量，例如 [0.1,0.2]。
     * 向量通过 PreparedStatement 以字符串绑定，再由 SQL 中的 ?::vector 显式转换。
     */
    private String toPgVectorLiteral(float[] vector) {
        StringBuilder builder = new StringBuilder(vector.length * 12);
        builder.append('[');
        for (int i = 0; i < vector.length; i++) {
            if (!Float.isFinite(vector[i])) {
                throw new IllegalStateException("查询向量包含非法数值: " + vector[i]);
            }
            if (i > 0) {
                builder.append(',');
            }
            builder.append(vector[i]);
        }
        builder.append(']');
        return builder.toString();
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}