package com.qh.ai_agent.knowledge;

import com.qh.ai_agent.knowledge.parser.KbEntry;
import com.qh.ai_agent.knowledge.parser.UnifiedKbLoader;
import com.qh.ai_agent.knowledge.retriever.Bm25Retriever;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 知识库重建/增量入库服务。
 *
 * 解决管理员上传 FAQ 后 BM25 与 PgVector 不更新的问题：
 *   rebuildBm25()      重新加载比赛知识库 + FAQ，重建主 BM25 索引和 FAQ 独立索引；
 *   incrementalEmbed() 把新增条目按 kb_id 去重后增量写入 csVectorStore；
 *   reindexAll()       串行执行以上两步并返回统计。
 *
 * 所有失败都会转成结果对象并记录日志，不向上抛异常，避免上传接口/服务启动被打挂。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeReindexService {

    private final UnifiedKbLoader unifiedKbLoader;
    private final FaqLuceneIndex faqLuceneIndex;
    private final Bm25Retriever bm25Retriever;

    @Autowired(required = false)
    @Qualifier("csVectorStore")
    private VectorStore csVectorStore;

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    @Value("${cs.retrieval.bm25-index-dir:./kb-lucene-index}")
    private String bm25IndexPath;

    @Value("${cs.reindex.embed-batch-size:20}")
    private int embedBatchSize;

    @Value("${cs.reindex.vector-table:cs_vector_store}")
    private String vectorTable;

    /**
     * 重建检索侧 BM25：
     *   1. 清空 UnifiedKbLoader 缓存并重新解析比赛知识库；
     *   2. 从 KnowledgeBaseLoader 重新加载 FAQ（含 classpath 与管理后台上传目录）；
     *   3. 重建 FAQ 独立 Lucene 索引；
     *   4. 将比赛 KB + FAQ 一起写入主 BM25 索引。
     */
    public StepResult rebuildBm25() {
        long start = System.currentTimeMillis();
        try {
            unifiedKbLoader.clearCache();
            List<KbEntry> allEntries = new ArrayList<>(unifiedKbLoader.loadAll());
            int competitionCount = allEntries.size();

            int faqIndexed = 0;
            String faqMessage = "";

            // FAQ 条目独立加载：即使 FAQ Lucene 索引构建失败，也尽量让 FAQ 进入主 BM25。
            List<KbEntry> faqEntries = List.of();
            try {
                faqEntries = faqLuceneIndex.loadKbEntries();
                allEntries.addAll(faqEntries);
            } catch (Exception e) {
                faqMessage = "；FAQ 条目加载失败: " + e.getMessage();
                log.error("[KnowledgeReindex] FAQ 条目加载失败，主 BM25 继续", e);
            }

            try {
                faqIndexed = faqLuceneIndex.rebuild();
            } catch (Exception e) {
                faqMessage = faqMessage + "；FAQ 独立索引重建失败: " + e.getMessage();
                log.error("[KnowledgeReindex] FAQ 独立索引重建失败，主 BM25 仍继续", e);
            }

            bm25Retriever.buildIndex(allEntries, bm25IndexPath);
            long elapsed = System.currentTimeMillis() - start;
            String message = String.format("比赛知识库 %d 条，FAQ %d 条，主 BM25 共 %d 条，FAQ Lucene 索引 %d 条%s",
                    competitionCount, faqEntries.size(), allEntries.size(), faqIndexed, faqMessage);
            log.info("[KnowledgeReindex] rebuildBm25 完成: {}，耗时 {}ms", message, elapsed);
            return new StepResult(true, "BM25", allEntries.size(), elapsed, message);
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            String message = "BM25 重建失败: " + e.getMessage();
            log.error("[KnowledgeReindex] rebuildBm25 失败", e);
            return new StepResult(false, "BM25", 0, elapsed, message);
        }
    }

    /**
     * 增量写入向量库：
     *   - 比赛知识库 + FAQ 统一转换为 Spring AI Document；
     *   - 按 kb_id 去重，查询 cs_vector_store 已存在的 kb_id 并跳过；
     *   - 批量调用 csVectorStore.add()，单批失败不影响其它批次。
     */
    public EmbedResult incrementalEmbed() {
        long start = System.currentTimeMillis();
        try {
            Map<String, KbEntry> uniqueEntries = loadUniqueEntries();
            int total = uniqueEntries.size();
            if (csVectorStore == null) {
                long elapsed = System.currentTimeMillis() - start;
                String message = "csVectorStore 未启用（cs.vector.enabled=false），跳过向量增量入库";
                log.info("[KnowledgeReindex] {}", message);
                return new EmbedResult(true, total, 0, total, 0, elapsed, message);
            }

            Set<String> existingIds = loadExistingKbIds();
            List<KbEntry> pending = new ArrayList<>();
            for (KbEntry entry : uniqueEntries.values()) {
                if (!existingIds.contains(entry.getKbId())) {
                    pending.add(entry);
                }
            }
            int skipped = total - pending.size();
            if (pending.isEmpty()) {
                long elapsed = System.currentTimeMillis() - start;
                String message = "无新增条目，全部按 kb_id 跳过";
                log.info("[KnowledgeReindex] incrementalEmbed: {}", message);
                return new EmbedResult(true, total, 0, skipped, 0, elapsed, message);
            }

            int batchSize = Math.max(1, embedBatchSize);
            int added = 0;
            int failed = 0;
            String lastError = "";
            for (int i = 0; i < pending.size(); i += batchSize) {
                int end = Math.min(i + batchSize, pending.size());
                List<Document> batch = new ArrayList<>(end - i);
                for (KbEntry entry : pending.subList(i, end)) {
                    batch.add(toVectorDocument(entry));
                }
                try {
                    csVectorStore.add(batch);
                    added += batch.size();
                } catch (Exception e) {
                    failed += batch.size();
                    lastError = e.getMessage();
                    log.error("[KnowledgeReindex] 向量批量写入失败，batch={}~{}", i, end, e);
                }
            }

            long elapsed = System.currentTimeMillis() - start;
            boolean success = failed == 0;
            String message = String.format("待入库 %d 条，新增 %d 条，跳过 %d 条，失败 %d 条%s",
                    total, added, skipped, failed, failed > 0 ? "，最后错误: " + lastError : "");
            log.info("[KnowledgeReindex] incrementalEmbed 完成: {}，耗时 {}ms", message, elapsed);
            return new EmbedResult(success, total, added, skipped, failed, elapsed, message);
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            String message = "向量增量入库失败: " + e.getMessage();
            log.error("[KnowledgeReindex] incrementalEmbed 失败", e);
            return new EmbedResult(false, 0, 0, 0, 0, elapsed, message);
        }
    }

    /** 串行执行 BM25 重建 + 向量增量入库，返回可序列化统计。 */
    public ReindexReport reindexAll() {
        long start = System.currentTimeMillis();
        StepResult bm25 = rebuildBm25();
        EmbedResult vector = incrementalEmbed();
        long elapsed = System.currentTimeMillis() - start;
        boolean success = bm25.success() && vector.success();
        String message = success ? "知识库重建完成" : "知识库重建部分失败，请查看 steps 详情";
        log.info("[KnowledgeReindex] reindexAll success={}, elapsed={}ms, bm25={}, vector={}",
                success, elapsed, bm25.message(), vector.message());
        return new ReindexReport(success, elapsed, bm25, vector, message);
    }

    private Map<String, KbEntry> loadUniqueEntries() {
        unifiedKbLoader.clearCache();
        List<KbEntry> all = new ArrayList<>(unifiedKbLoader.loadAll());
        all.addAll(faqLuceneIndex.loadKbEntries());

        Map<String, KbEntry> unique = new LinkedHashMap<>();
        for (KbEntry entry : all) {
            if (entry.getQuestion() == null || entry.getQuestion().isBlank()) {
                continue;
            }
            String kbId = entry.getKbId();
            if (kbId == null || kbId.isBlank()) {
                kbId = "auto_" + Integer.toHexString(Objects.hash(entry.getSourceFile(), entry.getQuestion()));
                entry.setKbId(kbId);
            }
            unique.putIfAbsent(kbId, entry);
        }
        return unique;
    }

    private Set<String> loadExistingKbIds() {
        if (jdbcTemplate == null) {
            return Set.of();
        }
        String table = safeVectorTable();
        try {
            List<String> ids = jdbcTemplate.queryForList(
                    "SELECT metadata->>'kb_id' FROM " + table + " WHERE metadata->>'kb_id' IS NOT NULL",
                    String.class);
            Set<String> result = new HashSet<>();
            for (String id : ids) {
                if (id != null && !id.isBlank()) {
                    result.add(id);
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("[KnowledgeReindex] 读取向量库已有 kb_id 失败，将按空集合继续: {}", e.getMessage());
            return Set.of();
        }
    }

    private String safeVectorTable() {
        if (vectorTable != null && vectorTable.matches("[A-Za-z0-9_]+")) {
            return vectorTable;
        }
        return "cs_vector_store";
    }

    private Document toVectorDocument(KbEntry entry) {
        String question = entry.getQuestion() != null ? entry.getQuestion() : "";
        String answer = entry.getAnswer() != null ? entry.getAnswer() : "";
        Map<String, Object> metadata = new HashMap<>();
        putIfNotNull(metadata, "kb_id", entry.getKbId());
        putIfNotNull(metadata, "question", entry.getQuestion());
        putIfNotNull(metadata, "domain", entry.getDomain());
        putIfNotNull(metadata, "category", entry.getCategory());
        putIfNotNull(metadata, "source", entry.getSourceFile());
        metadata.put("doc_type", FaqLuceneIndex.FAQ_DOMAIN.equalsIgnoreCase(entry.getDomain())
                ? "FAQ" : "COMPETITION_KB");
        metadata.put("chunk_index", entry.getChunkIndex());
        return new Document("Q: " + question + "\nA: " + answer, metadata);
    }

    private void putIfNotNull(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    public record StepResult(boolean success, String step, int indexed, long elapsedMs, String message) {
        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("success", success);
            map.put("step", step);
            map.put("indexed", indexed);
            map.put("elapsedMs", elapsedMs);
            map.put("message", message);
            return map;
        }
    }

    public record EmbedResult(boolean success, int total, int added, int skipped, int failed,
                              long elapsedMs, String message) {
        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("success", success);
            map.put("total", total);
            map.put("added", added);
            map.put("skipped", skipped);
            map.put("failed", failed);
            map.put("elapsedMs", elapsedMs);
            map.put("message", message);
            return map;
        }
    }

    public record ReindexReport(boolean success, long elapsedMs, StepResult bm25,
                                EmbedResult vector, String message) {
        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("success", success);
            map.put("elapsedMs", elapsedMs);
            map.put("bm25", bm25.toMap());
            map.put("vector", vector.toMap());
            map.put("message", message);
            return map;
        }
    }
}