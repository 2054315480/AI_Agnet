package com.qh.ai_agent.knowledge.retriever;

import com.qh.ai_agent.knowledge.parser.KbEntry;
import lombok.Builder;
import lombok.Data;

/**
 * 检索结果统一模型。
 * 无论来自 PgVector 还是 BM25，都包装为此结构。
 */
@Data
@Builder
public class RetrievalResult implements Comparable<RetrievalResult> {

    /** 知识库条目编号 */
    private String kbId;

    /** 问题文本 */
    private String question;

    /** 答案文本 */
    private String answer;

    /** 所属行业 */
    private String domain;

    /** 业务类目 */
    private String category;

    /** 来源文件名 */
    private String sourceFile;

    /** 检索相关性分数（0~1 或 BM25 原始分归一化后） */
    private double score;

    /** 检索来源 */
    private Source source;

    public enum Source {
        BM25, VECTOR, HYBRID
    }

    @Override
    public int compareTo(RetrievalResult other) {
        return Double.compare(other.score, this.score); // 降序
    }

    /**
     * 从 KbEntry 创建检索结果。
     */
    public static RetrievalResult from(KbEntry entry, double score, Source source) {
        return RetrievalResult.builder()
                .kbId(entry.getKbId())
                .question(entry.getQuestion())
                .answer(entry.getAnswer())
                .domain(entry.getDomain())
                .category(entry.getCategory())
                .sourceFile(entry.getSourceFile())
                .score(score)
                .source(source)
                .build();
    }
}
