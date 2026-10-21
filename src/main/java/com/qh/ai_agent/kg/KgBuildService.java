package com.qh.ai_agent.kg;

import com.qh.ai_agent.knowledge.parser.KbEntry;
import com.qh.ai_agent.knowledge.parser.UnifiedKbLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 知识图谱构建。
 *
 * 三阶段：
 *   1. 规则抽取（毫秒级，零成本）
 *   2. 并发批次处理（用线程池并行抽取，这是"构建并行化"的真实落点）
 *   3. 归一化合并（别名并集 + doc_freq 重算）
 *
 * 为什么并行在这里有效：抽取是 IO/CPU 混合型任务，单条 KB 抽取互相独立，
 * 天然可并行。串行 774 条 vs 8 线程并行的耗时差异是可量化的。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KgBuildService {

    private final KgExtractor extractor;
    private final UnifiedKbLoader unifiedKbLoader;
    private final JdbcTemplate jdbcTemplate;
    private final ThreadPoolTaskExecutor buildExecutor;

    private volatile Map<String, Object> lastReport = Map.of("status", "never-run");

    /**
     * 全量重建。
     */
    public Map<String, Object> buildAll(int threads) {
        long t0 = System.currentTimeMillis();
        List<KbEntry> entries;
        try {
            entries = unifiedKbLoader.loadAll();
        } catch (Exception e) {
            return Map.of("success", false, "error", "加载知识库失败: " + e.getMessage());
        }
        if (entries == null || entries.isEmpty()) {
            return Map.of("success", false, "error", "知识库为空");
        }

        // 清空旧数据
        jdbcTemplate.execute("TRUNCATE kg_mention, kg_relation, kg_entity RESTART IDENTITY CASCADE");

        // 阶段 2：并发抽取
        Map<String, Set<String>> entityKbMap = new ConcurrentHashMap<>();   // name -> kbIds
        Map<String, String> entityTypeMap = new ConcurrentHashMap<>();      // name -> type
        Map<String, Set<String>> relationSet = new ConcurrentHashMap<>();   // "src|rel|tgt" -> kbIds
        AtomicInteger processed = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        AtomicInteger mentionFail = new AtomicInteger();

        int poolSize = Math.max(1, Math.min(threads, 16));
        ExecutorService pool = Executors.newFixedThreadPool(poolSize);
        CountDownLatch latch = new CountDownLatch(entries.size());

        for (KbEntry entry : entries) {
            pool.submit(() -> {
                try {
                    KgExtractor.Extracted ex = extractor.extract(
                            entry.getKbId(), entry.getQuestion(), entry.getAnswer());
                    for (KgExtractor.Entity e : ex.entities()) {
                        entityKbMap.computeIfAbsent(e.name(), k -> ConcurrentHashMap.newKeySet()).add(entry.getKbId());
                        entityTypeMap.putIfAbsent(e.name(), e.type());
                    }
                    for (KgExtractor.Relation r : ex.relations()) {
                        String key = r.source() + "|" + r.relation() + "|" + r.target();
                        relationSet.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(entry.getKbId());
                    }
                    processed.incrementAndGet();
                } catch (Exception e) {
                    failed.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        try {
            latch.await(120, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        pool.shutdownNow();
        long extractMs = System.currentTimeMillis() - t0;

        // 阶段 3：落库
        long t1 = System.currentTimeMillis();
        Map<String, Long> entityIdMap = new HashMap<>();
        for (Map.Entry<String, Set<String>> e : entityKbMap.entrySet()) {
            String name = e.getKey();
            String type = entityTypeMap.getOrDefault(name, "UNKNOWN");
            Long id;
            try {
                jdbcTemplate.update(
                        "INSERT INTO kg_entity (name, normalized, type, doc_freq) VALUES (?,?,?,?) " +
                        "ON CONFLICT (normalized, type) DO UPDATE SET doc_freq = EXCLUDED.doc_freq",
                        name, KgExtractor.normalize(name), type, e.getValue().size());
                id = jdbcTemplate.queryForObject(
                        "SELECT id FROM kg_entity WHERE normalized=? AND type=?",
                        Long.class, KgExtractor.normalize(name), type);
            } catch (Exception ex) {
                log.warn("[KgBuild] 实体入库失败 {}: {}", name, ex.getMessage());
                continue;
            }
            if (id == null) {
                continue;
            }
            entityIdMap.put(name, id);
            for (String kbId : e.getValue()) {
                try {
                    jdbcTemplate.update(
                            "INSERT INTO kg_mention (entity_id, kb_id, field) VALUES (?,?,?)",
                            id, kbId, "content");
                } catch (Exception ex) {
                    if (mentionFail.getAndIncrement() < 3) {
                        log.warn("[KgBuild] mention 插入失败 entityId={} kbId={}: {}", id, kbId, ex.getMessage());
                    }
                }
            }
        }

        int relationCount = 0;
        AtomicInteger relationFail = new AtomicInteger();
        for (Map.Entry<String, Set<String>> r : relationSet.entrySet()) {
            String[] parts = r.getKey().split("\\|");
            if (parts.length != 3) {
                continue;
            }
            Long src = entityIdMap.get(parts[0]);
            Long tgt = entityIdMap.get(parts[2]);
            if (src == null || tgt == null) {
                continue;
            }
            for (String kbId : r.getValue()) {
                try {
                    jdbcTemplate.update(
                            "INSERT INTO kg_relation (source_id, target_id, relation, evidence_kb_id) VALUES (?,?,?,?)",
                            src, tgt, parts[1], kbId);
                    relationCount++;
                } catch (Exception ex) {
                    if (relationFail.getAndIncrement() < 3) {
                        log.warn("[KgBuild] relation 插入失败 {}: {}", r.getKey(), ex.getMessage());
                    }
                }
            }
        }
        long persistMs = System.currentTimeMillis() - t1;

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("success", true);
        report.put("kbEntries", entries.size());
        report.put("processed", processed.get());
        report.put("failed", failed.get());
        report.put("entities", entityKbMap.size());
        report.put("mentions", entityKbMap.values().stream().mapToInt(Set::size).sum());
        report.put("relations", relationCount);
        report.put("mentionFail", mentionFail.get());
        report.put("relationFail", relationFail.get());
        report.put("threads", poolSize);
        report.put("extractMs", extractMs);
        report.put("persistMs", persistMs);
        report.put("totalMs", System.currentTimeMillis() - t0);
        lastReport = report;
        log.info("[KgBuild] 完成: {} 实体 / {} 关系, 抽取 {}ms, 落库 {}ms, 线程 {}",
                entityKbMap.size(), relationCount, extractMs, persistMs, poolSize);
        return report;
    }

    public Map<String, Object> stats() {
        Map<String, Object> out = new LinkedHashMap<>();
        try {
            out.put("entities", jdbcTemplate.queryForObject("SELECT COUNT(*) FROM kg_entity", Long.class));
            out.put("mentions", jdbcTemplate.queryForObject("SELECT COUNT(*) FROM kg_mention", Long.class));
            out.put("relations", jdbcTemplate.queryForObject("SELECT COUNT(*) FROM kg_relation", Long.class));
            out.put("byType", jdbcTemplate.queryForList(
                    "SELECT type, COUNT(*) AS c FROM kg_entity GROUP BY type ORDER BY c DESC"));
            out.put("topEntities", jdbcTemplate.queryForList(
                    "SELECT name, type, doc_freq FROM kg_entity ORDER BY doc_freq DESC LIMIT 10"));
        } catch (Exception e) {
            out.put("error", e.getMessage());
        }
        out.put("lastBuild", lastReport);
        return out;
    }
}
