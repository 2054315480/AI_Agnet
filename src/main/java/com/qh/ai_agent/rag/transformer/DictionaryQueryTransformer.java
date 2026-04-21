package com.qh.ai_agent.rag.transformer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.*;

@Component
@Slf4j
public class DictionaryQueryTransformer implements QueryTransformer {

    private final Map<String, String> synonymMap = new LinkedHashMap<>();
    private final Set<String> stopWords = new HashSet<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        try {
            ClassPathResource resource = new ClassPathResource("dictionaries/synonyms.json");
            try (InputStream is = resource.getInputStream()) {
                JsonNode root = objectMapper.readTree(is);

                JsonNode synonymsNode = root.get("synonyms");
                if (synonymsNode != null) {
                    Iterator<Map.Entry<String, JsonNode>> fields = synonymsNode.fields();
                    while (fields.hasNext()) {
                        Map.Entry<String, JsonNode> entry = fields.next();
                        synonymMap.put(entry.getKey(), entry.getValue().asText());
                    }
                }

                JsonNode stopWordsNode = root.get("stopWords");
                if (stopWordsNode != null) {
                    for (JsonNode word : stopWordsNode) {
                        stopWords.add(word.asText());
                    }
                }
            }
            log.info("字典查询转换器初始化完成，同义词 {} 条，停用词 {} 个", synonymMap.size(), stopWords.size());
        } catch (Exception e) {
            log.warn("加载字典文件失败，使用空字典: {}", e.getMessage());
        }
    }

    @Override
    public Query transform(Query query) {
        String originalText = query.text();
        String transformed = normalizeQuery(originalText);
        transformed = expandSynonyms(transformed);
        transformed = removeStopWords(transformed);

        // 如果没有变化，直接返回原始 query（保留 history 和 context）
        if (transformed.equals(originalText.trim())) {
            log.debug("查询无变化，保持原样: {}", originalText);
            return query;
        }

        log.info("查询转换: [{}] -> [{}]", originalText, transformed);
        return query.mutate()
                .text(transformed)
                .build();
    }

    private String normalizeQuery(String text) {
        if (text == null) return "";
        // 全角转半角
        String result = text
                .replace("？", "?")
                .replace("！", "!")
                .replace("，", ",")
                .replace("。", ".")
                .replace("：", ":")
                .replace("；", ";");
        // 多空格合并
        result = result.replaceAll("\\s+", " ");
        return result.trim();
    }

    private String expandSynonyms(String text) {
        String result = text;
        // 按长度降序排列，优先匹配长词
        List<String> keys = synonymMap.keySet().stream()
                .sorted((a, b) -> b.length() - a.length())
                .toList();

        for (String key : keys) {
            if (result.contains(key)) {
                String replacement = synonymMap.get(key);
                result = result.replace(key, replacement);
            }
        }
        return result;
    }

    private String removeStopWords(String text) {
        String result = text;
        for (String stopWord : stopWords) {
            // 只去除句末的停用词或独立出现的停用词
            if (result.endsWith(stopWord)) {
                result = result.substring(0, result.length() - stopWord.length()).trim();
            }
        }
        return result.trim();
    }
}
