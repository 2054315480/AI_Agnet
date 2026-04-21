package com.qh.ai_agent.rag.enricher;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

@Component
@Slf4j
public class DocumentMetadataEnricher {

    private static final Map<String, String> CATEGORY_KEYWORDS = new LinkedHashMap<>();

    static {
        CATEGORY_KEYWORDS.put("单身", "单身");
        CATEGORY_KEYWORDS.put("脱单", "单身");
        CATEGORY_KEYWORDS.put("相亲", "单身");
        CATEGORY_KEYWORDS.put("恋爱", "恋爱");
        CATEGORY_KEYWORDS.put("表白", "恋爱");
        CATEGORY_KEYWORDS.put("暧昧", "恋爱");
        CATEGORY_KEYWORDS.put("结婚", "婚姻");
        CATEGORY_KEYWORDS.put("婚姻", "婚姻");
        CATEGORY_KEYWORDS.put("夫妻", "婚姻");
        CATEGORY_KEYWORDS.put("离婚", "婚姻");
    }

    private static final Set<String> TAG_STOP_WORDS = Set.of(
            "的", "了", "是", "在", "我", "有", "和", "就", "不", "人", "都", "一", "一个",
            "上", "也", "很", "到", "说", "要", "去", "你", "会", "着", "没有", "看", "好",
            "自己", "这", "他", "她", "它", "那", "被", "从", "把", "让", "对", "而", "与",
            "可以", "能", "什么", "如何", "怎么", "为什么", "这个", "那个", "因为", "所以",
            "如果", "但是", "但", "而且", "还", "又", "或", "之", "其"
    );

    public List<Document> enrichDocuments(List<Document> documents) {
        for (int i = 0; i < documents.size(); i++) {
            Document doc = documents.get(i);
            enrichDocument(doc);
            if ((i + 1) % 5 == 0) {
                log.info("元信息丰富进度: {}/{}", i + 1, documents.size());
            }
        }
        log.info("元信息丰富完成，共处理 {} 个文档", documents.size());
        return documents;
    }

    public void enrichDocument(Document document) {
        Map<String, Object> metadata = document.getMetadata();

        // source_type
        if (!metadata.containsKey("source_type")) {
            String filename = (String) metadata.getOrDefault("filename", "");
            metadata.put("source_type", "MARKDOWN");
        }

        // category
        if (!metadata.containsKey("category")) {
            metadata.put("category", detectCategory(document.getText()));
        }

        // tags
        if (!metadata.containsKey("tags")) {
            metadata.put("tags", extractTags(document.getText()));
        }

        // load_date
        metadata.put("load_date", LocalDate.now().toString());

        // content_hash
        String hash = DigestUtils.md5DigestAsHex(document.getText().getBytes(StandardCharsets.UTF_8));
        metadata.put("content_hash", hash);

        // language
        metadata.put("language", detectLanguage(document.getText()));
    }

    private String detectCategory(String text) {
        if (text == null || text.isEmpty()) {
            return "通用";
        }
        for (Map.Entry<String, String> entry : CATEGORY_KEYWORDS.entrySet()) {
            if (text.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return "通用";
    }

    private List<String> extractTags(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        Map<String, Integer> freq = new HashMap<>();
        // 按标点符号和空格分词，提取2-4字的中文词组
        String[] segments = text.split("[\\s,\\u3002\\uff01\\uff1f\\u3001\\uff1b\\uff1a\\u201c\\u201d\\u2018\\u2019\\uff08\\uff09\\u3010\\u3011\\[\\]{}<>\\u300a\\u300b\\u00b7\\u2026\\u2014\\-\\|\\\\/]+");
        for (String seg : segments) {
            // 提取2-4字的连续中文子串
            for (int len = 2; len <= 4; len++) {
                for (int i = 0; i <= seg.length() - len; i++) {
                    String sub = seg.substring(i, i + len);
                    if (isAllChinese(sub) && !TAG_STOP_WORDS.contains(sub)) {
                        freq.merge(sub, 1, Integer::sum);
                    }
                }
            }
        }
        return freq.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .map(Map.Entry::getKey)
                .toList();
    }

    private boolean isAllChinese(String s) {
        for (char c : s.toCharArray()) {
            if (c < 0x4E00 || c > 0x9FFF) {
                return false;
            }
        }
        return true;
    }

    private String detectLanguage(String text) {
        if (text == null || text.isEmpty()) {
            return "unknown";
        }
        int chinese = 0, english = 0;
        for (char c : text.toCharArray()) {
            if ((c >= 0x4E00 && c <= 0x9FFF) || (c >= 0x3400 && c <= 0x4DBF)) {
                chinese++;
            } else if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
                english++;
            }
        }
        if (chinese > english) return "zh";
        if (english > chinese) return "en";
        return "mixed";
    }
}
