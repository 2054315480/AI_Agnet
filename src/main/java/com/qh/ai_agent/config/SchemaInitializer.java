package com.qh.ai_agent.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 启动时补建观测与知识图谱相关的表。
 *
 * 这些表由 trace / kg 模块直接读写，但仓库里没有对应的 Flyway 迁移脚本，
 * 全新数据库上会直接报 "relation does not exist"（表现为能力矩阵 500、trace 面板空白）。
 * 这里统一用 CREATE TABLE IF NOT EXISTS 兜底，已存在时不做任何改动。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SchemaInitializer {

    private final JdbcTemplate jdbcTemplate;

    private static final String[] DDL = {
            // ---- 链路追踪 ----
            "CREATE TABLE IF NOT EXISTS agent_trace ("
                    + "id BIGSERIAL PRIMARY KEY,"
                    + "trace_id VARCHAR(64) NOT NULL UNIQUE,"
                    + "session_id VARCHAR(64),"
                    + "user_query TEXT,"
                    + "rewritten TEXT,"
                    + "intent VARCHAR(64),"
                    + "status VARCHAR(32),"
                    + "total_ms INT,"
                    + "total_tokens INT,"
                    + "degraded_reason TEXT,"
                    + "created_at TIMESTAMPTZ DEFAULT now())",
            "CREATE INDEX IF NOT EXISTS idx_agent_trace_created ON agent_trace (created_at DESC)",
            "CREATE TABLE IF NOT EXISTS trace_span ("
                    + "id BIGSERIAL PRIMARY KEY,"
                    + "trace_id VARCHAR(64) NOT NULL,"
                    + "span_name VARCHAR(128),"
                    + "start_ms INT,"
                    + "duration_ms INT,"
                    + "status VARCHAR(32),"
                    + "attributes JSONB,"
                    + "created_at TIMESTAMPTZ DEFAULT now())",
            "CREATE INDEX IF NOT EXISTS idx_trace_span_trace ON trace_span (trace_id, start_ms)",
            // ---- 知识图谱 ----
            "CREATE TABLE IF NOT EXISTS kg_entity ("
                    + "id BIGSERIAL PRIMARY KEY,"
                    + "name VARCHAR(255) NOT NULL,"
                    + "normalized VARCHAR(255) NOT NULL,"
                    + "type VARCHAR(64) DEFAULT 'unknown',"
                    + "doc_freq INT DEFAULT 0,"
                    + "created_at TIMESTAMPTZ DEFAULT now(),"
                    + "UNIQUE (normalized, type))",
            "CREATE TABLE IF NOT EXISTS kg_mention ("
                    + "id BIGSERIAL PRIMARY KEY,"
                    + "entity_id BIGINT REFERENCES kg_entity(id) ON DELETE CASCADE,"
                    + "kb_id VARCHAR(128),"
                    + "field VARCHAR(32))",
            "CREATE INDEX IF NOT EXISTS idx_kg_mention_entity ON kg_mention (entity_id)",
            "CREATE INDEX IF NOT EXISTS idx_kg_mention_kb ON kg_mention (kb_id)",
            "CREATE TABLE IF NOT EXISTS kg_relation ("
                    + "id BIGSERIAL PRIMARY KEY,"
                    + "source_id BIGINT REFERENCES kg_entity(id) ON DELETE CASCADE,"
                    + "target_id BIGINT REFERENCES kg_entity(id) ON DELETE CASCADE,"
                    + "relation VARCHAR(64),"
                    + "evidence_kb_id VARCHAR(128))",
            "CREATE INDEX IF NOT EXISTS idx_kg_relation_src ON kg_relation (source_id)",
            "CREATE INDEX IF NOT EXISTS idx_kg_relation_tgt ON kg_relation (target_id)"
    };

    @PostConstruct
    public void init() {
        int ok = 0;
        for (String sql : DDL) {
            try {
                jdbcTemplate.execute(sql);
                ok++;
            } catch (Exception e) {
                log.warn("[SchemaInitializer] 建表语句执行失败（已跳过）: {} -> {}", sql.substring(0, Math.min(60, sql.length())), e.getMessage());
            }
        }
        log.info("[SchemaInitializer] 观测/图谱表检查完成: {}/{} 条语句执行成功", ok, DDL.length);
    }
}
