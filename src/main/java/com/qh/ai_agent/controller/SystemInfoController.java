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
        features.put("kgEnabled", false);
        features.put("rerankEnabled", false);
        features.put("cacheEnabled", false);
        features.put("lockEnabled", false);
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
                p.waitFor();
                cachedCommit = line == null ? "unknown" : line.trim();
            }
        } catch (Exception e) {
            cachedCommit = "unknown";
        }
        commitCacheAt = now;
        return cachedCommit;
    }
}
