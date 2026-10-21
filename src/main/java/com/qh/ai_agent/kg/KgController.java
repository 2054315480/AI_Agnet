package com.qh.ai_agent.kg;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识图谱管理接口。
 */
@RestController
@RequestMapping("/admin/kg")
@RequiredArgsConstructor
public class KgController {

    private final KgBuildService buildService;
    private final KgQueryService queryService;
    private final JdbcTemplate jdbcTemplate;

    /** 构建图谱（可指定并发线程数） */
    @PostMapping("/build")
    public ResponseEntity<?> build(@RequestBody(required = false) Map<String, Object> body) {
        int threads = Integer.parseInt(String.valueOf((body == null ? Map.of() : body).getOrDefault("threads", 8)));
        return ResponseEntity.ok(buildService.buildAll(threads));
    }

    /** 图谱统计 */
    @GetMapping("/stats")
    public ResponseEntity<?> stats() {
        return ResponseEntity.ok(buildService.stats());
    }

    /**
     * 图谱可视化数据：返回 ECharts graph 所需的 nodes + links。
     *
     * 两种模式：
     * - 传 query：只展示 query 命中的实体及其一跳邻居（聚焦视图）
     * - 不传 query：展示 doc_freq 最高的实体及其关系（全局概览）
     */
    @PostMapping("/graph")
    public ResponseEntity<?> graph(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> req = body == null ? Map.of() : body;
        String query = String.valueOf(req.getOrDefault("query", ""));
        int limit = Integer.parseInt(String.valueOf(req.getOrDefault("limit", 30)));

        List<Map<String, Object>> nodes = new java.util.ArrayList<>();
        List<Map<String, Object>> links = new java.util.ArrayList<>();
        List<Map<String, Object>> matched = new java.util.ArrayList<>();

        try {
            java.util.Set<Long> entityIds = new java.util.LinkedHashSet<>();
            if (!query.isBlank()) {
                var entities = queryService.linkEntities(query);
                matched.addAll(entities);
                for (Map<String, Object> e : entities) {
                    entityIds.add(((Number) e.get("id")).longValue());
                }
            }

            String sql;
            List<Map<String, Object>> rows;
            if (!entityIds.isEmpty()) {
                // 命中实体本身
                rows = jdbcTemplate.queryForList(
                        "SELECT id, name, type, doc_freq FROM kg_entity WHERE id = ANY (?) LIMIT ?",
                        entityIds.toArray(new Long[0]), limit);
                // 加一跳邻居
                List<Map<String, Object>> neighbors = jdbcTemplate.queryForList(
                        "SELECT DISTINCT e.id, e.name, e.type, e.doc_freq FROM kg_relation r " +
                        "JOIN kg_entity e ON e.id = r.target_id WHERE r.source_id = ANY (?) " +
                        "UNION SELECT DISTINCT e.id, e.name, e.type, e.doc_freq FROM kg_relation r " +
                        "JOIN kg_entity e ON e.id = r.source_id WHERE r.target_id = ANY (?) LIMIT ?",
                        entityIds.toArray(new Long[0]), entityIds.toArray(new Long[0]), limit);
                List<Map<String, Object>> merged = new java.util.ArrayList<>(rows);
                merged.addAll(neighbors);
                rows = merged;
            } else {
                rows = jdbcTemplate.queryForList(
                        "SELECT id, name, type, doc_freq FROM kg_entity ORDER BY doc_freq DESC LIMIT ?", limit);
            }

            java.util.Set<Long> nodeIds = new java.util.HashSet<>();
            for (Map<String, Object> r : rows) {
                Long id = ((Number) r.get("id")).longValue();
                if (!nodeIds.add(id)) {
                    continue;
                }
                Map<String, Object> n = new java.util.LinkedHashMap<>();
                n.put("id", String.valueOf(id));
                n.put("name", r.get("name"));
                n.put("type", r.get("type"));
                n.put("docFreq", r.get("doc_freq"));
                n.put("matched", entityIds.contains(id));
                nodes.add(n);
            }

            if (!nodeIds.isEmpty()) {
                List<Map<String, Object>> rels = jdbcTemplate.queryForList(
                        "SELECT r.source_id, r.target_id, r.relation FROM kg_relation r LIMIT 2000");
                java.util.Set<String> seen = new java.util.HashSet<>();
                for (Map<String, Object> rel : rels) {
                    long src = ((Number) rel.get("source_id")).longValue();
                    long tgt = ((Number) rel.get("target_id")).longValue();
                    if (!nodeIds.contains(src) || !nodeIds.contains(tgt)) {
                        continue;
                    }
                    String key = src + "-" + tgt + "-" + rel.get("relation");
                    if (!seen.add(key)) {
                        continue;
                    }
                    Map<String, Object> l = new java.util.LinkedHashMap<>();
                    l.put("source", String.valueOf(src));
                    l.put("target", String.valueOf(tgt));
                    l.put("relation", rel.get("relation"));
                    links.add(l);
                }
            }
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }

        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("query", query);
        out.put("nodes", nodes);
        out.put("links", links);
        out.put("matched", matched);
        out.put("nodeCount", nodes.size());
        out.put("linkCount", links.size());
        return ResponseEntity.ok(out);
    }

    /** 实体链接调试：输入 query 看能识别出哪些实体、关联哪些 kb */
    @PostMapping("/link")
    public ResponseEntity<?> link(@RequestBody Map<String, Object> body) {
        String query = String.valueOf(body.getOrDefault("query", ""));
        var entities = queryService.linkEntities(query);
        var kbScores = queryService.relatedKbIds(entities);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("query", query);
        out.put("enabled", queryService.isEnabled());
        out.put("entities", entities);
        out.put("relatedKbCount", kbScores.size());
        out.put("relatedKbTop", kbScores.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(10)
                .map(e -> Map.of("kbId", e.getKey(), "weight", e.getValue()))
                .toList());
        return ResponseEntity.ok(out);
    }
}
