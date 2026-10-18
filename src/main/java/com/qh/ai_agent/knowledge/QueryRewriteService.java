package com.qh.ai_agent.knowledge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 确定性的查询改写服务（不依赖大模型）。
 *
 * <p>处理步骤：</p>
 * <ol>
 *   <li>全角字符转半角，合并空白，清理首尾标点；</li>
 *   <li>去掉「你好、请问、麻烦问一下」等客套前缀；</li>
 *   <li>读取 dictionaries/synonyms.json，保留原词并追加命中词的标准同义词；</li>
 *   <li>去掉句末无意义停用词。</li>
 * </ol>
 *
 * <p>该实现只做规则处理，不产生网络调用，失败时保留原查询，不影响检索主链路。</p>
 */
@Slf4j
@Service
public class QueryRewriteService {

    private static final String SYNONYM_RESOURCE = "dictionaries/synonyms.json";

    /** 常见客套前缀，按长度降序，优先匹配更长的表达。 */
    private static final List<String> POLITE_PREFIXES = List.of(
            "麻烦问一下", "麻烦请问一下", "想问一下", "想请问一下", "请问一下",
            "麻烦问下", "请问下", "请教一下", "想请教", "想问问", "问一下",
            "客服你好", "客服您好", "你好", "您好", "麻烦", "请问", "请教", "问下"
    ).stream()
            .sorted(Comparator.comparingInt(String::length).reversed())
            .toList();

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 保持 JSON 中的顺序，便于规则结果稳定、可复现。 */
    private final Map<String, String> synonymMap = new LinkedHashMap<>();

    private final Set<String> stopWords = new LinkedHashSet<>();

    @PostConstruct
    public void init() {
        try (InputStream inputStream = new ClassPathResource(SYNONYM_RESOURCE).getInputStream()) {
            JsonNode root = objectMapper.readTree(inputStream);

            JsonNode synonymsNode = root.get("synonyms");
            if (synonymsNode != null) {
                Iterator<Map.Entry<String, JsonNode>> fields = synonymsNode.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> entry = fields.next();
                    String alias = entry.getKey();
                    String canonical = entry.getValue().asText();
                    if (alias != null && !alias.isBlank() && canonical != null && !canonical.isBlank()) {
                        synonymMap.put(alias, canonical);
                    }
                }
            }

            JsonNode stopWordsNode = root.get("stopWords");
            if (stopWordsNode != null) {
                for (JsonNode word : stopWordsNode) {
                    String stopWord = word.asText();
                    if (stopWord != null && !stopWord.isBlank()) {
                        stopWords.add(stopWord);
                    }
                }
            }

            log.info("[QueryRewriteService] 同义词词典加载完成: synonyms={}, stopWords={}",
                    synonymMap.size(), stopWords.size());
        } catch (Exception e) {
            // 词典加载失败时仍可执行基础清洗，不能影响正常检索。
            log.warn("[QueryRewriteService] 词典加载失败，将仅执行基础查询清洗: {}", e.getMessage());
        }
    }
    /**
     * 执行查询改写。
     *
     * @param query 用户原始查询
     * @return 改写结果，包含原查询、改写后查询以及命中的同义词映射
     */
    public QueryRewriteResult rewrite(String query) {
        String originalQuery = query == null ? "" : query;
        String normalized = normalizeText(originalQuery);
        String baseQuery = removePolitePrefixes(normalized);
        baseQuery = removeTrailingStopWords(baseQuery);

        if (baseQuery.isBlank()) {
            // 例如用户只输入「请问？」，清洗后为空时保留原输入，避免把查询改没。
            baseQuery = originalQuery.trim();
        }

        List<String> matchedSynonyms = new ArrayList<>();
        List<String> expansions = expandSynonyms(baseQuery, matchedSynonyms);

        String rewrittenQuery = baseQuery;
        if (!expansions.isEmpty()) {
            rewrittenQuery = (baseQuery + " " + String.join(" ", expansions)).trim();
        }
        rewrittenQuery = rewrittenQuery.replaceAll("\\s+", " ").trim();

        QueryRewriteResult result = new QueryRewriteResult(
                originalQuery,
                rewrittenQuery,
                List.copyOf(matchedSynonyms)
        );
        log.debug("[QueryRewriteService] 查询改写: '{}' -> '{}', 命中同义词={}",
                originalQuery, rewrittenQuery, matchedSynonyms);
        return result;
    }

    /**
     * 全角转半角、空白归一化并清理首尾标点。
     */
    private String normalizeText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        StringBuilder halfWidth = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\u3000') {
                halfWidth.append(' ');
            } else if (c >= '\uFF01' && c <= '\uFF5E') {
                halfWidth.append((char) (c - 0xFEE0));
            } else {
                halfWidth.append(c);
            }
        }

        return stripEdgePunctuation(halfWidth.toString().replaceAll("\\s+", " ").trim());
    }

    /**
     * 循环剥离开头客套词；每次剥离后再次清理标点和空白。
     */
    private String removePolitePrefixes(String text) {
        String current = text == null ? "" : text.trim();
        boolean changed = true;

        while (changed && !current.isBlank()) {
            changed = false;
            for (String prefix : POLITE_PREFIXES) {
                if (current.startsWith(prefix)) {
                    String candidate = stripEdgePunctuation(current.substring(prefix.length()).trim());
                    if (!candidate.isBlank()) {
                        current = candidate;
                        changed = true;
                        break;
                    }
                }
            }
        }
        return current;
    }
    /**
     * 只移除句末停用词，避免破坏「怎么办」这类有实际语义的词组。
     */
    private String removeTrailingStopWords(String text) {
        String current = text == null ? "" : text.trim();
        List<String> sortedStopWords = stopWords.stream()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();

        boolean changed = true;
        while (changed && !current.isBlank()) {
            changed = false;
            for (String stopWord : sortedStopWords) {
                if (current.endsWith(stopWord) && current.length() > stopWord.length()) {
                    current = stripEdgePunctuation(
                            current.substring(0, current.length() - stopWord.length()).trim());
                    changed = true;
                    break;
                }
            }
        }
        return current;
    }

    /**
     * 不替换用户原词，而是把命中的标准同义词追加到查询末尾，兼顾原词召回和同义召回。
     */
    private List<String> expandSynonyms(String text, List<String> matchedSynonyms) {
        if (text == null || text.isBlank() || synonymMap.isEmpty()) {
            return List.of();
        }

        List<Map.Entry<String, String>> entries = synonymMap.entrySet().stream()
                .sorted(Comparator
                        .comparingInt((Map.Entry<String, String> e) -> e.getKey().length())
                        .reversed()
                        .thenComparing(Map.Entry::getKey))
                .toList();

        Set<String> expansions = new LinkedHashSet<>();
        for (Map.Entry<String, String> entry : entries) {
            String alias = entry.getKey();
            String canonical = entry.getValue();
            if (!containsAlias(text, alias)) {
                continue;
            }

            boolean canonicalAlreadyMatched = matchedSynonyms.stream().anyMatch(item -> {
                int arrowIndex = item.indexOf("->");
                return arrowIndex >= 0
                        && item.substring(arrowIndex + 2).equalsIgnoreCase(canonical);
            });
            if (!canonicalAlreadyMatched) {
                matchedSynonyms.add(alias + "->" + canonical);
            }
            if (!containsAlias(text, canonical) && expansions.stream()
                    .noneMatch(value -> value.equalsIgnoreCase(canonical))) {
                expansions.add(canonical);
            }
        }
        return new ArrayList<>(expansions);
    }

    /**
     * 中文别名按包含匹配；纯 ASCII 别名要求词边界，避免 ex 命中 text 之类的误匹配。
     */
    private boolean containsAlias(String text, String alias) {
        if (text == null || alias == null || alias.isBlank()) {
            return false;
        }
        if (!isAsciiAlias(alias)) {
            return text.contains(alias);
        }

        String lowerText = text.toLowerCase(Locale.ROOT);
        String lowerAlias = alias.toLowerCase(Locale.ROOT);
        int fromIndex = 0;
        while (fromIndex <= lowerText.length() - lowerAlias.length()) {
            int index = lowerText.indexOf(lowerAlias, fromIndex);
            if (index < 0) {
                return false;
            }
            int before = index - 1;
            int after = index + lowerAlias.length();
            boolean leftBoundary = before < 0 || !isAsciiWordChar(lowerText.charAt(before));
            boolean rightBoundary = after >= lowerText.length() || !isAsciiWordChar(lowerText.charAt(after));
            if (leftBoundary && rightBoundary) {
                return true;
            }
            fromIndex = index + 1;
        }
        return false;
    }

    private boolean isAsciiAlias(String text) {
        return text.chars().allMatch(c -> c < 128);
    }

    private boolean isAsciiWordChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
    }

    private String stripEdgePunctuation(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("^[\\s\\p{Punct}，。！？、：；…—·]+", "")
                .replaceAll("[\\s\\p{Punct}，。！？、：；…—·]+$", "")
                .trim();
    }

    /**
     * 查询改写结果。
     *
     * @param originalQuery   原始查询
     * @param rewrittenQuery  用于实际检索的查询
     * @param matchedSynonyms 命中的同义词，格式为「别名->标准词」
     */
    public record QueryRewriteResult(String originalQuery,
                                     String rewrittenQuery,
                                     List<String> matchedSynonyms) {
    }
}