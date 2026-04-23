package com.qh.ai_agent.knowledge;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 知识库加载器 — 从 classpath:knowledge/ 目录读取 Markdown 文件
 *
 * 目录结构：
 *   knowledge/faq/      — FAQ 文档（按 ## 标题分块，每块一个 Q&A）
 *   knowledge/product/   — 产品文档（每文件一个产品）
 *   knowledge/policy/    — 政策文档（每文件一个政策）
 *
 * 替换指引：
 *   赛题提供真实数据后，只需替换 knowledge/ 目录下的 Markdown 文件即可。
 *   文件格式保持不变：FAQ 用 ## 分块，产品/政策用 # 标题。
 */
@Slf4j
@Component
public class KnowledgeBaseLoader {

    private final ResourcePatternResolver resolver;

    public KnowledgeBaseLoader(ResourcePatternResolver resolver) {
        this.resolver = resolver;
    }

    /**
     * 加载所有知识库文档
     */
    public List<Document> loadAll() {
        List<Document> docs = new ArrayList<>();
        docs.addAll(loadFromDirectory("classpath:knowledge/faq/*.md", "FAQ"));
        docs.addAll(loadFromDirectory("classpath:knowledge/product/*.md", "PRODUCT"));
        docs.addAll(loadFromDirectory("classpath:knowledge/policy/*.md", "POLICY"));
        log.info("[KnowledgeBaseLoader] 加载完成，共 {} 条文档", docs.size());
        return docs;
    }

    /**
     * 从指定目录加载 Markdown 文件
     */
    private List<Document> loadFromDirectory(String locationPattern, String docType) {
        List<Document> docs = new ArrayList<>();
        try {
            Resource[] resources = resolver.getResources(locationPattern);
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename == null || !filename.endsWith(".md")) continue;

                String categoryName = filename.replace(".md", "");
                String content = readResource(resource);

                if (content.isBlank()) continue;

                if ("FAQ".equals(docType)) {
                    docs.addAll(parseFaqDocument(content, categoryName, docs.size()));
                } else {
                    docs.add(parseSingleDocument(content, docType, categoryName, docs.size()));
                }
            }
        } catch (IOException e) {
            log.warn("[KnowledgeBaseLoader] 加载目录失败: {} — {}", locationPattern, e.getMessage());
        }
        return docs;
    }

    /**
     * 解析 FAQ 文档 — 按 ## 标题分块，每块生成一个 Document
     * content 格式："Q: {question}\nA: {answer}"，与原硬编码版本一致
     */
    private List<Document> parseFaqDocument(String content, String categoryName, int startIndex) {
        List<Document> docs = new ArrayList<>();
        String[] lines = content.split("\n");

        String currentQuestion = null;
        StringBuilder currentAnswer = new StringBuilder();

        for (String line : lines) {
            String trimmed = line.trim();
            // 跳过文件级标题（# 开头但不是 ##）
            if (trimmed.startsWith("# ") && !trimmed.startsWith("## ")) {
                continue;
            }
            if (trimmed.startsWith("## ")) {
                // 保存上一个 Q&A 块
                if (currentQuestion != null && !currentAnswer.isEmpty()) {
                    docs.add(buildFaqDocument(currentQuestion, currentAnswer.toString().trim(),
                            categoryName, startIndex + docs.size()));
                }
                currentQuestion = trimmed.substring(3).trim();
                currentAnswer = new StringBuilder();
            } else if (currentQuestion != null) {
                currentAnswer.append(line).append("\n");
            }
        }
        // 最后一个块
        if (currentQuestion != null && !currentAnswer.isEmpty()) {
            docs.add(buildFaqDocument(currentQuestion, currentAnswer.toString().trim(),
                    categoryName, startIndex + docs.size()));
        }

        return docs;
    }

    private Document buildFaqDocument(String question, String answer, String category, int chunkIndex) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", "FAQ-" + category);
        metadata.put("doc_type", "FAQ");
        metadata.put("category", category);
        metadata.put("chunk_index", chunkIndex);
        metadata.put("question", question);

        String docContent = "Q: " + question + "\nA: " + answer;
        return new Document(docContent, metadata);
    }

    /**
     * 解析产品/政策文档 — 每个文件生成一个 Document
     * 从 # 标题提取名称，全文作为 content
     */
    private Document parseSingleDocument(String content, String docType, String categoryName, int chunkIndex) {
        String title = extractTitle(content);
        String sourcePrefix = "PRODUCT".equals(docType) ? "产品文档" : "政策文档";
        String categoryLabel = "PRODUCT".equals(docType) ? "产品文档" : "政策文档";

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", sourcePrefix + "-" + (title != null ? title : categoryName));
        metadata.put("doc_type", docType);
        metadata.put("category", categoryLabel);
        metadata.put("chunk_index", chunkIndex);

        if ("PRODUCT".equals(docType) && title != null) {
            metadata.put("product_name", title);
        }

        return new Document(content, metadata);
    }

    /**
     * 从 Markdown 内容提取 # 一级标题文本
     */
    private String extractTitle(String content) {
        for (String line : content.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("# ") && !trimmed.startsWith("## ")) {
                return trimmed.substring(2).trim();
            }
        }
        return null;
    }

    /**
     * 读取 Resource 文件内容
     */
    private String readResource(Resource resource) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }
}
