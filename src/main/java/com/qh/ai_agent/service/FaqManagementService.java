package com.qh.ai_agent.service;

import com.qh.ai_agent.knowledge.KnowledgeBaseLoader;
import com.qh.ai_agent.knowledge.KnowledgeReindexService;
import com.qh.ai_agent.knowledge.KnowledgeSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class FaqManagementService {

    private final KnowledgeSearchService knowledgeSearchService;
    private final KnowledgeBaseLoader knowledgeBaseLoader;
    private final KnowledgeReindexService knowledgeReindexService;

    @Value("${cs.faq.data-dir:#{null}}")
    private String externalFaqDir;

    /** 上传/删除 FAQ 后是否自动重建 BM25 并增量写入向量库。 */
    @Value("${cs.reindex.auto-on-upload:true}")
    private boolean autoReindexOnUpload;

    /**
     * 上传 FAQ Markdown 文件。
     * 写入 data/faq 后清除内存缓存，并触发 KnowledgeReindexService 重建检索索引。
     */
    public Map<String, Object> uploadFaq(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.endsWith(".md")) {
            return Map.of("error", "仅支持 Markdown (.md) 文件");
        }

        Path targetDir = getFaqDir();
        Files.createDirectories(targetDir);
        Path targetPath = targetDir.resolve(filename);

        boolean overwrite = Files.exists(targetPath);
        Files.write(targetPath, file.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        String content = Files.readString(targetPath);
        int entryCount = countFaqEntries(content);
        log.info("[FAQ] 上传文件: {}, 条目数: {}, 覆盖: {}", filename, entryCount, overwrite);

        // 清除缓存，确保下一次加载能看到新文件
        knowledgeSearchService.clearCache();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("filename", filename);
        response.put("entryCount", entryCount);
        response.put("overwritten", overwrite);
        if (autoReindexOnUpload) {
            response.put("reindex", safeReindex());
        } else {
            response.put("reindex", Map.of("skipped", true, "message", "cs.reindex.auto-on-upload=false，已跳过自动重建"));
        }
        return response;
    }

    /**
     * 获取 FAQ 文件列表。
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
     * 删除 FAQ 文件。
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
            if (autoReindexOnUpload) {
                safeReindex();
            }
            return true;
        }
        return false;
    }

    /**
     * 重新加载知识库：清理缓存后重建 BM25/FAQ 索引并增量写入向量库。
     */
    public Map<String, Object> reloadKnowledge() {
        knowledgeSearchService.clearCache();
        return reindexKnowledge();
    }

    /**
     * 手动触发全量重建，供 POST /admin/faq/reindex 调用。
     */
    public Map<String, Object> reindexKnowledge() {
        Map<String, Object> reindex = safeReindex();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", Boolean.TRUE.equals(reindex.get("success")) ? "reindexed" : "partial");
        response.put("reindex", reindex);
        return response;
    }

    /**
     * 重建失败不能影响上传接口，因此统一转换成结果 Map。
     */
    private Map<String, Object> safeReindex() {
        try {
            return knowledgeReindexService.reindexAll().toMap();
        } catch (Exception e) {
            log.error("[FAQ] 知识库重建失败（上传/删除仍成功）", e);
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("success", false);
            error.put("message", "知识库重建失败: " + e.getMessage());
            return error;
        }
    }

    private Path getFaqDir() {
        if (externalFaqDir != null && !externalFaqDir.isBlank()) {
            return Paths.get(externalFaqDir);
        }
        // 默认使用项目根目录下的 data/faq
        return Paths.get(System.getProperty("user.dir"), "data", "faq");
    }

    /**
     * 兼容两种 FAQ 格式：
     *   - 旧格式：## 问题
     *   - 新格式：### Q1：问题
     */
    private int countFaqEntries(String content) {
        Matcher newFormat = Pattern.compile("^###\\s+[Qq]\\d*\\s*[：:]", Pattern.MULTILINE).matcher(content);
        int newCount = 0;
        while (newFormat.find()) {
            newCount++;
        }
        if (newCount > 0) {
            return newCount;
        }

        Matcher oldFormat = Pattern.compile("^##\\s+\\S+", Pattern.MULTILINE).matcher(content);
        int oldCount = 0;
        while (oldFormat.find()) {
            oldCount++;
        }
        return oldCount;
    }
}