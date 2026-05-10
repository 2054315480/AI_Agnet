package com.qh.ai_agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 批量 RAG 知识库检索测试结果。
 * 注意：matchedCount / avgTopRelevance / avgElapsedMs 是计算字段，
 * 必须用 @JsonProperty 标注，否则 Jackson 序列化 record 时不会包含。
 */
public record BatchTestResult(
        String fileName,
        int totalQuestions,
        long elapsedMs,
        List<QuestionTestResult> results
) {
    public record QuestionTestResult(
            String question,
            String expectedAnswer,
            String expectedKbId,
            String domain,
            String category,
            List<SearchHit> searchHits,
            boolean matched,
            double topRelevance,
            long elapsedMs
    ) {}

    public record SearchHit(
            String content,
            String source,
            String kbId,
            String docType,
            String category,
            double relevance
    ) {}

    @JsonProperty
    public int matchedCount() {
        return (int) results.stream().filter(QuestionTestResult::matched).count();
    }

    @JsonProperty
    public double avgTopRelevance() {
        return results.stream()
                .mapToDouble(QuestionTestResult::topRelevance)
                .average()
                .orElse(0.0);
    }

    @JsonProperty
    public double avgElapsedMs() {
        return results.stream()
                .mapToLong(QuestionTestResult::elapsedMs)
                .average()
                .orElse(0.0);
    }
}
