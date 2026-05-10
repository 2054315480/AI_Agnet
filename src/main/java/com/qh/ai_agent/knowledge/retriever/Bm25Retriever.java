package com.qh.ai_agent.knowledge.retriever;

import com.qh.ai_agent.knowledge.parser.KbEntry;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.MMapDirectory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于 Apache Lucene 的 BM25 稀疏检索器。
 * 对知识库条目的 question 字段建索引，使用结巴中文分词。
 *
 * 索引目录：./kb-lucene-index（可配置）
 */
@Slf4j
@Component
public class Bm25Retriever {

    private final JiebaAnalyzer analyzer;
    private Directory indexDir;
    private boolean indexReady = false;

    public Bm25Retriever() {
        this.analyzer = new JiebaAnalyzer();
    }

    /**
     * 为知识库条目构建 Lucene 索引。
     * 在 UnifiedKbLoader 加载完毕后调用。
     *
     * @param entries  所有知识库条目
     * @param indexPath 索引存储路径
     */
    public void buildIndex(List<KbEntry> entries, String indexPath) throws IOException {
        Path path = Path.of(indexPath);
        this.indexDir = MMapDirectory.open(path);

        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
        // Lucene 9.x 默认使用 BM25Similarity，无需显式设置

        try (IndexWriter writer = new IndexWriter(indexDir, config)) {
            int count = 0;
            for (KbEntry entry : entries) {
                if (entry.getQuestion() == null || entry.getQuestion().isBlank()) {
                    continue;
                }
                Document doc = new Document();
                // StringField: 不分词，精确存储
                doc.add(new StringField("kbId",
                        entry.getKbId() != null ? entry.getKbId() : "", Field.Store.YES));
                // TextField: 分词索引，用于 BM25 匹配；存储原始文本用于返回
                doc.add(new TextField("question",
                        entry.getQuestion(), Field.Store.YES));
                // StoredField: 仅存储，不索引
                doc.add(new StoredField("answer",
                        entry.getAnswer() != null ? entry.getAnswer() : ""));
                doc.add(new StringField("domain",
                        entry.getDomain() != null ? entry.getDomain() : "", Field.Store.YES));
                doc.add(new StringField("category",
                        entry.getCategory() != null ? entry.getCategory() : "", Field.Store.YES));
                doc.add(new StringField("sourceFile",
                        entry.getSourceFile() != null ? entry.getSourceFile() : "", Field.Store.YES));

                writer.addDocument(doc);
                count++;
            }
            writer.commit();
            log.info("[Bm25Retriever] 索引构建完成: {} 条文档 → {}", count, indexPath);
        }
        indexReady = true;
    }

    /**
     * BM25 检索。
     *
     * @param query 用户原始查询文本
     * @param topK  返回的最大结果数
     * @return 按 BM25 分数降序排列的检索结果
     */
    public List<RetrievalResult> search(String query, int topK) {
        if (!indexReady || indexDir == null) {
            log.warn("[Bm25Retriever] 索引未就绪，返回空结果");
            return List.of();
        }

        List<RetrievalResult> results = new ArrayList<>();
        try (DirectoryReader reader = DirectoryReader.open(indexDir)) {
            IndexSearcher searcher = new IndexSearcher(reader);

            // 在 question 字段上搜索，Analyzer 自动处理中文分词
            QueryParser parser = new QueryParser("question", analyzer);
            Query luceneQuery = parser.parse(escapeQuery(query));

            TopDocs topDocs = searcher.search(luceneQuery, topK);
            // 归一化 BM25 分数
            float maxScore = topDocs.scoreDocs.length > 0
                    ? topDocs.scoreDocs[0].score : 1.0f;

            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                Document doc = searcher.doc(scoreDoc.doc);
                double normalizedScore = maxScore > 0
                        ? (double) (scoreDoc.score / maxScore) : 0.0;

                results.add(RetrievalResult.builder()
                        .kbId(doc.get("kbId"))
                        .question(doc.get("question"))
                        .answer(doc.get("answer"))
                        .domain(doc.get("domain"))
                        .category(doc.get("category"))
                        .sourceFile(doc.get("sourceFile"))
                        .score(normalizedScore)
                        .source(RetrievalResult.Source.BM25)
                        .build());
            }
        } catch (Exception e) {
            log.error("[Bm25Retriever] 检索失败: {}", e.getMessage());
        }
        return results;
    }

    /**
     * 检查索引是否已就绪。
     */
    public boolean isIndexReady() {
        return indexReady;
    }

    /**
     * 转义 Lucene 查询中的特殊字符，防止解析错误。
     */
    private String escapeQuery(String query) {
        // 转义 Lucene 特殊字符
        return query.replaceAll("([+\\-!(){}\\[\\]^\"~*?:\\\\]|&&|\\|\\|)", "\\\\$1");
    }
}
