package com.qh.ai_agent.knowledge;

import com.qh.ai_agent.knowledge.parser.KbEntry;
import com.qh.ai_agent.knowledge.retriever.ChineseBigramAnalyzer;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.MMapDirectory;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * FAQ 专用 Lucene BM25 索引。
 *
 * 和主知识库 Bm25Retriever 的职责区分：
 *   - Bm25Retriever 索引比赛知识库 KbEntry.question，服务 HybridRetriever；
 *   - FaqLuceneIndex 只索引 KnowledgeBaseLoader 中的 FAQ 文档（classpath knowledge/faq
 *     以及管理员上传的 data/faq），使用独立目录，供 Tier1 FAQ 直接匹配。
 *
 * 索引字段：
 *   kb_id          FAQ 稳定编号（来源+问题哈希），用于溯源/去重；
 *   question       参与 BM25 分词匹配的提问文本；
 *   question_exact 归一化后的完整问题，用于精确命中加权；
 *   answer/source/category/chunk_index 仅存储，检索命中后原样返回。
 *
 * 配置开关 cs.faq.lucene-enabled 默认 true；索引构建失败只记录日志，
 * KnowledgeSearchService 会回退到原有自研相似度逻辑。
 */
@Slf4j
@Component
public class FaqLuceneIndex {

    public static final String FAQ_DOMAIN = "faq";

    private final KnowledgeBaseLoader knowledgeBaseLoader;
    private final ChineseBigramAnalyzer analyzer = new ChineseBigramAnalyzer();

    @Value("${cs.faq.lucene-enabled:true}")
    private boolean enabled;

    @Value("${cs.faq.lucene-index-dir:./kb-lucene-faq-index}")
    private String indexPath;

    /** 查询 bigram 的最少命中比例，越高越严格。 */
    @Value("${cs.faq.lucene-min-match-ratio:0.5}")
    private double minMatchRatio;

    @Value("${cs.faq.lucene-top-k:5}")
    private int defaultTopK;

    private volatile Directory indexDir;
    private volatile boolean indexReady = false;

    public FaqLuceneIndex(KnowledgeBaseLoader knowledgeBaseLoader) {
        this.knowledgeBaseLoader = knowledgeBaseLoader;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isIndexReady() {
        return indexReady;
    }

    /**
     * 重建 FAQ Lucene 索引。FAQ 全部来自 KnowledgeBaseLoader，
     * 因而 classpath 与 data/faq 下的 FAQ 都会进入同一份独立索引。
     *
     * @return 实际写入的 FAQ 条数
     */
    public synchronized int rebuild() throws IOException {
        if (!enabled) {
            log.info("[FaqLuceneIndex] cs.faq.lucene-enabled=false，跳过 FAQ 索引构建");
            return 0;
        }

        List<Document> faqDocs = loadFaqDocuments();
        Path path = Path.of(indexPath);
        Files.createDirectories(path);
        Directory newDir = MMapDirectory.open(path);
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE);

        int count = 0;
        try (IndexWriter writer = new IndexWriter(newDir, config)) {
            for (Document doc : faqDocs) {
                String question = questionOf(doc);
                if (question == null || question.isBlank()) {
                    continue;
                }
                org.apache.lucene.document.Document luceneDoc = new org.apache.lucene.document.Document();
                luceneDoc.add(new StringField("kb_id", kbIdOf(doc), Field.Store.YES));
                luceneDoc.add(new TextField("question", question, Field.Store.YES));
                luceneDoc.add(new StringField("question_exact", normalize(question), Field.Store.NO));
                luceneDoc.add(new StoredField("answer", answerOf(doc)));
                luceneDoc.add(new StringField("source", sourceOf(doc), Field.Store.YES));
                luceneDoc.add(new StringField("category", categoryOf(doc), Field.Store.YES));
                luceneDoc.add(new StoredField("chunk_index", chunkIndexOf(doc)));
                writer.addDocument(luceneDoc);
                count++;
            }
            writer.commit();
        } catch (IOException | RuntimeException e) {
            try {
                newDir.close();
            } catch (IOException closeError) {
                log.warn("[FaqLuceneIndex] 关闭失败索引目录异常: {}", closeError.getMessage());
            }
            throw e;
        }

        Directory oldDir = this.indexDir;
        this.indexDir = newDir;
        this.indexReady = true;
        if (oldDir != null) {
            try {
                oldDir.close();
            } catch (IOException e) {
                log.warn("[FaqLuceneIndex] 关闭旧索引目录异常: {}", e.getMessage());
            }
        }
        log.info("[FaqLuceneIndex] FAQ Lucene 索引重建完成: {} 条 -> {}", count, indexPath);
        return count;
    }

    /**
     * Lucene BM25 检索 FAQ。返回结果按 BM25 分数归一化，
     * 并强制要求命中查询 bigram 的最低比例，避免长问题被弱相关词召回。
     */
    public synchronized List<FaqHit> search(String query, int topK) {
        if (!enabled || query == null || query.isBlank()) {
            return List.of();
        }
        try {
            if (!indexReady) {
                rebuild();
            }
            Directory dir = indexDir;
            if (dir == null) {
                return List.of();
            }
            int size = topK > 0 ? topK : Math.max(1, defaultTopK);
            try (DirectoryReader reader = DirectoryReader.open(dir)) {
                IndexSearcher searcher = new IndexSearcher(reader);
                Query luceneQuery = buildQuery(query);
                if (luceneQuery == null) {
                    return List.of();
                }
                TopDocs topDocs = searcher.search(luceneQuery, size);
                if (topDocs.scoreDocs.length == 0) {
                    return List.of();
                }
                float maxScore = topDocs.scoreDocs[0].score;
                List<FaqHit> hits = new ArrayList<>();
                for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                    org.apache.lucene.document.Document doc = searcher.doc(scoreDoc.doc);
                    double normalized = maxScore > 0 ? (double) (scoreDoc.score / maxScore) : 0.0;
                    hits.add(new FaqHit(
                            doc.get("kb_id"),
                            doc.get("question"),
                            doc.get("answer"),
                            doc.get("source"),
                            doc.get("category"),
                            storedInt(doc, "chunk_index"),
                            normalized
                    ));
                }
                log.debug("[FaqLuceneIndex] query={} 命中 {} 条，topScore={}", query, hits.size(), maxScore);
                return hits;
            }
        } catch (Exception e) {
            log.error("[FaqLuceneIndex] FAQ BM25 检索失败，将回退自研相似度: {}", e.getMessage());
            return List.of();
        }
    }

    /** 读取当前所有 FAQ 文档（classpath + 外部上传目录）。 */
    public List<Document> loadFaqDocuments() {
        List<Document> faqDocs = new ArrayList<>();
        for (Document doc : knowledgeBaseLoader.loadAll()) {
            Map<String, Object> meta = doc.getMetadata();
            if (meta == null || !"FAQ".equals(meta.get("doc_type"))) {
                continue;
            }
            String question = questionOf(doc);
            if (question != null && !question.isBlank()) {
                faqDocs.add(doc);
            }
        }
        return faqDocs;
    }

    /** 将 FAQ 文档转换为统一的 KbEntry，供主 BM25 索引与向量增量入库复用。 */
    public List<KbEntry> loadKbEntries() {
        List<KbEntry> entries = new ArrayList<>();
        for (Document doc : loadFaqDocuments()) {
            entries.add(toKbEntry(doc));
        }
        return entries;
    }

    public KbEntry toKbEntry(Document doc) {
        return KbEntry.builder()
                .kbId(kbIdOf(doc))
                .question(questionOf(doc))
                .answer(answerOf(doc))
                .domain(FAQ_DOMAIN)
                .category(categoryOf(doc))
                .sourceFile(sourceOf(doc))
                .chunkIndex(chunkIndexOf(doc))
                .build();
    }

    private Query buildQuery(String query) throws IOException {
        List<String> tokens = tokenize(query);
        if (tokens.isEmpty()) {
            return null;
        }
        BooleanQuery.Builder tokenBuilder = new BooleanQuery.Builder();
        for (String token : tokens) {
            tokenBuilder.add(new TermQuery(new Term("question", token)), BooleanClause.Occur.SHOULD);
        }
        double ratio = Math.max(0.0, Math.min(1.0, minMatchRatio));
        int minShouldMatch = Math.max(1, (int) Math.ceil(tokens.size() * ratio));
        tokenBuilder.setMinimumNumberShouldMatch(minShouldMatch);

        BooleanQuery.Builder outer = new BooleanQuery.Builder();
        outer.add(tokenBuilder.build(), BooleanClause.Occur.MUST);
        Query exact = new TermQuery(new Term("question_exact", normalize(query)));
        outer.add(new BoostQuery(exact, 4.0f), BooleanClause.Occur.SHOULD);
        return outer.build();
    }

    private List<String> tokenize(String text) throws IOException {
        List<String> tokens = new ArrayList<>();
        try (TokenStream ts = analyzer.tokenStream("question", text)) {
            CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
            ts.reset();
            while (ts.incrementToken()) {
                String token = term.toString();
                if (token != null && !token.isBlank()) {
                    tokens.add(token);
                }
            }
            ts.end();
        }
        return tokens;
    }

    private String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private String questionOf(Document doc) {
        Map<String, Object> meta = doc.getMetadata();
        if (meta == null || meta.get("question") == null) {
            return null;
        }
        return meta.get("question").toString();
    }

    private String answerOf(Document doc) {
        String text = doc.getText();
        if (text == null) {
            return "";
        }
        int answerIndex = text.indexOf("\nA:");
        if (answerIndex >= 0) {
            return text.substring(answerIndex + 3).trim();
        }
        return text;
    }

    private String sourceOf(Document doc) {
        Map<String, Object> meta = doc.getMetadata();
        Object value = meta == null ? null : meta.get("source");
        return value == null || value.toString().isBlank() ? "FAQ-未知来源" : value.toString();
    }

    private String categoryOf(Document doc) {
        Map<String, Object> meta = doc.getMetadata();
        Object value = meta == null ? null : meta.get("category");
        return value == null || value.toString().isBlank() ? "未分类" : value.toString();
    }

    private int chunkIndexOf(Document doc) {
        Map<String, Object> meta = doc.getMetadata();
        Object value = meta == null ? null : meta.get("chunk_index");
        return value instanceof Number number ? number.intValue() : -1;
    }

    private int storedInt(org.apache.lucene.document.Document doc, String field) {
        String value = doc.get(field);
        if (value == null || value.isBlank()) {
            return -1;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * FAQ 没有业务 kb_id，使用来源 + 问题文本生成稳定编号。
     * 相同来源、相同问题在多次重建后编号保持一致，便于向量库按 kb_id 去重。
     */
    public String kbIdOf(Document doc) {
        Map<String, Object> meta = doc.getMetadata();
        Object explicit = meta == null ? null : meta.get("kb_id");
        if (explicit != null && !explicit.toString().isBlank()) {
            return explicit.toString();
        }
        String raw = sourceOf(doc) + "|" + questionOf(doc);
        return "faq_" + sha256Hex(raw).substring(0, 12);
    }

    private String sha256Hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(text.hashCode());
        }
    }

    /** 一条 FAQ 的 BM25 命中结果。 */
    public record FaqHit(
            String kbId,
            String question,
            String answer,
            String source,
            String category,
            int chunkIndex,
            double score
    ) {
        public String content() {
            return "Q: " + question + "\nA: " + answer;
        }
    }
}
