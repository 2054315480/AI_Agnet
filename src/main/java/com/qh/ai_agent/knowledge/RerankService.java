package com.qh.ai_agent.knowledge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qh.ai_agent.knowledge.retriever.RetrievalResult;
import com.qh.ai_agent.trace.MetricsRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * 精排服务：调用 DashScope gte-rerank-v2 对 RRF 融合后的候选重新打分。
 *
 * 为什么需要精排：
 * RRF 只用排名融合，没有语义打分能力；而 cross-encoder 类重排模型能
 * 直接对 (query, 文档) 建模相关性，显著提升 top-k 的准确率。
 *
 * 降级策略：任何异常都返回原顺序，绝不能让精排失败拖垮检索。
 */
@Slf4j
@Service
public class RerankService {

    @Value("${cs.rerank.enabled:true}")
    private boolean enabled;

    @Value("${cs.rerank.api-key:}")
    private String apiKey;

    @Value("${cs.rerank.url:https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank}")
    private String url;

    @Value("${cs.rerank.model:gte-rerank-v2}")
    private String model;

    @Value("${cs.rerank.candidate-top-k:20}")
    private int candidateTopK;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 对候选重新排序。
     *
     * @param query      用户原始查询
     * @param candidates RRF 融合后的候选
     * @param topN       最终返回条数
     * @return 重排后的结果（保留原 RetrievalResult 结构，score 替换为 rerank 分）
     */
    public List<RetrievalResult> rerank(String query, List<RetrievalResult> candidates, int topN) {
        if (!enabled || candidates == null || candidates.size() <= 1) {
            return candidates == null ? List.of() : candidates.stream().limit(topN).toList();
        }
        if (apiKey == null || apiKey.isBlank()) {
            log.debug("[Rerank] 未配置 api-key，跳过精排");
            return candidates.stream().limit(topN).toList();
        }

        List<RetrievalResult> pool = candidates.stream().limit(candidateTopK).toList();
        try {
            List<String> docs = pool.stream()
                    .map(r -> {
                        String q = r.getQuestion() == null ? "" : r.getQuestion();
                        String a = r.getAnswer() == null ? "" : r.getAnswer();
                        String text = (q + " " + a).trim();
                        return text.length() > 500 ? text.substring(0, 500) : text;
                    })
                    .toList();

            Map<String, Object> input = new LinkedHashMap<>();
            input.put("query", query);
            input.put("documents", docs);
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("return_documents", false);
            params.put("top_n", Math.min(topN, docs.size()));
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("input", input);
            body.put("parameters", params);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            HttpEntity<String> req = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);

            long t0 = System.currentTimeMillis();
            ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.POST, req, String.class);
            long cost = System.currentTimeMillis() - t0;

            JsonNode root = objectMapper.readTree(resp.getBody());
            JsonNode results = root.path("output").path("results");
            if (!results.isArray() || results.isEmpty()) {
                log.warn("[Rerank] 返回为空，降级为原顺序");
                return candidates.stream().limit(topN).toList();
            }

            List<RetrievalResult> reranked = new ArrayList<>();
            for (JsonNode item : results) {
                int idx = item.path("index").asInt(-1);
                double score = item.path("relevance_score").asDouble(0);
                if (idx < 0 || idx >= pool.size()) {
                    continue;
                }
                RetrievalResult origin = pool.get(idx);
                reranked.add(RetrievalResult.builder()
                        .kbId(origin.getKbId())
                        .question(origin.getQuestion())
                        .answer(origin.getAnswer())
                        .domain(origin.getDomain())
                        .category(origin.getCategory())
                        .sourceFile(origin.getSourceFile())
                        .score(score)
                        .source(RetrievalResult.Source.HYBRID)
                        .build());
            }
            MetricsRegistry.incRerankCallsStatic();
            log.info("[Rerank] 完成: candidates={} -> topN={} 耗时={}ms", pool.size(), reranked.size(), cost);
            return reranked.isEmpty() ? candidates.stream().limit(topN).toList() : reranked;
        } catch (Exception e) {
            log.warn("[Rerank] 调用失败，降级为原顺序: {}", e.getMessage());
            return candidates.stream().limit(topN).toList();
        }
    }
}
