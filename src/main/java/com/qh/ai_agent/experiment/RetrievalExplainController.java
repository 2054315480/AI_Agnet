package com.qh.ai_agent.experiment;

import com.qh.ai_agent.knowledge.retriever.HybridRetriever;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 检索解释：把一次混合检索的每个阶段（BM25 / 向量 / RRF）的候选与分数都返回。
 *
 * 为什么这个接口最有说服力：
 * 观众输入一个问题，就能看到"哪条候选在 BM25 里排第几、向量分多少、RRF 后变成第几"，
 * 以及它最终有没有入选。这种过程细节无法伪造。
 */
@RestController
@RequestMapping("/admin/retrieval")
@RequiredArgsConstructor
public class RetrievalExplainController {

    private final HybridRetriever hybridRetriever;

    @PostMapping("/explain")
    public ResponseEntity<?> explain(@RequestBody Map<String, Object> body) {
        String query = String.valueOf(body.getOrDefault("query", "")).trim();
        if (query.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "query 不能为空"));
        }
        try {
            HybridRetriever.RetrievalExplain e = hybridRetriever.explain(query);
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("query", e.query());
            r.put("rrfK", e.rrfK());
            r.put("similarityThreshold", e.similarityThreshold());
            r.put("bm25", e.bm25());
            r.put("vector", e.vector());
            r.put("fused", e.fused());
            r.put("timings", Map.of(
                    "bm25Ms", e.bm25Ms(),
                    "vectorMs", e.vectorMs(),
                    "fuseMs", e.fuseMs(),
                    "totalMs", e.totalMs()));
            return ResponseEntity.ok(r);
        } catch (Exception ex) {
            return ResponseEntity.status(500).body(Map.of("error", ex.getMessage()));
        }
    }
}
