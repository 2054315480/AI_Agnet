package com.qh.ai_agent.controller;

import com.qh.ai_agent.trace.MetricsRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 能力清单：把"这个系统到底实现了什么"用结构化数据暴露出来。
 *
 * 每条能力包含：
 * - name / category：名称与分类
 * - status：implemented / partial / planned
 * - evidence：怎么验证（页面路径或实验名）
 * - metrics：真实运行指标（能从 DB / 内存算出来的都算）
 *
 * 设计原则：metrics 必须是真实可查的数字，不允许写死，否则失去"自证"意义。
 */
@Slf4j
@RestController
@RequestMapping("/system")
@RequiredArgsConstructor
public class CapabilityController {

    private final MetricsRegistry metrics;
    private final JdbcTemplate jdbcTemplate;

    @GetMapping("/capabilities")
    public Map<String, Object> capabilities() {
        List<Map<String, Object>> list = new ArrayList<>();

        // ---------- 可观测 ----------
        list.add(cap("实时链路追踪", "可观测",
                "implemented", "发一条消息，右侧「实时链路」面板实时显示每个环节耗时",
                Map.of("累计 trace 数", count("agent_trace"),
                        "span 总数", count("trace_span"))));

        list.add(cap("系统自证条", "可观测",
                "implemented", "页面顶部常驻显示 commit / 运行时长 / 累计请求 / 表行数",
                Map.of("累计对话请求", metrics.getChatRequests(),
                        "LLM 调用次数", metrics.getLlmCalls())));

        list.add(cap("检索过程解释", "可观测",
                "implemented", "实验中心 -> 检索解释：输入问题看 BM25/向量/RRF 三阶段候选与分数",
                Map.of("检索次数", metrics.getRetrievalCalls())));

        // ---------- 检索与 RAG ----------
        list.add(cap("混合检索 BM25 + 向量 + RRF", "RAG",
                "implemented", "检索解释页可看到两路候选与融合结果",
                Map.of("向量文档数", count("cs_vector_store"))));

        list.add(cap("两路检索并行", "RAG",
                "implemented", "HybridRetriever 用 CompletableFuture 并行跑 BM25 与向量",
                Map.of("检索次数", metrics.getRetrievalCalls())));

        list.add(cap("FAQ 独立 Lucene 索引", "RAG",
                "implemented", "Tier1 精确匹配走独立 Lucene 索引，未命中回退自研相似度",
                Map.of()));

        Map<String, Object> lastEval = lastEval();
        list.add(cap("自动化评测（Recall/MRR/nDCG）", "RAG",
                "implemented", "评测报告页：一键跑同义改写评测集，支持历史对比",
                lastEval));

        // ---------- 性能与稳定性 ----------
        list.add(cap("三层缓存 + 分布式锁单飞", "性能",
                "implemented", "实验中心 -> 缓存击穿单飞：20 并发只回源 1 次",
                Map.of("L1 命中", metrics.getCacheL1Hits(),
                        "L2 命中", metrics.getCacheL2Hits(),
                        "锁获取", metrics.getLockAcquired(),
                        "锁后命中缓存", metrics.getLockServedFromCache())));

        list.add(cap("异步落库（移出主链路）", "性能",
                "implemented", "落库/链路保存由 persistExecutor 异步执行",
                Map.of("已落库消息数", count("conversation_messages"))));

        list.add(cap("LLM 降级链路", "稳定性",
                "implemented", "实验中心 -> 注入 llm 故障，发消息仍返回 200 并标记 degraded",
                Map.of("累计降级次数", metrics.getDegradedCount())));

        // ---------- Agent ----------
        list.add(cap("ReAct 多步推理 + 工具调用", "Agent",
                "implemented", "对话中「处理过程」可展开查看每步工具调用",
                Map.of("LLM 调用次数", metrics.getLlmCalls())));

        list.add(cap("工具循环检测", "Agent",
                "implemented", "同工具+同参数连续超过 2 次即打断并建议转人工",
                Map.of()));

        list.add(cap("意图识别 + 8 阶段状态机", "Agent",
                "implemented", "对话会展示意图标签与置信度；状态机决定追问/澄清/转人工",
                Map.of("会话数", count("conversations"))));

        list.add(cap("输入输出护栏（违禁词/PII）", "Agent",
                "implemented", "输入侧拦截违禁词、脱敏手机号；输出侧再脱敏一次",
                Map.of()));

        // ---------- 数据与存储 ----------
        list.add(cap("会话状态可插拔存储（内存/Redis）", "存储",
                "implemented", "内存版 TTL+LRU；Redis 版多实例共享+滑动 TTL",
                Map.of()));

        list.add(cap("知识库重建 + 增量向量化", "存储",
                "implemented", "FAQ 管理页上传后自动重建 BM25 + 增量写向量库",
                Map.of("向量行数", count("cs_vector_store"))));

        list.add(cap("向量写入幂等（唯一索引）", "存储",
                "implemented", "cs_vector_store 对 metadata->kb_id 建唯一索引",
                Map.of("索引存在", indexExists("uq_cs_vector_kb_id"))));

        // ---------- 未完成 ----------
        list.add(cap("Reranker 精排", "RAG", "planned",
                "计划接入 DashScope gte-rerank，用评测集验证增益", Map.of()));
        list.add(cap("轻量知识图谱", "RAG", "planned",
                "计划用 PG 三张表 + 递归 CTE 做实体链接增强", Map.of()));
        list.add(cap("熔断器 + 令牌桶限流", "稳定性",
                "implemented", "实验中心 -> 系统韧性：可查看熔断三态与限流拒绝数，支持手动打开熔断",
                Map.of()));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("generatedAt", java.time.Instant.now().toString());
        out.put("total", list.size());
        out.put("implemented", list.stream().filter(m -> "implemented".equals(m.get("status"))).count());
        out.put("items", list);
        return out;
    }

    private Map<String, Object> cap(String name, String category, String status,
                                    String evidence, Map<String, Object> metricsMap) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("category", category);
        m.put("status", status);
        m.put("evidence", evidence);
        m.put("metrics", metricsMap);
        return m;
    }

    private Long count(String table) {
        try {
            return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean indexExists(String indexName) {
        try {
            Integer n = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM pg_indexes WHERE indexname = ?", Integer.class, indexName);
            return n != null && n > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private Map<String, Object> lastEval() {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT run_name, recall_at_5, mrr, ndcg_at_5, sample_count FROM eval_run " +
                    "ORDER BY created_at DESC LIMIT 1");
            if (rows.isEmpty()) {
                return Map.of("评测次数", 0);
            }
            Map<String, Object> r = rows.get(0);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("最近一次", r.get("run_name"));
            m.put("样本数", r.get("sample_count"));
            m.put("Recall@5", round(r.get("recall_at_5")));
            m.put("MRR", round(r.get("mrr")));
            m.put("nDCG@5", round(r.get("ndcg_at_5")));
            return m;
        } catch (Exception e) {
            return Map.of();
        }
    }

    private Object round(Object v) {
        if (v instanceof Number n) {
            return Math.round(n.doubleValue() * 1000.0) / 1000.0;
        }
        return v;
    }
}
