package com.qh.ai_agent.kg;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 图谱查询：把用户 query 里的实体识别出来，再扩展出相关 kb_id，用于检索加权。
 *
 * 价值场景：
 *   "我耳机坏了想退，退款多久到账"
 *   -> 实体：耳机(PRODUCT)、坏了(ISSUE)、退货(ACTION)、退款(ACTION)
 *   -> 一跳扩散：坏了 -> 退货(resolved_by)、退货 -> 退款(requires)
 *   -> 拿到这些实体关联的 kb_id，即使向量检索没直接命中，也能被图谱捞回来
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KgQueryService {

    private final JdbcTemplate jdbcTemplate;

    @Value("${cs.kg.enabled:false}")
    private boolean enabled;

    /** 实体加权系数：finalScore = rrfScore + boost * entityHit */
    @Value("${cs.kg.boost:0.05}")
    private double boost;

    /** 一跳扩散权重衰减 */
    @Value("${cs.kg.hop-decay:0.5}")
    private double hopDecay;

    /** 超过这个 doc_freq 的实体视为"过宽实体"，不参与链接（如单字/通用词） */
    @Value("${cs.kg.max-entity-doc-freq:40}")
    private int maxEntityDocFreq;

    /** 单次查询最多参与打分的实体数，避免长 query 命中一堆实体后噪声叠加 */
    @Value("${cs.kg.max-entities:8}")
    private int maxEntities;

    public boolean isEnabled() {
        return enabled;
    }

    public double getBoost() {
        return boost;
    }

    /**
     * 实体链接：从 query 中识别已存在的实体。
     * 用最长匹配 + 归一化包含判断，毫秒级，不需要模型。
     */
    public List<Map<String, Object>> linkEntities(String query) {
        if (!enabled || query == null || query.isBlank()) {
            return List.of();
        }
        String norm = KgExtractor.normalize(query);
        try {
            List<Map<String, Object>> all = jdbcTemplate.queryForList(
                    "SELECT id, name, normalized, type, doc_freq FROM kg_entity ORDER BY length(normalized) DESC");
            List<Map<String, Object>> hit = new ArrayList<>();
            Set<Long> used = new HashSet<>();
            for (Map<String, Object> e : all) {
                String n = String.valueOf(e.get("normalized"));
                if (n.isEmpty() || !norm.contains(n)) {
                    continue;
                }
                Long id = ((Number) e.get("id")).longValue();
                if (used.add(id)) {
                    hit.add(e);
                }
            }
            return hit;
        } catch (Exception ex) {
            log.debug("[KgQuery] 实体链接失败: {}", ex.getMessage());
            return List.of();
        }
    }

    /**
     * 由实体扩展出候选 kb_id 及其权重。
     * 直连（1.0） + 一跳扩散（decay）。
     */
    public Map<String, Double> relatedKbIds(List<Map<String, Object>> entities) {
        Map<String, Double> kbScores = new LinkedHashMap<>();
        if (entities == null || entities.isEmpty()) {
            return kbScores;
        }
        try {
            long totalDocs = 1L;
            try {
                Long n = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM cs_vector_store", Long.class);
                if (n != null && n > 0) {
                    totalDocs = n;
                }
            } catch (Exception ignore) {
                // 拿不到总量就用默认值，只影响权重绝对量级
            }

            int usedEntities = 0;
            for (Map<String, Object> e : entities) {
                if (usedEntities >= maxEntities) {
                    break;
                }
                long docFreq = e.get("doc_freq") instanceof Number n2 ? n2.longValue() : 1L;
                // 过宽实体（例："修" 命中 78/803 篇）对所有文档一视同仁，只会把排序冲平，直接跳过
                if (docFreq > maxEntityDocFreq) {
                    continue;
                }
                usedEntities++;

                Long id = ((Number) e.get("id")).longValue();
                // IDF：越稀有越有区分度。之前统一给 1.0，导致 165 个候选分数完全相同、boost 起不到任何作用
                double idf = Math.log(1.0 + (double) totalDocs / Math.max(1L, docFreq));

                // 直连命中：同一实体被多个 kb 共享时按权重累加（证据叠加）
                for (String kbId : jdbcTemplate.queryForList(
                        "SELECT kb_id FROM kg_mention WHERE entity_id = ? LIMIT 50", String.class, id)) {
                    kbScores.merge(kbId, idf, Double::sum);
                }
                // 一跳扩散：权重按 hopDecay 衰减
                List<Long> neighbors = jdbcTemplate.queryForList(
                        "SELECT target_id FROM kg_relation WHERE source_id = ? " +
                        "UNION SELECT source_id FROM kg_relation WHERE target_id = ? LIMIT 20",
                        Long.class, id, id);
                for (Long nid : neighbors) {
                    double hopWeight = idf * hopDecay;
                    for (String kbId : jdbcTemplate.queryForList(
                            "SELECT kb_id FROM kg_mention WHERE entity_id = ? LIMIT 20", String.class, nid)) {
                        kbScores.merge(kbId, hopWeight, Double::sum);
                    }
                }
            }

            // 归一化到 [0,1]，保证 boost * w 的量级可控（boost 默认 0.05）
            double max = kbScores.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
            if (max > 0) {
                Map<String, Double> normalized = new LinkedHashMap<>();
                kbScores.forEach((k, v) -> normalized.put(k, v / max));
                return normalized;
            }
        } catch (Exception ex) {
            log.debug("[KgQuery] 关系扩展失败: {}", ex.getMessage());
        }
        return kbScores;
    }

    /**
     * 给检索结果加权（原地修改 score）。
     */
    public void applyBoost(List<com.qh.ai_agent.knowledge.retriever.RetrievalResult> results,
                           Map<String, Double> kbScores) {
        if (!enabled || results == null || results.isEmpty() || kbScores == null || kbScores.isEmpty()) {
            return;
        }
        for (com.qh.ai_agent.knowledge.retriever.RetrievalResult r : results) {
            Double w = kbScores.get(r.getKbId());
            if (w != null) {
                r.setScore(r.getScore() + boost * w);
            }
        }
        results.sort(Comparator.comparingDouble(
                com.qh.ai_agent.knowledge.retriever.RetrievalResult::getScore).reversed());
    }
}
