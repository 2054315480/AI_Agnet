package com.qh.ai_agent.controller;

import com.qh.ai_agent.trace.MetricsRegistry;
import com.qh.ai_agent.trace.TraceCollector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 系统自证信息：给前端"自证条"提供真实数据。
 *
 * 所有数字都从运行时实时读取（DB COUNT / 内存计数器 / git 命令），不允许写死。
 * 观众刷新页面能看到累计数持续增长，这是"真实运行实例"而非"录屏"的直接证据。
 */
@Slf4j
@RestController
@RequestMapping("/system")
@RequiredArgsConstructor
public class SystemInfoController {

    private final MetricsRegistry metrics;
    private final JdbcTemplate jdbcTemplate;

    @Value("${cs.trace.enabled:true}")
    private boolean traceEnabled;

    @Value("${cs.cache.enabled:true}")
    private boolean cacheEnabled;

    @Value("${cs.kg.enabled:false}")
    private boolean kgEnabled;

    @Value("${cs.rerank.enabled:false}")
    private boolean rerankEnabled;

    private volatile String cachedCommit;
    private volatile long commitCacheAt;

    @GetMapping("/info")
    public ResponseEntity<?> info() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("gitCommit", gitCommit());
        data.put("startedAt", metrics.getStartedAt().toString());
        data.put("uptimeSeconds", Duration.between(metrics.getStartedAt(), Instant.now()).getSeconds());
        data.put("uptimeHuman", humanUptime());

        Map<String, Object> counters = new LinkedHashMap<>();
        counters.put("chatRequests", metrics.getChatRequests());
        counters.put("llmCalls", metrics.getLlmCalls());
        counters.put("cacheL1Hits", metrics.getCacheL1Hits());
        counters.put("cacheL2Hits", metrics.getCacheL2Hits());
        counters.put("lockAcquired", metrics.getLockAcquired());
        counters.put("lockServedFromCache", metrics.getLockServedFromCache());
        counters.put("degradedCount", metrics.getDegradedCount());
        counters.put("retrievalCalls", metrics.getRetrievalCalls());
        counters.put("rerankCalls", metrics.getRerankCalls());
        counters.put("traces", TraceCollector.totalTraces());
        Map<String, Object> buildInfo = new LinkedHashMap<>();
        buildInfo.put("commit", gitCommit());
        buildInfo.put("builtAt", readBuildInfo("builtAt"));
        buildInfo.put("jarSha256", readBuildInfo("jarSha256"));
        data.put("buildInfo", buildInfo);
        data.put("counters", counters);

        Map<String, Object> tables = new LinkedHashMap<>();
        tables.put("conversations", count("conversations"));
        tables.put("messages", count("conversation_messages"));
        tables.put("analytics", count("dialog_analytics"));
        tables.put("csVectorStore", count("cs_vector_store"));
        tables.put("agentTrace", count("agent_trace"));
        data.put("tables", tables);

        Map<String, Object> features = new LinkedHashMap<>();
        features.put("traceEnabled", traceEnabled);
        features.put("kgEnabled", kgEnabled);
        features.put("rerankEnabled", rerankEnabled);
        features.put("cacheEnabled", cacheEnabled);
        features.put("lockEnabled", true);
        data.put("features", features);

        return ResponseEntity.ok(data);
    }

    private Integer count(String table) {
        try {
            return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        } catch (Exception e) {
            return null;
        }
    }

    private String humanUptime() {
        long s = Duration.between(metrics.getStartedAt(), Instant.now()).getSeconds();
        long d = s / 86400;
        long h = (s % 86400) / 3600;
        long m = (s % 3600) / 60;
        if (d > 0) {
            return d + " 天 " + h + " 小时";
        }
        if (h > 0) {
            return h + " 小时 " + m + " 分";
        }
        return m + " 分钟";
    }

    /** 读部署信息文件（默认工作目录下的 BUILD_INFO），用于在无 .git 的部署机上报告真实版本 */
    private String readBuildInfo(String key) {
        try {
            java.nio.file.Path path = java.nio.file.Path.of(
                    System.getProperty("app.build-info", "BUILD_INFO"));
            if (!java.nio.file.Files.exists(path)) {
                return null;
            }
            for (String line : java.nio.file.Files.readAllLines(path)) {
                int idx = line.indexOf('=');
                if (idx > 0 && line.substring(0, idx).trim().equals(key)) {
                    return line.substring(idx + 1).trim();
                }
            }
        } catch (Exception ignore) {
            // 读不到就当没有
        }
        return null;
    }

    /** 从 git 读取当前 commit（5 分钟缓存），失败不影响接口 */
    private String gitCommit() {
        long now = System.currentTimeMillis();
        if (cachedCommit != null && now - commitCacheAt < 300_000) {
            return cachedCommit;
        }
        try {
            Process p = new ProcessBuilder("git", "rev-parse", "--short", "HEAD")
                    .redirectErrorStream(true).start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line = r.readLine();
                int exit = p.waitFor();
                // 部署目录通常没有 .git，git 会把 "fatal: ..." 打到合并后的 stdout，
                // 这里必须按退出码判断，否则会把错误信息当成 commit 返回给前端
                cachedCommit = (exit != 0 || line == null || line.startsWith("fatal"))
                        ? "unknown" : line.trim();
            }
        } catch (Exception e) {
            cachedCommit = null;
        }
        // 部署目录没有 .git：读部署脚本写入的 BUILD_INFO（commit=/builtAt=/jarSha256=）
        // git 在部署机上可能存在但不在仓库里（返回 fatal / 非 0），此时也要回退到 BUILD_INFO
        if (cachedCommit == null || cachedCommit.isBlank() || "unknown".equals(cachedCommit)) {
            String fromFile = readBuildInfo("commit");
            cachedCommit = (fromFile == null || fromFile.isBlank()) ? "unknown" : fromFile.trim();
        }
        commitCacheAt = now;
        return cachedCommit;
    }
}
