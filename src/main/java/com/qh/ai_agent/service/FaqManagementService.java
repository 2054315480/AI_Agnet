package com.qh.ai_agent.service;

import com.qh.ai_agent.knowledge.KnowledgeBaseLoader;
import com.qh.ai_agent.knowledge.KnowledgeSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class FaqManagementService {

    private final KnowledgeSearchService knowledgeSearchService;
    private final KnowledgeBaseLoader knowledgeBaseLoader;

    @Value("${cs.faq.data-dir:#{null}}")
    private String externalFaqDir;

    /**
     * 上传 FAQ Markdown 文件
     */
    public Map<String, Object> uploadFaq(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.endsWith(".md")) {
            return Map.of("error", "仅支持 Markdown (.md) 文件");
        }

        Path targetDir = getFaqDir();
        Files.createDirectories(targetDir);
        Path targetPath = targetDir.resolve(filename);

        // 检查是否已存在
        boolean overwrite = Files.exists(targetPath);
        Files.write(targetPath, file.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        // 解析条目数
        String content = Files.readString(targetPath);
        int entryCount = countFaqEntries(content);

        log.info("[FAQ] 上传文件: {}, 条目数: {}, 覆盖: {}", filename, entryCount, overwrite);

        // 清除缓存以便下次搜索时重新加载
        knowledgeSearchService.clearCache();

        return Map.of(
                "filename", filename,
                "entryCount", entryCount,
                "overwritten", overwrite
        );
    }

    /**
     * 获取 FAQ 文件列表
     */
    public List<Map<String, Object>> listFaqFiles() throws IOException {
        Path faqDir = getFaqDir();
        List<Map<String, Object>> files = new ArrayList<>();

        if (!Files.exists(faqDir)) return files;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(faqDir, "*.md")) {
            for (Path path : stream) {
                String content = Files.readString(path);
                int entryCount = countFaqEntries(content);
                files.add(Map.of(
                        "filename", path.getFileName().toString(),
                        "entryCount", entryCount,
                        "size", Files.size(path)
                ));
            }
        }

        return files;
    }

    /**
     * 删除 FAQ 文件
     */
    public boolean deleteFaq(String filename) throws IOException {
        // 安全检查：防止路径遍历
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            return false;
        }

        Path faqDir = getFaqDir();
        Path target = faqDir.resolve(filename);

        if (Files.exists(target) && Files.isRegularFile(target)) {
            Files.delete(target);
            knowledgeSearchService.clearCache();
            log.info("[FAQ] 删除文件: {}", filename);
            return true;
        }
        return false;
    }

    /**
     * 重新加载知识库
     */
    public Map<String, Object> reloadKnowledge() {
        knowledgeSearchService.clearCache();
        // 触发重新加载
        var searchResult = knowledgeSearchService.search("测试", 1);
        return Map.of(
                "status", "reloaded",
                "results", searchResult.results().size()
        );
    }

    private Path getFaqDir() {
        if (externalFaqDir != null && !externalFaqDir.isBlank()) {
            return Paths.get(externalFaqDir);
        }
        // 默认使用 classpath 下的 knowledge/faq 目录
        // 对于上传，使用项目根目录下的 data/faq
        return Paths.get(System.getProperty("user.dir"), "data", "faq");
    }

    private int countFaqEntries(String content) {
        Pattern pattern = Pattern.compile("^##\\s+.+", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(content);
        int count = 0;
        while (matcher.find()) count++;
        return count;
    }
}
