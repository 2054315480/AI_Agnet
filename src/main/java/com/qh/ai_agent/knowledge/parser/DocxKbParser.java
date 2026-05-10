package com.qh.ai_agent.knowledge.parser;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * DOCX 知识库解析器。
 * 使用 Apache POI 读取 .docx 文件，按标题样式和 [条目编号] 标记切分 QA 对。
 */
@Slf4j
@Component
public class DocxKbParser implements KbParser {

    @Override
    public List<KbEntry> parse(File file, String domain) throws IOException {
        List<KbEntry> entries = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(file);
             XWPFDocument doc = new XWPFDocument(fis)) {

            String currentCategory = null;
            String currentQuestion = null;
            StringBuilder currentAnswer = new StringBuilder();
            String currentKbId = null;
            String filename = file.getName();

            for (IBodyElement element : doc.getBodyElements()) {
                if (!(element instanceof XWPFParagraph)) {
                    continue;
                }

                XWPFParagraph para = (XWPFParagraph) element;
                String text = para.getText().trim();
                if (text.isEmpty()) {
                    continue;
                }

                String styleId = para.getStyleID();
                boolean isHeading = styleId != null && styleId.matches("(?i)Heading\\d+");

                // 检测条目编号（可能单独一行或嵌入段落中）
                String kbId = KbEntryIdExtractor.extract(text);

                if (isHeading) {
                    // 保存上一个条目
                    if (currentQuestion != null && currentAnswer.length() > 0) {
                        entries.add(buildEntry(currentKbId, currentQuestion,
                                currentAnswer.toString(), domain, currentCategory, filename, entries.size()));
                    }

                    // Heading1 → 大类目，Heading2 → 子类目，Heading3+ → 问题
                    String level = styleId.replaceAll("(?i)Heading", "");
                    if ("1".equals(level)) {
                        // 一级标题：重置上下文
                        currentCategory = text;
                        currentQuestion = null;
                        currentAnswer = new StringBuilder();
                        currentKbId = null;
                    } else if ("2".equals(level)) {
                        currentCategory = text;
                        currentQuestion = null;
                        currentAnswer = new StringBuilder();
                        currentKbId = null;
                    } else {
                        // Heading3+ → 问题文本
                        currentQuestion = text.replaceFirst("^\\d+[.、．]\\s*", "").trim();
                        currentAnswer = new StringBuilder();
                        currentKbId = null;
                    }
                } else if (kbId != null) {
                    // 条目编号行 → 绑定到当前问题
                    if (currentKbId == null) {
                        currentKbId = kbId;
                    }
                    // 检查此行是否也包含 Q/A 内容
                    String remainingText = text.replaceAll("\\*?\\[条目编号[:：]\\s*`?\\s*kb_qa_[a-f0-9]{10}\\s*`?\\s*]\\*?", "").trim();
                    if (!remainingText.isEmpty() && currentQuestion != null) {
                        currentAnswer.append(remainingText).append("\n");
                    }
                } else if (currentQuestion != null) {
                    // 普通段落 → 答案内容
                    currentAnswer.append(text).append("\n");
                }
            }

            // 保存最后一个条目
            if (currentQuestion != null && currentAnswer.length() > 0) {
                entries.add(buildEntry(currentKbId, currentQuestion,
                        currentAnswer.toString(), domain, currentCategory, filename, entries.size()));
            }

            // 如果按标题样式没解析到条目，尝试全文正则匹配
            if (entries.isEmpty()) {
                entries = parseByPattern(textOf(doc), domain, filename);
            }
        }
        log.info("[DocxKbParser] {} → {} 条", file.getName(), entries.size());
        return entries;
    }

    /**
     * 降级方案：全文正则匹配 QA 对。
     * 适用于标题样式不规范的 DOCX 文件。
     */
    private List<KbEntry> parseByPattern(String fullText, String domain, String filename) {
        List<KbEntry> entries = new ArrayList<>();

        // 按 Q: 或 数字. 模式切分
        String[] sections = fullText.split("(?=(?:Q[：:]|\\d+[.、．]\\s*(?:我|请|如|怎|是|想|能|可)))");
        for (int i = 0; i < sections.length; i++) {
            String section = sections[i].trim();
            if (section.isEmpty()) {
                continue;
            }

            String kbId = KbEntryIdExtractor.extract(section);
            String question = extractQuestionFromText(section);
            String answer = extractAnswerFromText(section);

            if (question != null && !answer.isBlank()) {
                entries.add(KbEntry.builder()
                        .kbId(kbId)
                        .question(question)
                        .answer(answer.trim())
                        .domain(domain)
                        .sourceFile(filename)
                        .chunkIndex(i)
                        .build());
            }
        }
        return entries;
    }

    private String extractQuestionFromText(String text) {
        // 取第一行作为问题
        String firstLine = text.split("\n")[0].trim();
        return firstLine.replaceFirst("^\\d+[.、．]\\s*", "").trim();
    }

    private String extractAnswerFromText(String text) {
        // 取第一行之后的内容作为答案（排除条目编号行）
        String[] lines = text.split("\n");
        StringBuilder answer = new StringBuilder();
        boolean firstLineSkipped = false;
        for (String line : lines) {
            String trimmed = line.trim();
            if (!firstLineSkipped) {
                firstLineSkipped = true;
                continue;
            }
            if (KbEntryIdExtractor.extract(trimmed) != null) {
                continue;
            }
            if (!trimmed.isEmpty()) {
                answer.append(trimmed).append("\n");
            }
        }
        return answer.toString();
    }

    /**
     * 提取 DOCX 文档的纯文本内容。
     */
    private String textOf(XWPFDocument doc) {
        StringBuilder sb = new StringBuilder();
        for (XWPFParagraph para : doc.getParagraphs()) {
            String text = para.getText().trim();
            if (!text.isEmpty()) {
                sb.append(text).append("\n");
            }
        }
        return sb.toString();
    }

    private KbEntry buildEntry(String kbId, String question, String answer,
                                String domain, String category, String filename, int index) {
        return KbEntry.builder()
                .kbId(kbId)
                .question(question)
                .answer(answer.trim())
                .domain(domain)
                .category(category)
                .sourceFile(filename)
                .chunkIndex(index)
                .build();
    }

    @Override
    public String supportedExtension() {
        return "docx";
    }
}
