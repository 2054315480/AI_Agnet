package com.qh.ai_agent.knowledge.parser;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * PDF 知识库解析器。
 * 使用 PDFBox 2.x 提取文本，正则切分 QA 对并提取 [条目编号]。
 *
 * 核心难点（比赛雷点 7）：PDF 解析时反引号、星号、方括号可能变形，
 * 因此用正则 kb_qa_[a-f0-9]{10} 直接匹配 ID 模式，不依赖格式标记。
 */
@Slf4j
@Component
public class PdfKbParser implements KbParser {

    /** QA 切分模式：匹配 "### N." 或 "Q:" 或纯数字编号开头的问题 */
    private static final Pattern QA_SPLIT_PATTERN =
            Pattern.compile("(?=(?:###\\s*\\d+\\.)|(?:Q[：:])|(?:^\\d+[.、．]\\s*(?:我|请|如|怎|是|想|能|可|您)))",
                    Pattern.MULTILINE);

    /** 问题开头标记：提取 "### N. xxx" 或 "Q: xxx" 中的问题文本 */
    private static final Pattern QUESTION_PREFIX =
            Pattern.compile("^(?:###\\s*\\d+\\.\\s*)|(?:Q[：:]\\s*)");

    @Override
    public List<KbEntry> parse(File file, String domain) throws IOException {
        List<KbEntry> entries = new ArrayList<>();
        String filename = file.getName();

        try (PDDocument document = PDDocument.load(file)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setLineSeparator("\n");
            String fullText = stripper.getText(document);

            if (fullText.isBlank()) {
                log.warn("[PdfKbParser] {} PDF 文本为空", filename);
                return entries;
            }

            // 提取一级标题作为默认类目
            String defaultCategory = extractDefaultCategory(fullText);

            // 按 QA 边界切分
            String[] sections = QA_SPLIT_PATTERN.split(fullText);
            for (int i = 0; i < sections.length; i++) {
                String section = sections[i].trim();
                if (section.isEmpty()) {
                    continue;
                }

                String kbId = KbEntryIdExtractor.extract(section);
                if (kbId == null) {
                    // 无条目编号的段落 → 可能是纯文本段落，跳过
                    continue;
                }

                String question = extractQuestion(section);
                String answer = extractAnswer(section);

                if (question != null && answer != null && !answer.isBlank()) {
                    entries.add(KbEntry.builder()
                            .kbId(kbId)
                            .question(question)
                            .answer(answer.trim())
                            .domain(domain)
                            .category(defaultCategory)
                            .sourceFile(filename)
                            .chunkIndex(i)
                            .build());
                }
            }
        }
        log.info("[PdfKbParser] {} → {} 条", filename, entries.size());
        return entries;
    }

    /**
     * 提取默认类目（从一级标题或文件名推断）。
     */
    private String extractDefaultCategory(String text) {
        for (String line : text.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("# ") && !trimmed.startsWith("## ")) {
                return trimmed.substring(2).trim();
            }
        }
        return null;
    }

    /**
     * 从段落中提取问题文本。
     * 取第一行有效文本，去除编号前缀。
     */
    private String extractQuestion(String section) {
        for (String line : section.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            // 跳过元信息行
            if (trimmed.startsWith("> ") || trimmed.startsWith("*[条目编号")) {
                continue;
            }
            // 去除编号前缀
            return QUESTION_PREFIX.matcher(trimmed).replaceFirst("").trim();
        }
        return null;
    }

    /**
     * 从段落中提取答案文本。
     * 跳过第一行（问题）和条目编号行。
     */
    private String extractAnswer(String section) {
        String[] lines = section.split("\n");
        StringBuilder answer = new StringBuilder();
        boolean firstLineSkipped = false;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            // 跳过条目编号行
            if (KbEntryIdExtractor.extract(trimmed) != null) {
                continue;
            }

            // 跳过元信息行
            if (trimmed.startsWith("> ") || trimmed.startsWith("# ")) {
                continue;
            }

            // 跳过第一行（问题）
            if (!firstLineSkipped) {
                firstLineSkipped = true;
                if (QUESTION_PREFIX.matcher(trimmed).find() ||
                        trimmed.matches("^\\d+[.、．].*")) {
                    continue;
                }
            }

            answer.append(trimmed).append("\n");
        }
        return answer.toString();
    }

    @Override
    public String supportedExtension() {
        return "pdf";
    }
}
