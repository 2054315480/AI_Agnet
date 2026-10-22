package com.qh.ai_agent.knowledge.parser;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;

/**
 * 知识库解析器工厂 — 根据文件扩展名分配合适的解析器。
 */
@Component
@RequiredArgsConstructor
public class KbParserFactory {

    private final MdKbParser mdKbParser;
    private final DocxKbParser docxKbParser;
    private final PdfKbParser pdfKbParser;
    private final XlsxKbParser xlsxKbParser;
    private final CsvKbParser csvKbParser;
    private final JsonKbParser jsonKbParser;
    private final TxtKbParser txtKbParser;

    private Map<String, KbParser> parserMap;

    /**
     * 根据文件扩展名获取解析器。
     *
     * @param filename 文件名（含扩展名）
     * @return 对应的解析器，不支持的类型返回 null
     */
    public KbParser getParser(String filename) {
        if (parserMap == null) {
            parserMap = Map.of(
                    "md", mdKbParser,
                    "docx", docxKbParser,
                    "pdf", pdfKbParser,
                    "xlsx", xlsxKbParser,
                    "csv", csvKbParser,
                    "json", jsonKbParser,
                    "txt", txtKbParser
            );
        }

        String ext = getExtension(filename).toLowerCase(Locale.ROOT);
        return parserMap.get(ext);
    }

    /**
     * 检查文件类型是否支持。
     */
    public boolean isSupported(String filename) {
        return getParser(filename) != null;
    }

    private String getExtension(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(dot + 1) : "";
    }
}