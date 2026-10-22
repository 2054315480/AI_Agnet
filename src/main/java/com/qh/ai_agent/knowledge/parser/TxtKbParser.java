package com.qh.ai_agent.knowledge.parser;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * TXT 知识库解析器。
 *
 * 规则：
 *   - 按空行分段；
 *   - 段落过短（<20 字）合并到上一段；
 *   - 单段过长（>1500 字）按句子切分；
 *   - UTF-8 优先，失败回退 GBK。
 */
@Slf4j
@Component
public class TxtKbParser implements KbParser {

    private static final int MIN_PARAGRAPH_LENGTH = 20;
    private static final int MAX_PARAGRAPH_LENGTH = 1500;

    @Override
    public List<KbEntry> parse(File file, String domain) throws IOException {
        List<KbEntry> entries = new ArrayList<>();
        String text = readText(file);
        List<String> paragraphs = mergeShortParagraphs(splitParagraphs(text));
        String tableName = stripExtension(file.getName());

        for (String paragraph : paragraphs) {
            for (String chunk : splitLongParagraph(paragraph)) {
                String answer = chunk.trim();
                if (answer.isEmpty()) {
                    continue;
                }
                String question = buildQuestion(tableName, answer);
                entries.add(KbEntry.builder()
                        .question(question)
                        .answer(answer)
                        .domain(domain)
                        .category(tableName)
                        .sourceFile(file.getName())
                        .chunkIndex(entries.size())
                        .build());
            }
        }
        log.info("[TxtKbParser] {} -> {} 条", file.getName(), entries.size());
        return entries;
    }

    private List<String> splitParagraphs(String text) {
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        String[] rawParagraphs = normalized.split("\\n[ \\t]*\\n+");
        List<String> paragraphs = new ArrayList<>();
        for (String raw : rawParagraphs) {
            String paragraph = raw.trim();
            if (!paragraph.isEmpty()) {
                paragraphs.add(paragraph);
            }
        }
        return paragraphs;
    }

    private List<String> mergeShortParagraphs(List<String> paragraphs) {
        List<String> merged = new ArrayList<>();
        for (String paragraph : paragraphs) {
            if (paragraph.length() < MIN_PARAGRAPH_LENGTH && !merged.isEmpty()) {
                int last = merged.size() - 1;
                merged.set(last, merged.get(last) + "\n" + paragraph);
            } else {
                merged.add(paragraph);
            }
        }
        return merged;
    }
    /**
     * 超长段落按句末标点切句，再重新拼成不超过 1500 字的块。
     */
    private List<String> splitLongParagraph(String paragraph) {
        List<String> chunks = new ArrayList<>();
        if (paragraph.length() <= MAX_PARAGRAPH_LENGTH) {
            chunks.add(paragraph);
            return chunks;
        }

        String[] sentences = paragraph.split("(?<=[。！？!?；;\\n])");
        StringBuilder current = new StringBuilder();
        for (String rawSentence : sentences) {
            String sentence = rawSentence.trim();
            if (sentence.isEmpty()) {
                continue;
            }
            if (sentence.length() > MAX_PARAGRAPH_LENGTH) {
                if (current.length() > 0) {
                    chunks.add(current.toString().trim());
                    current.setLength(0);
                }
                for (int start = 0; start < sentence.length(); start += MAX_PARAGRAPH_LENGTH) {
                    int end = Math.min(sentence.length(), start + MAX_PARAGRAPH_LENGTH);
                    chunks.add(sentence.substring(start, end));
                }
                continue;
            }
            if (current.length() > 0 && current.length() + sentence.length() > MAX_PARAGRAPH_LENGTH) {
                chunks.add(current.toString().trim());
                current.setLength(0);
            }
            current.append(sentence);
        }
        if (current.length() > 0) {
            chunks.add(current.toString().trim());
        }
        return chunks;
    }

    private String buildQuestion(String tableName, String answer) {
        String oneLine = answer.replaceAll("\\s+", " ").trim();
        int end = -1;
        for (char punctuation : new char[]{'。', '！', '？', '!', '?', '；', ';'}) {
            int index = oneLine.indexOf(punctuation);
            if (index >= 0 && (end < 0 || index < end)) {
                end = index;
            }
        }
        String body;
        if (end >= 0 && end + 1 <= 80) {
            body = oneLine.substring(0, end + 1);
        } else if (oneLine.length() > 60) {
            body = oneLine.substring(0, 60) + "...";
        } else {
            body = oneLine;
        }
        return tableName.isEmpty() ? body : tableName + " - " + body;
    }

    private String readText(File file) throws IOException {
        byte[] bytes = Files.readAllBytes(file.toPath());
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            log.info("[TxtKbParser] {} 非 UTF-8，回退 GBK 解码", file.getName());
            text = new String(bytes, Charset.forName("GBK"));
        }
        if (text.startsWith("\uFEFF")) {
            text = text.substring(1);
        }
        return text;
    }

    private String stripExtension(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    @Override
    public String supportedExtension() {
        return "txt";
    }
}