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
 * CSV 知识库解析器。
 *
 * 手写 RFC4180 状态机，支持：
 *   - 引号字段内的逗号、换行；
 *   - 双引号转义（""）；
 *   - 首行表头，按行切片；
 *   - UTF-8 优先，非法 UTF-8 时回退 GBK。
 */
@Slf4j
@Component
public class CsvKbParser implements KbParser {

    @Override
    public List<KbEntry> parse(File file, String domain) throws IOException {
        List<KbEntry> entries = new ArrayList<>();
        String content = readText(file);
        List<List<String>> rows = parseCsv(content);
        if (rows.isEmpty()) {
            return entries;
        }

        int headerIndex = firstNonEmptyRow(rows);
        if (headerIndex < 0) {
            return entries;
        }
        List<String> headers = rows.get(headerIndex);
        String tableName = stripExtension(file.getName());

        for (int r = headerIndex + 1; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            if (isBlankRow(row)) {
                continue;
            }
            if (isSameAsHeader(row, headers)) {
                continue;
            }

            StringBuilder answer = new StringBuilder();
            answer.append("表名: ").append(tableName).append("\n");
            int nonEmpty = 0;
            int columnCount = Math.max(row.size(), headers.size());
            for (int c = 0; c < columnCount; c++) {
                String value = c < row.size() ? trimToEmpty(row.get(c)) : "";
                if (value.isEmpty()) {
                    continue;
                }
                nonEmpty++;
                String header = c < headers.size() ? trimToEmpty(headers.get(c)) : "";
                if (header.isEmpty()) {
                    header = "列" + columnName(c);
                }
                answer.append(header).append(": ").append(value).append("\n");
            }
            if (nonEmpty == 0) {
                continue;
            }

            String firstValue = firstNonEmptyValue(row);
            if (firstValue.isEmpty()) {
                continue;
            }
            String question = tableName.isEmpty() ? firstValue : tableName + " - " + firstValue;

            entries.add(KbEntry.builder()
                    .question(question)
                    .answer(answer.toString().trim())
                    .domain(domain)
                    .category(tableName)
                    .sourceFile(file.getName())
                    .chunkIndex(entries.size())
                    .build());
        }
        log.info("[CsvKbParser] {} -> {} 条", file.getName(), entries.size());
        return entries;
    }
    /**
     * RFC4180 状态机解析：引号内的逗号和换行都属于字段内容。
     */
    private List<List<String>> parseCsv(String content) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < content.length(); i++) {
            char ch = content.charAt(i);
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < content.length() && content.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(ch);
                }
            } else {
                if (ch == '"' && field.length() == 0) {
                    inQuotes = true;
                } else if (ch == ',') {
                    row.add(field.toString());
                    field.setLength(0);
                } else if (ch == '\r' || ch == '\n') {
                    if (ch == '\r' && i + 1 < content.length() && content.charAt(i + 1) == '\n') {
                        i++;
                    }
                    row.add(field.toString());
                    field.setLength(0);
                    rows.add(row);
                    row = new ArrayList<>();
                } else {
                    field.append(ch);
                }
            }
        }
        if (field.length() > 0 || !row.isEmpty()) {
            row.add(field.toString());
            rows.add(row);
        }
        return rows;
    }
    /**
     * 优先按 UTF-8 严格解码；遇到非法字节时回退 GBK。
     */
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
            log.info("[CsvKbParser] {} 非 UTF-8，回退 GBK 解码", file.getName());
            text = new String(bytes, Charset.forName("GBK"));
        }
        if (text.startsWith("\uFEFF")) {
            text = text.substring(1);
        }
        return text;
    }

    private int firstNonEmptyRow(List<List<String>> rows) {
        for (int i = 0; i < rows.size(); i++) {
            if (!isBlankRow(rows.get(i))) {
                return i;
            }
        }
        return -1;
    }

    private boolean isBlankRow(List<String> row) {
        for (String value : row) {
            if (!trimToEmpty(value).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private boolean isSameAsHeader(List<String> row, List<String> headers) {
        int nonEmpty = 0;
        int matched = 0;
        int count = Math.min(row.size(), headers.size());
        for (int c = 0; c < count; c++) {
            String value = trimToEmpty(row.get(c));
            if (value.isEmpty()) {
                continue;
            }
            nonEmpty++;
            if (value.equals(trimToEmpty(headers.get(c)))) {
                matched++;
            }
        }
        return nonEmpty > 0 && matched == nonEmpty;
    }

    private String firstNonEmptyValue(List<String> row) {
        for (String value : row) {
            String trimmed = trimToEmpty(value);
            if (!trimmed.isEmpty()) {
                return trimmed;
            }
        }
        return "";
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String stripExtension(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    private String columnName(int index) {
        StringBuilder name = new StringBuilder();
        int value = index;
        do {
            name.insert(0, (char) ('A' + value % 26));
            value = value / 26 - 1;
        } while (value >= 0);
        return name.toString();
    }

    @Override
    public String supportedExtension() {
        return "csv";
    }
}