package com.qh.ai_agent.kg;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 知识图谱管理接口。
 */
@RestController
@RequestMapping("/admin/kg")
@RequiredArgsConstructor
public class KgController {

    private final KgBuildService buildService;
    private final KgQueryService queryService;

    /** 构建图谱（可指定并发线程数） */
    @PostMapping("/build")
    public ResponseEntity<?> build(@RequestBody(required = false) Map<String, Object> body) {
        int threads = Integer.parseInt(String.valueOf((body == null ? Map.of() : body).getOrDefault("threads", 8)));
        return ResponseEntity.ok(buildService.buildAll(threads));
    }

    /** 图谱统计 */
    @GetMapping("/stats")
    public ResponseEntity<?> stats() {
        return ResponseEntity.ok(buildService.stats());
    }

    /** 实体链接调试：输入 query 看能识别出哪些实体、关联哪些 kb */
    @PostMapping("/link")
    public ResponseEntity<?> link(@RequestBody Map<String, Object> body) {
        String query = String.valueOf(body.getOrDefault("query", ""));
        var entities = queryService.linkEntities(query);
        var kbScores = queryService.relatedKbIds(entities);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("query", query);
        out.put("enabled", queryService.isEnabled());
        out.put("entities", entities);
        out.put("relatedKbCount", kbScores.size());
        out.put("relatedKbTop", kbScores.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(10)
                .map(e -> Map.of("kbId", e.getKey(), "weight", e.getValue()))
                .toList());
        return ResponseEntity.ok(out);
    }
}
