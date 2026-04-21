package com.qh.ai_agent.rag.reader;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.qh.ai_agent.rag.enricher.DocumentMetadataEnricher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;

@Component
@Slf4j
public class GitHubDocumentReader implements DocumentReader {

    private final GitHubApiService gitHubApiService;
    private final DocumentMetadataEnricher metadataEnricher;

    @Value("${ai.rag.github.default-owner:}")
    private String defaultOwner;

    @Value("${ai.rag.github.default-repo:}")
    private String defaultRepo;

    public GitHubDocumentReader(GitHubApiService gitHubApiService,
                                DocumentMetadataEnricher metadataEnricher) {
        this.gitHubApiService = gitHubApiService;
        this.metadataEnricher = metadataEnricher;
    }

    @Override
    public List<Document> get() {
        if (defaultOwner.isEmpty() || defaultRepo.isEmpty()) {
            log.warn("未配置默认 GitHub 仓库 (ai.rag.github.default-owner/default-repo)");
            return List.of();
        }
        return loadRepository(defaultOwner, defaultRepo);
    }

    @Override
    public List<Document> read() {
        return get();
    }

    /**
     * 加载指定 GitHub 仓库的文档（README + Issues + 仓库信息）
     */
    public List<Document> loadRepository(String owner, String repo) {
        List<Document> documents = new ArrayList<>();
        String repoLabel = owner + "/" + repo;
        log.info("开始加载 GitHub 仓库文档: {}", repoLabel);

        // 1. README
        String readme = gitHubApiService.fetchReadme(owner, repo);
        if (readme != null && !readme.isEmpty()) {
            Document doc = createDocument(readme, Map.of(
                    "source_type", "GITHUB_README",
                    "repository", repoLabel,
                    "path", "README.md",
                    "load_date", LocalDate.now().toString()
            ));
            documents.add(doc);
            log.info("加载 README 成功，长度: {}", readme.length());
        }

        // 2. Issues（最近 10 条）
        JSONArray issues = gitHubApiService.fetchIssues(owner, repo, 1, 10);
        for (int i = 0; i < issues.size(); i++) {
            JSONObject issue = issues.getJSONObject(i);
            String title = issue.getStr("title", "");
            String body = issue.getStr("body", "");
            int number = issue.getInt("number", 0);
            String state = issue.getStr("state", "open");

            if (body == null) body = "";
            String content = "# Issue #" + number + ": " + title + "\n\n" + body;

            Document doc = createDocument(content, Map.of(
                    "source_type", "GITHUB_ISSUE",
                    "repository", repoLabel,
                    "issue_number", String.valueOf(number),
                    "issue_state", state,
                    "load_date", LocalDate.now().toString()
            ));
            documents.add(doc);
        }
        log.info("加载 Issues 数量: {}", issues.size());

        // 3. 仓库基本信息
        Map<String, Object> repoInfo = gitHubApiService.fetchRepoInfo(owner, repo);
        if (!repoInfo.isEmpty()) {
            String infoContent = String.format(
                    "# 仓库信息: %s\n\n描述: %s\n语言: %s\nStars: %d",
                    repoInfo.getOrDefault("name", repoLabel),
                    repoInfo.getOrDefault("description", "无"),
                    repoInfo.getOrDefault("language", "未知"),
                    repoInfo.getOrDefault("stars", 0)
            );
            Document doc = createDocument(infoContent, Map.of(
                    "source_type", "GITHUB_REPO_INFO",
                    "repository", repoLabel,
                    "load_date", LocalDate.now().toString()
            ));
            documents.add(doc);
        }

        // 元信息丰富
        metadataEnricher.enrichDocuments(documents);
        log.info("GitHub 仓库 {} 文档加载完成，共 {} 个文档", repoLabel, documents.size());
        return documents;
    }

    private Document createDocument(String content, Map<String, Object> metadata) {
        Map<String, Object> meta = new HashMap<>(metadata);
        return new Document(content, meta);
    }
}
