package com.qh.ai_agent.knowledge.parser;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 统一知识库加载器。
 * 遍历知识库根目录下的行业子目录，根据文件扩展名分发给对应解析器，汇总所有 KbEntry。
 *
 * 目录结构：
 *   knowledge_base/
 *   ├── 银行/  -> domain=banking
 *   ├── 电信/  -> domain=telecom
 *   └── 电商/  -> domain=ecommerce
 *
 * 加载完成后统一做三件事：
 *   1. 清洗模板占位符（{{...}} / ${...} / <%= %> / [TODO] / [占位]）；
 *   2. 对 (question + answer) 做归一化 SHA-256 内容去重；
 *   3. 按文件名/目录名过滤面试、题库、测试副本等非业务素材。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UnifiedKbLoader {

    private static final Pattern MUSTACHE_PLACEHOLDER = Pattern.compile("\\{\\{[\\s\\S]*?}}");
    private static final Pattern DOLLAR_PLACEHOLDER = Pattern.compile("\\$\\{[^}]*}");
    private static final Pattern JSP_PLACEHOLDER = Pattern.compile("<%=[\\s\\S]*?%>");
    private static final Pattern SQUARE_PLACEHOLDER = Pattern.compile(
            "(?i)[\\[【]\\s*(?:TODO|TBD|placeholder|占位符?|待补充|待填写)[^\\]】]*[\\]】]");
    private static final Pattern MULTI_SPACE = Pattern.compile("[ \\t]{2,}");
    private static final Pattern TRAILING_SPACE = Pattern.compile("(?m)[ \\t]+$");
    private static final Pattern MULTI_BLANK_LINE = Pattern.compile("\\n{3,}");

    private final KbParserFactory parserFactory;

    /** 比赛知识库根目录，默认 data/data/knowledge_base */
    @Value("${competition.kb.dir:./data/data/knowledge_base}")
    private String kbRootDir;

    /** 排除素材的正则，多个正则用逗号分隔 */
    @Value("${cs.kb.exclude-patterns:面试|题库|test_ascii|测试语料说明}")
    private String excludePatterns;

    /** 加载结果缓存 */
    private List<KbEntry> cachedEntries;

    /**
     * 加载全部知识库文档。
     *
     * @return 所有解析出的 KbEntry 列表
     */
    public List<KbEntry> loadAll() {
        if (cachedEntries != null) {
            return cachedEntries;
        }

        List<KbEntry> allEntries = new ArrayList<>();
        File root = new File(kbRootDir);

        if (!root.exists() || !root.isDirectory()) {
            log.warn("[UnifiedKbLoader] 知识库根目录不存在: {}", root.getAbsolutePath());
            return allEntries;
        }

        File[] domainDirs = root.listFiles(File::isDirectory);
        if (domainDirs == null) {
            return allEntries;
        }

        for (File domainDir : domainDirs) {
            String domain = mapDomain(domainDir.getName());
            File[] docFiles = domainDir.listFiles(
                    (dir, name) -> isSupportedFile(name));
            if (docFiles == null) {
                continue;
            }

            for (File docFile : docFiles) {
                if (isExcluded(docFile, root)) {
                    log.info("[UnifiedKbLoader] 跳过非业务素材: {}", docFile.getName());
                    continue;
                }
                try {
                    KbParser parser = parserFactory.getParser(docFile.getName());
                    if (parser == null) {
                        log.warn("[UnifiedKbLoader] 不支持的文件类型: {}", docFile.getName());
                        continue;
                    }
                    List<KbEntry> entries = parser.parse(docFile, domain);
                    allEntries.addAll(entries);
                    log.info("[UnifiedKbLoader] {} -> {} 条 (domain={})",
                            docFile.getName(), entries.size(), domain);
                } catch (IOException e) {
                    log.error("[UnifiedKbLoader] 解析失败: {}", docFile.getName(), e);
                }
            }
        }

        List<KbEntry> result = splitLongEntries(cleanAndDedup(allEntries));
        log.info("[UnifiedKbLoader] 加载完成: 总计 {} 条 (来自 {})", result.size(), kbRootDir);
        cachedEntries = result;
        return result;
    }
    /** 超过该长度的条目会被切分成多块，提升检索粒度（也避免单块过长稀释语义） */
    private static final int MAX_ANSWER_CHARS = 200;
    private static final int CHUNK_ANSWER_CHARS = 180;

    /**
     * 长条目切分：answer 超过 200 字时按句子边界切成 180 字左右的块，
     * 每块保留相同 question、kbId 追加 _pN 后缀，便于精确定位与去重。
     */
    private List<KbEntry> splitLongEntries(List<KbEntry> entries) {
        List<KbEntry> out = new ArrayList<>();
        int splitCount = 0;
        for (KbEntry entry : entries) {
            String answer = entry.getAnswer() == null ? "" : entry.getAnswer();
            if (answer.length() <= MAX_ANSWER_CHARS) {
                out.add(entry);
                continue;
            }
            List<String> segments = splitBySentence(answer, CHUNK_ANSWER_CHARS);
            if (segments.size() <= 1) {
                out.add(entry);
                continue;
            }
            // kbId 可能为空：直接拼 "_pN" 会让多条变成 "null_p1"，撞上向量表唯一索引
            String baseKbId = entry.getKbId();
            if (baseKbId == null || baseKbId.isBlank()) {
                baseKbId = "kb_" + Integer.toHexString(
                        (entry.getQuestion() + "|" + entry.getSourceFile()).hashCode());
            }
            for (int i = 0; i < segments.size(); i++) {
                out.add(KbEntry.builder()
                        .kbId(baseKbId + "_p" + (i + 1))
                        .question(entry.getQuestion())
                        .answer(segments.get(i))
                        .domain(entry.getDomain())
                        .category(entry.getCategory())
                        .sourceFile(entry.getSourceFile())
                        .chunkIndex(entry.getChunkIndex() * 100 + i)
                        .build());
            }
            splitCount++;
        }
        if (splitCount > 0) {
            log.info("[UnifiedKbLoader] 长条目切分: {} 条被拆分为更小块，总条数 {} -> {}", splitCount, entries.size(), out.size());
        }
        return out;
    }

    /** 按句号/问号/叹号/换行切分，尽量在语义边界断开 */
    private List<String> splitBySentence(String text, int chunkSize) {
        List<String> segments = new ArrayList<>();
        String[] sentences = text.split("(?<=[。！？!?\\n])");
        StringBuilder current = new StringBuilder();
        for (String sentence : sentences) {
            if (current.length() + sentence.length() > chunkSize && current.length() > 0) {
                segments.add(current.toString().trim());
                current.setLength(0);
            }
            current.append(sentence);
        }
        if (current.length() > 0) {
            segments.add(current.toString().trim());
        }
        return segments;
    }

    /**
     * 清理占位符 + 丢弃过短条目 + 内容去重。
     */
    private List<KbEntry> cleanAndDedup(List<KbEntry> entries) {
        List<KbEntry> cleaned = new ArrayList<>();
        int discarded = 0;
        for (KbEntry entry : entries) {
            entry.setQuestion(cleanPlaceholders(entry.getQuestion()));
            entry.setAnswer(cleanPlaceholders(entry.getAnswer()));
            String combined = (entry.getQuestion() + " " + entry.getAnswer()).trim();
            if (combined.length() < 10 || entry.getAnswer().isBlank()) {
                discarded++;
                continue;
            }
            // 新解析器（xlsx/csv/json/txt）可能不生成 kbId：这里统一补一个确定性 ID，
            // 否则向量表的唯一索引对 NULL 不生效，引用出处也会缺失
            if (entry.getKbId() == null || entry.getKbId().isBlank()) {
                entry.setKbId("kb_" + sha256(normalizeForDedup(entry.getQuestion() + entry.getAnswer()))
                        .substring(0, 12));
            }
            cleaned.add(entry);
        }

        Set<String> seen = new HashSet<>();
        List<KbEntry> result = new ArrayList<>();
        int duplicate = 0;
        for (KbEntry entry : cleaned) {
            String normalized = normalizeForDedup(entry.getQuestion() + entry.getAnswer());
            String fingerprint = sha256(normalized);
            if (seen.add(fingerprint)) {
                result.add(entry);
            } else {
                duplicate++;
            }
        }

        log.info("[UnifiedKbLoader] 清洗完成: 原始 {} 条, 去重后 {} 条, 丢弃 {} 条占位符/过短条目",
                entries.size(), result.size(), discarded);
        log.info("[UnifiedKbLoader] 内容去重: 移除 {} 条重复条目", duplicate);
        return result;
    }

    /**
     * 去掉常见模板占位符，并整理多余空白。
     */
    private String cleanPlaceholders(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String result = MUSTACHE_PLACEHOLDER.matcher(text).replaceAll(" ");
        result = DOLLAR_PLACEHOLDER.matcher(result).replaceAll(" ");
        result = JSP_PLACEHOLDER.matcher(result).replaceAll(" ");
        result = SQUARE_PLACEHOLDER.matcher(result).replaceAll(" ");
        result = MULTI_SPACE.matcher(result).replaceAll(" ");
        result = TRAILING_SPACE.matcher(result).replaceAll("");
        result = MULTI_BLANK_LINE.matcher(result).replaceAll("\n\n");
        return result.trim();
    }

    /**
     * 去重归一化：去掉所有空白并忽略英文大小写。
     */
    private String normalizeForDedup(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // JDK 必须提供 SHA-256，这里仅作兜底
            return Integer.toHexString(text.hashCode());
        }
    }

    /**
     * 文件名或相对路径命中 exclude-patterns 时跳过。
     */
    private boolean isExcluded(File docFile, File root) {
        String relativePath;
        try {
            relativePath = root.toPath().relativize(docFile.toPath()).toString().replace('\\', '/');
        } catch (IllegalArgumentException e) {
            relativePath = docFile.getName();
        }
        String source = relativePath + "/" + docFile.getName();

        String patterns = excludePatterns;
        if (patterns == null || patterns.isBlank()) {
            patterns = "面试|题库|test_ascii|测试语料说明";
        }
        for (String regex : patterns.split(",")) {
            String trimmed = regex.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                if (Pattern.compile(trimmed).matcher(source).find()) {
                    return true;
                }
            } catch (PatternSyntaxException e) {
                log.warn("[UnifiedKbLoader] 非法排除正则: {}", trimmed);
            }
        }
        return false;
    }

    /**
     * 清除缓存，下次加载时重新解析。
     */
    public void clearCache() {
        cachedEntries = null;
        log.info("[UnifiedKbLoader] 缓存已清除");
    }

    /**
     * 中文行业名 -> 英文 domain 值。
     */
    private String mapDomain(String chineseName) {
        return switch (chineseName) {
            case "银行" -> "banking";
            case "电信" -> "telecom";
            case "电商" -> "ecommerce";
            default -> chineseName;
        };
    }

    /**
     * 检查是否支持的文件类型。
     */
    private boolean isSupportedFile(String name) {
        // 排除 Word/Office 临时锁定文件（~$xxx.docx）
        if (name.startsWith("~$")) {
            return false;
        }
        String lowerName = name.toLowerCase(Locale.ROOT);
        return lowerName.endsWith(".md")
                || lowerName.endsWith(".docx")
                || lowerName.endsWith(".pdf")
                || lowerName.endsWith(".xlsx")
                || lowerName.endsWith(".csv")
                || lowerName.endsWith(".json")
                || lowerName.endsWith(".txt");
    }
}