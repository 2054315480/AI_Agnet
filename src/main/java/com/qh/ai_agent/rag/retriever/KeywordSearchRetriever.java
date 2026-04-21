package com.qh.ai_agent.rag.retriever;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;

@Slf4j
public class KeywordSearchRetriever implements DocumentRetriever {

    private final JdbcTemplate jdbcTemplate;
    private final String tableName;
    private final int topK;

    public KeywordSearchRetriever(JdbcTemplate jdbcTemplate, String tableName, int topK) {
        this.jdbcTemplate = jdbcTemplate;
        this.tableName = tableName;
        this.topK = topK;
    }

    @Override
    public List<Document> retrieve(Query query) {
        String searchText = query.text();
        if (searchText == null || searchText.trim().isEmpty()) {
            return List.of();
        }

        // 使用 PostgreSQL LIKE 做关键词搜索（全文搜索的降级方案）
        String[] keywords = searchText.trim().split("\\s+");
        StringBuilder whereClause = new StringBuilder();
        List<Object> params = new ArrayList<>();

        for (int i = 0; i < keywords.length; i++) {
            if (i > 0) whereClause.append(" OR ");
            whereClause.append("content ILIKE ?");
            params.add("%" + keywords[i] + "%");
        }

        String sql = String.format(
                "SELECT id, content, metadata FROM %s WHERE %s LIMIT ?",
                tableName, whereClause
        );
        params.add(topK);

        try {
            List<Document> results = jdbcTemplate.query(sql, (rs, rowNum) -> {
                String id = rs.getString("id");
                String content = rs.getString("content");
                String metadataJson = rs.getString("metadata");
                Map<String, Object> metadata = parseMetadata(metadataJson);
                metadata.put("retriever_source", "KEYWORD");
                return new Document(id, content, metadata);
            }, params.toArray());

            log.info("关键词检索 '{}' 返回 {} 条结果", searchText, results.size());
            return results;
        } catch (Exception e) {
            log.warn("关键词检索失败: {}", e.getMessage());
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseMetadata(String json) {
        if (json == null || json.isEmpty()) {
            return new HashMap<>();
        }
        try {
            // 使用 Spring Boot 自带的 Jackson
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(json, Map.class);
        } catch (Exception e) {
            return new HashMap<>();
        }
    }
}
