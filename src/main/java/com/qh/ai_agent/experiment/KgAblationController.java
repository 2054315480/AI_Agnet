package com.qh.ai_agent.experiment;

import com.qh.ai_agent.eval.EvalDatasetBuilder;
import com.qh.ai_agent.eval.EvalService;
import com.qh.ai_agent.eval.RetrievalMetrics;
import com.qh.ai_agent.kg.KgExtractor;
import com.qh.ai_agent.kg.KgQueryService;
import com.qh.ai_agent.knowledge.retriever.HybridRetriever;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

@Slf4j
@RestController
@RequestMapping("/admin/kg")
@RequiredArgsConstructor
public class KgAblationController {

    private final HybridRetriever hybridRetriever;
    private final KgQueryService kgQueryService;
    private final EvalDatasetBuilder datasetBuilder;
    private final JdbcTemplate jdbcTemplate;

    @Value("${cs.kg.boost:0.05}")
    private double boost;

    @Value("${cs.kg.hop-decay:0.5}")
    private double hopDecay;

    @Value("${cs.kg.max-entity-doc-freq:40}")
    private int maxEntityDocFreq;

    @Value("${cs.kg.max-entities:8}")
    private int maxEntities;

    @PostMapping("/ablation")
    public ResponseEntity<?> ablation(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> req = body == null ? Map.of() : body;
        int requestedSize = Integer.parseInt(String.valueOf(req.getOrDefault("sampleSize", 30)));
        int topK = Integer.parseInt(String.valueOf(req.getOrDefault("topK", 5)));
        int sampleSize = Math.max(1, requestedSize);
        int effectiveTopK = Math.max(1, Math.min(20, topK));

        List<EvalService.Sample> all;
        try {
            all = datasetBuilder.loadLabeledSet();
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "加载标注集失败: " + e.getMessage()));
        }
        List<EvalService.Sample> samples = all.stream().limit(sampleSize).toList();
        if (samples.isEmpty()) {
            return ResponseEntity.status(500).body(Map.of("error", "标注集为空，无法消融"));
        }

        List<Double> baseRecall = new ArrayList<>();
        List<Double> baseMrr = new ArrayList<>();
        List<Double> baseNdcg = new ArrayList<>();
        List<Double> kgRecall = new ArrayList<>();
        List<Double> kgMrr = new ArrayList<>();
        List<Double> kgNdcg = new ArrayList<>();
        List<Map<String, Object>> details = new ArrayList<>();
        int entityLinkedSamples = 0;
        int entityTotal = 0;

        for (EvalService.Sample sample : samples) {
            HybridRetriever.RetrievalExplain explain;
            try {
                explain = hybridRetriever.explain(sample.query());
            } catch (Exception e) {
                log.warn("[KgAblation] explain 失败 sample={}: {}", sample.id(), e.getMessage());
                continue;
            }

            List<HybridRetriever.ExplainItem> candidates =
                    explain.fused().stream()
                            .sorted(Comparator.comparingDouble(HybridRetriever.ExplainItem::score).reversed())
                            .toList();
            List<Map<String, Object>> entities = linkEntitiesForAblation(sample.query());
            Map<String, Double> kbScores = entities.isEmpty()
                    ? Map.of() : kgQueryService.relatedKbIds(entities);
            if (!entities.isEmpty()) {
                entityLinkedSamples++;
                entityTotal += entities.size();
            }

            List<String> baseIds = candidates.stream()
                    .limit(effectiveTopK)
                    .map(HybridRetriever.ExplainItem::kbId)
                    .filter(Objects::nonNull)
                    .toList();
            List<String> kgIds = candidates.stream()
                    .map(item -> {
                        double score = item.score();
                        Double weight = kbScores.get(item.kbId());
                        if (weight != null) {
                            score += boost * weight;
                        }
                        return new Ranked(item.kbId(), score);
                    })
                    .filter(r -> r.kbId() != null)
                    .sorted(Comparator.comparingDouble(Ranked::score).reversed())
                    .limit(effectiveTopK)
                    .map(Ranked::kbId)
                    .toList();

            Set<String> expected = new HashSet<>(sample.expectedKbIds());
            double r1 = RetrievalMetrics.recallAtK(baseIds, expected, effectiveTopK);
            double m1 = RetrievalMetrics.mrr(baseIds, expected);
            double n1 = RetrievalMetrics.ndcgAtK(baseIds, expected, effectiveTopK);
            double r2 = RetrievalMetrics.recallAtK(kgIds, expected, effectiveTopK);
            double m2 = RetrievalMetrics.mrr(kgIds, expected);
            double n2 = RetrievalMetrics.ndcgAtK(kgIds, expected, effectiveTopK);
            baseRecall.add(r1);
            baseMrr.add(m1);
            baseNdcg.add(n1);
            kgRecall.add(r2);
            kgMrr.add(m2);
            kgNdcg.add(n2);

            Map<String, Object> d = new LinkedHashMap<>();
            d.put("id", sample.id());
            d.put("query", sample.query());
            d.put("expected", sample.expectedKbIds());
            d.put("entities", entities.stream().map(e -> e.get("name")).toList());
            d.put("relatedKbCount", kbScores.size());
            d.put("withoutKg", Map.of("recallAtK", round(r1), "mrr", round(m1), "ndcgAtK", round(n1)));
            d.put("withKg", Map.of("recallAtK", round(r2), "mrr", round(m2), "ndcgAtK", round(n2)));
            details.add(d);
        }

        Map<String, Object> withoutKg = metrics(baseRecall, baseMrr, baseNdcg);
        Map<String, Object> withKg = metrics(kgRecall, kgMrr, kgNdcg);
        Map<String, Object> delta = new LinkedHashMap<>();
        delta.put("recallAtK", round(num(withKg.get("recallAtK")) - num(withoutKg.get("recallAtK"))));
        delta.put("mrr", round(num(withKg.get("mrr")) - num(withoutKg.get("mrr"))));
        delta.put("ndcgAtK", round(num(withKg.get("ndcgAtK")) - num(withoutKg.get("ndcgAtK"))));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("datasetType", "labeled");
        out.put("datasetSource", datasetBuilder.getLabeledSetPath());
        out.put("sampleCount", details.size());
        out.put("requestedSampleSize", requestedSize);
        out.put("topK", effectiveTopK);
        out.put("method", "same_candidates_in_process_ablation");
        out.put("onlineRuntimeToggle", false);
        out.put("note", "未热切换 cs.kg.enabled；对同一份 explain 融合候选分别跳过/应用图谱 boost，"
                + "两组候选池一致。结果反映的是图谱写回带来的排序增量，不等同于线上开关 A/B。");
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("kgEnabled", kgQueryService.isEnabled());
        config.put("boost", boost);
        config.put("hopDecay", hopDecay);
        config.put("maxEntityDocFreq", maxEntityDocFreq);
        config.put("maxEntities", maxEntities);
        out.put("kgEffectiveConfig", config);
        out.put("entityLinkedSamples", entityLinkedSamples);
        out.put("entityTotal", entityTotal);
        out.put("withoutKg", withoutKg);
        out.put("withKg", withKg);
        out.put("delta", delta);
        out.put("deltaDefinition", "withKg - withoutKg（正数表示图谱加权更好）");
        out.put("details", details);
        log.info("[KgAblation] samples={} withoutKg={} withKg={} delta={}",
                details.size(), withoutKg, withKg, delta);
        return ResponseEntity.ok(out);
    }

    /**
     * 消融专用实体链接：绕过 kg.enabled 的短路，但仍复用同一套 kg_entity 归一化匹配规则。
     * 这样即使线上开关关闭，也能做 what-if 实验；是否应用到检索由本实验显式控制。
     */
    private List<Map<String, Object>> linkEntitiesForAblation(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        try {
            String norm = KgExtractor.normalize(query);
            List<Map<String, Object>> all = jdbcTemplate.queryForList(
                    "SELECT id, name, normalized, type, doc_freq FROM kg_entity ORDER BY length(normalized) DESC");
            List<Map<String, Object>> hits = new ArrayList<>();
            for (Map<String, Object> entity : all) {
                String normalized = String.valueOf(entity.get("normalized"));
                if (!normalized.isEmpty() && norm.contains(normalized)) {
                    hits.add(entity);
                }
            }
            return hits;
        } catch (Exception e) {
            log.debug("[KgAblation] 实体链接失败: {}", e.getMessage());
            return List.of();
        }
    }

    private static Map<String, Object> metrics(List<Double> recalls, List<Double> mrrs, List<Double> ndcgs) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("recallAtK", round(avg(recalls)));
        out.put("mrr", round(avg(mrrs)));
        out.put("ndcgAtK", round(avg(ndcgs)));
        return out;
    }

    private static double avg(List<Double> values) {
        return values.isEmpty() ? 0.0 : values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    private static double num(Object value) {
        return value instanceof Number n ? n.doubleValue() : 0.0;
    }

    private static double round(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    private record Ranked(String kbId, double score) {
    }
}
