package com.qh.ai_agent.knowledge.parser;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 统一知识库加载器。
 * 遍历知识库根目录下的行业子目录，根据文件扩展名分发给对应解析器，汇总所有 KbEntry。
 *
 * 目录结构：
 *   knowledge_base/
 *   ├── 银行/  → domain=banking
 *   ├── 电信/  → domain=telecom
 *   └── 电商/  → domain=ecommerce
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UnifiedKbLoader {

    private final KbParserFactory parserFactory;

    /** 比赛知识库根目录，默认 data/data/knowledge_base */
    @Value("${competition.kb.dir:./data/data/knowledge_base}")
    private String kbRootDir;

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
                try {
                    KbParser parser = parserFactory.getParser(docFile.getName());
                    if (parser == null) {
                        log.warn("[UnifiedKbLoader] 不支持的文件类型: {}", docFile.getName());
                        continue;
                    }
                    List<KbEntry> entries = parser.parse(docFile, domain);
                    allEntries.addAll(entries);
                    log.info("[UnifiedKbLoader] {} → {} 条 (domain={})",
                            docFile.getName(), entries.size(), domain);
                } catch (IOException e) {
                    log.error("[UnifiedKbLoader] 解析失败: {}", docFile.getName(), e);
                }
            }
        }

        log.info("[UnifiedKbLoader] 加载完成: 总计 {} 条 (来自 {})", allEntries.size(), kbRootDir);
        cachedEntries = allEntries;
        return allEntries;
    }

    /**
     * 清除缓存，下次加载时重新解析。
     */
    public void clearCache() {
        cachedEntries = null;
        log.info("[UnifiedKbLoader] 缓存已清除");
    }

    /**
     * 中文行业名 → 英文 domain 值。
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
        String lowerName = name.toLowerCase();
        return lowerName.endsWith(".md")
                || lowerName.endsWith(".docx")
                || lowerName.endsWith(".pdf");
    }
}
