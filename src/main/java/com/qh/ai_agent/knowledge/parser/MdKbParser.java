package com.qh.ai_agent.knowledge.parser;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Markdown 知识库解析器。
 *
 * 支持的格式（比赛 KB 常见结构）：
 *   ## 类目名称
 *   ### N. 问题文本
 *   答案文本...
 *   *[条目编号:`kb_qa_xxx`]*
 *   ---
 *
 * 也兼容现有项目的 FAQ 格式：
 *   ## 问题标题
 *   答案内容...
 */
@Slf4j
@Component
public class MdKbParser implements KbParser {

    @Override
    public List<KbEntry> parse(File file, String domain) throws IOException {
        String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        String filename = file.getName();

        // 判断是否为比赛格式（含 ### N. 标题模式）
        if (content.contains("### ") && content.contains("[条目编号")) {
            return parseCompetitionFormat(content, domain, filename);
        }
        // 回退：按 ## 标题切分的简单格式
        return parseSimpleFormat(content, domain, filename);
    }

    /**
     * 解析比赛标准格式：### N. question → answer → [条目编号]
     */
    private List<KbEntry> parseCompetitionFormat(String content, String domain, String filename) {
        List<KbEntry> entries = new ArrayList<>();
        String[] lines = content.split("\n");

        String currentCategory = extractFirstH2Title(content);
        String currentQuestion = null;
        StringBuilder currentAnswer = new StringBuilder();
        String currentKbId = null;
        boolean inAnswer = false;

        for (String line : lines) {
            String trimmed = line.trim();

            // 跳过文件级标题
            if (trimmed.startsWith("# ") && !trimmed.startsWith("## ")) {
                continue;
            }

            // ## 二级标题 → 类目名
            if (trimmed.startsWith("## ") && !trimmed.startsWith("### ")) {
                // 保存上一个条目
                if (currentQuestion != null && currentAnswer.length() > 0) {
                    entries.add(buildEntry(currentKbId, currentQuestion,
                            currentAnswer.toString(), domain, currentCategory, filename, entries.size()));
                }
                currentCategory = trimmed.substring(3).trim().replaceAll("\\*\\*", "").trim();
                currentQuestion = null;
                currentAnswer = new StringBuilder();
                currentKbId = null;
                inAnswer = false;
                continue;
            }

            // ### 三级标题 → 问题
            if (trimmed.startsWith("### ")) {
                if (currentQuestion != null && currentAnswer.length() > 0) {
                    entries.add(buildEntry(currentKbId, currentQuestion,
                            currentAnswer.toString(), domain, currentCategory, filename, entries.size()));
                }
                // 去掉 "### N. " 前缀，提取纯问题文本
                currentQuestion = trimmed.substring(4).trim()
                        .replaceFirst("^\\d+[.、．]\\s*", "").trim();
                currentAnswer = new StringBuilder();
                currentKbId = null;
                inAnswer = true;
                continue;
            }

            // 检测条目编号
            if (inAnswer) {
                String kbId = KbEntryIdExtractor.extract(trimmed);
                if (kbId != null) {
                    currentKbId = kbId;
                    continue; // 编号行不加入答案文本
                }

                // 跳过分隔线
                if (trimmed.equals("---") || trimmed.equals("***") || trimmed.equals("___")) {
                    continue;
                }

                // 跳过元信息行
                if (trimmed.startsWith("> ")) {
                    continue;
                }

                // 积累答案文本
                if (!trimmed.isEmpty()) {
                    currentAnswer.append(trimmed).append("\n");
                }
            }
        }

        // 保存最后一个条目
        if (currentQuestion != null && currentAnswer.length() > 0) {
            entries.add(buildEntry(currentKbId, currentQuestion,
                    currentAnswer.toString(), domain, currentCategory, filename, entries.size()));
        }

        return entries;
    }

    /**
     * 解析简单 ## 标题格式（现有知识库）
     */
    private List<KbEntry> parseSimpleFormat(String content, String domain, String filename) {
        List<KbEntry> entries = new ArrayList<>();
        String[] lines = content.split("\n");

        String currentQuestion = null;
        StringBuilder currentAnswer = new StringBuilder();

        for (String line : lines) {
            String trimmed = line.trim();

            if (trimmed.startsWith("# ") && !trimmed.startsWith("## ")) {
                continue;
            }

            if (trimmed.startsWith("## ")) {
                if (currentQuestion != null && currentAnswer.length() > 0) {
                    String kbId = KbEntryIdExtractor.extract(currentAnswer.toString());
                    entries.add(buildEntry(kbId, currentQuestion,
                            currentAnswer.toString(), domain, null, filename, entries.size()));
                }
                currentQuestion = trimmed.substring(3).trim();
                currentAnswer = new StringBuilder();
            } else if (currentQuestion != null) {
                currentAnswer.append(line).append("\n");
            }
        }

        if (currentQuestion != null && currentAnswer.length() > 0) {
            String kbId = KbEntryIdExtractor.extract(currentAnswer.toString());
            entries.add(buildEntry(kbId, currentQuestion,
                    currentAnswer.toString(), domain, null, filename, entries.size()));
        }

        return entries;
    }

    /**
     * 提取第一个 ## 标题作为默认类目
     */
    private String extractFirstH2Title(String content) {
        for (String line : content.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("## ") && !trimmed.startsWith("### ")) {
                return trimmed.substring(3).trim().replaceAll("\\*\\*", "").trim();
            }
        }
        return null;
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
        return "md";
    }
}
