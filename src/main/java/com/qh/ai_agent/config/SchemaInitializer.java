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
            // 9 跳链路展示需要的两列：检索分阶段明细 + 落库行 id
            "ALTER TABLE agent_trace ADD COLUMN IF NOT EXISTS retrieval_detail JSONB",
            "ALTER TABLE agent_trace ADD COLUMN IF NOT EXISTS db_ref JSONB",
            // 实验实测结果落库：优化成效页读"最近一次真实实验结果"，而不是写死数字
            "CREATE TABLE IF NOT EXISTS experiment_run ("
                    + "id BIGSERIAL PRIMARY KEY, type VARCHAR(64) NOT NULL, "
                    + "payload JSONB NOT NULL, created_at TIMESTAMPTZ DEFAULT now())",
            "CREATE INDEX IF NOT EXISTS idx_experiment_run_type ON experiment_run (type, created_at DESC)",
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
            "CREATE INDEX IF NOT EXISTS idx_kg_relation_tgt ON kg_relation (target_id)",
            // ---- 评测历史（批次 A：EvalService 会写入 eval_run）----
            "CREATE TABLE IF NOT EXISTS eval_run ("
                    + "id BIGSERIAL PRIMARY KEY,"
                    + "run_name VARCHAR(128),"
                    + "sample_count INT,"
                    + "recall_at_5 DOUBLE PRECISION,"
                    + "mrr DOUBLE PRECISION,"
                    + "ndcg_at_5 DOUBLE PRECISION,"
                    + "config_snapshot JSONB,"
                    + "created_at TIMESTAMPTZ DEFAULT now())"
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
        ensureVectorUniqueIndex();
    }

    /**
     * 向量写入幂等的唯一索引（批次 F/H）。
     *
     * 只有在这个索引缺失时才会动手：先探测是否有重复 kb_id，有就先按 ctid 保留一条再建索引，
     * 避免在已有脏数据的库上直接 CREATE UNIQUE INDEX 失败。
     */
    private void ensureVectorUniqueIndex() {
        try {
            Integer exists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM pg_indexes WHERE indexname = 'uq_cs_vector_kb_id'", Integer.class);
            if (exists != null && exists > 0) {
                return;
            }
            if (tableExists("cs_vector_store")) {
                Integer dupGroups = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM (SELECT metadata->>'kb_id' AS k FROM cs_vector_store "
                                + "WHERE metadata->>'kb_id' IS NOT NULL GROUP BY 1 HAVING COUNT(*) > 1) t",
                        Integer.class);
                if (dupGroups != null && dupGroups > 0) {
                    int removed = jdbcTemplate.update(
                            "DELETE FROM cs_vector_store a USING cs_vector_store b "
                                    + "WHERE a.ctid < b.ctid AND a.metadata->>'kb_id' = b.metadata->>'kb_id' "
                                    + "AND a.metadata->>'kb_id' IS NOT NULL");
                    log.warn("[SchemaInitializer] 向量表存在 {} 组重复 kb_id，已清理 {} 行后再建唯一索引", dupGroups, removed);
                }
                jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS uq_cs_vector_kb_id "
                        + "ON cs_vector_store ((metadata->>'kb_id')) WHERE metadata->>'kb_id' IS NOT NULL");
                log.info("[SchemaInitializer] 向量唯一索引 uq_cs_vector_kb_id 已就绪");
            }
        } catch (Exception e) {
            log.warn("[SchemaInitializer] 建立向量唯一索引失败（不影响启动）: {}", e.getMessage());
        }
    }

    private boolean tableExists(String table) {
        try {
            String r = jdbcTemplate.queryForObject("SELECT to_regclass(?)::text", String.class, table);
            return r != null && !r.isBlank();
        } catch (Exception e) {
            return false;
        }
    }
}
