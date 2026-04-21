package com.qh.ai_agent.rag.reader;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class GitHubApiService {

    private static final String GITHUB_API = "https://api.github.com";

    @Value("${ai.rag.github.token:}")
    private String githubToken;

    private HttpRequest createRequest(String url) {
        HttpRequest request = HttpRequest.get(url)
                .timeout(15000)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "AI-Agent-RAG");
        if (githubToken != null && !githubToken.isEmpty()) {
            request.header("Authorization", "Bearer " + githubToken);
        }
        return request;
    }

    public String fetchReadme(String owner, String repo) {
        String url = GITHUB_API + "/repos/" + owner + "/" + repo + "/readme";
        try (HttpResponse response = createRequest(url).execute()) {
            if (response.isOk()) {
                JSONObject json = JSONUtil.parseObj(response.body());
                String encoding = json.getStr("encoding", "base64");
                String content = json.getStr("content", "");
                if ("base64".equals(encoding)) {
                    return new String(Base64.getDecoder().decode(content.replace("\n", "")),
                            StandardCharsets.UTF_8);
                }
                return content;
            }
            log.warn("获取 README 失败: {} - {}", response.getStatus(), url);
            return null;
        } catch (Exception e) {
            log.error("获取 README 异常: {}", e.getMessage());
            return null;
        }
    }

    public JSONArray fetchIssues(String owner, String repo, int page, int perPage) {
        String url = GITHUB_API + "/repos/" + owner + "/" + repo
                + "/issues?state=open&sort=updated&page=" + page + "&per_page=" + perPage;
        try (HttpResponse response = createRequest(url).execute()) {
            if (response.isOk()) {
                return JSONUtil.parseArray(response.body());
            }
            log.warn("获取 Issues 失败: {} - {}", response.getStatus(), url);
            return new JSONArray();
        } catch (Exception e) {
            log.error("获取 Issues 异常: {}", e.getMessage());
            return new JSONArray();
        }
    }

    public JSONArray fetchDirectoryContents(String owner, String repo, String path) {
        String url = GITHUB_API + "/repos/" + owner + "/" + repo + "/contents/" + path;
        try (HttpResponse response = createRequest(url).execute()) {
            if (response.isOk()) {
                return JSONUtil.parseArray(response.body());
            }
            log.warn("获取目录内容失败: {} - {}", response.getStatus(), url);
            return new JSONArray();
        } catch (Exception e) {
            log.error("获取目录内容异常: {}", e.getMessage());
            return new JSONArray();
        }
    }

    public Map<String, Object> fetchRepoInfo(String owner, String repo) {
        String url = GITHUB_API + "/repos/" + owner + "/" + repo;
        try (HttpResponse response = createRequest(url).execute()) {
            if (response.isOk()) {
                JSONObject json = JSONUtil.parseObj(response.body());
                Map<String, Object> info = new HashMap<>();
                info.put("name", json.getStr("name"));
                info.put("description", json.getStr("description"));
                info.put("language", json.getStr("language"));
                info.put("stars", json.getInt("stargazers_count", 0));
                info.put("topics", json.getJSONArray("topics"));
                return info;
            }
        } catch (Exception e) {
            log.error("获取仓库信息异常: {}", e.getMessage());
        }
        return Map.of();
    }
}
