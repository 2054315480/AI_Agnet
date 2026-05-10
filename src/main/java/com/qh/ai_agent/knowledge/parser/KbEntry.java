package com.qh.ai_agent.knowledge.parser;

import lombok.Builder;
import lombok.Data;

/**
 * 知识库条目的统一数据模型。
 * 无论原始文件是 MD/DOCX/PDF，解析后都归一化为该结构。
 */
@Data
@Builder
public class KbEntry {

    /** 知识库唯一编号，如 kb_qa_8a3f1b2c00 */
    private String kbId;

    /** 条目的提问文本 */
    private String question;

    /** 条目的答案文本（含模板变量 {{...}}） */
    private String answer;

    /** 所属行业：banking / telecom / ecommerce */
    private String domain;

    /** 业务类目，如 CARD / BILLING / RETURNS */
    private String category;

    /** 来源文件名 */
    private String sourceFile;

    /** 在源文件中的段落序号 */
    private int chunkIndex;

    /**
     * 获取用于向量化的文本（Q + A 拼接）。
     */
    public String toEmbeddingText() {
        return question + " " + answer;
    }

    /**
     * 获取用于 Lucene 索引的文本（只索引 question 以提高匹配精度）。
     */
    public String toIndexText() {
        return question;
    }
}
