package com.qh.ai_agent.eval;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * 评测接口。
 *
 * 用法：
 *   POST /admin/eval/run        跑一次手工样本评测（兼容旧接口）
 *   POST /admin/eval/auto       从知识库自动构建评测集并跑
 *   POST /admin/eval/labeled    跑独立人工标注集
 *   GET  /admin/eval/history    历史结果
 *   GET  /admin/eval/compare    两次 run 指标差对比
 */
@RestController
@RequestMapping("/admin/eval")
@RequiredArgsConstructor
public class EvalController {

    private final EvalService evalService;
    private final EvalDatasetBuilder datasetBuilder;

    @PostMapping("/run")
    public ResponseEntity<?> run(@RequestBody Map<String, Object> body) {
        String runName = String.valueOf(body.getOrDefault("runName", "manual-" + System.currentTimeMillis()));
        int topK = Integer.parseInt(String.valueOf(body.getOrDefault("topK", 5)));
        String datasetType = String.valueOf(body.getOrDefault("datasetType", "manual"));
        String datasetSource = String.valueOf(body.getOrDefault("datasetSource", "request"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> raw = (List<Map<String, Object>>) body.getOrDefault("samples", List.of());
        List<EvalService.Sample> samples = raw.stream()
                .map(m -> new EvalService.Sample(
                        String.valueOf(m.get("id")),
                        String.valueOf(m.get("query")),
                        ((List<?>) m.getOrDefault("expectedKbIds", List.of())).stream().map(String::valueOf).toList(),
                        String.valueOf(m.getOrDefault("note", ""))))
                .toList();
        if (samples.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "samples 不能为空"));
        }
        return ResponseEntity.ok(evalService.run(runName, samples, topK, datasetType, datasetSource));
    }

    /** 自动构建评测集并跑（从知识库反推，需在报告中标注 auto）。 */
    @PostMapping("/auto")
    public ResponseEntity<?> auto(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> req = body == null ? Map.of() : body;
        int size = Integer.parseInt(String.valueOf(req.getOrDefault("size", 60)));
        int topK = Integer.parseInt(String.valueOf(req.getOrDefault("topK", 5)));
        String runName = String.valueOf(req.getOrDefault("runName", "auto-" + System.currentTimeMillis()));
        String mode = String.valueOf(req.getOrDefault("mode", "question"));
        List<EvalService.Sample> samples = datasetBuilder.build(size, mode);
        if (samples.isEmpty()) {
            return ResponseEntity.status(500).body(Map.of("error", "评测集构建失败（知识库为空？）"));
        }
        return ResponseEntity.ok(evalService.run(runName, samples, topK, "auto", "knowledge_base"));
    }

    /** 跑独立人工标注集。 */
    @PostMapping("/labeled")
    public ResponseEntity<?> labeled(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> req = body == null ? Map.of() : body;
        int topK = Integer.parseInt(String.valueOf(req.getOrDefault("topK", 5)));
        String runName = String.valueOf(req.getOrDefault("runName", "labeled-" + System.currentTimeMillis()));
        try {
            List<EvalService.Sample> samples = datasetBuilder.loadLabeledSet();
            return ResponseEntity.ok(evalService.run(
                    runName, samples, topK, "labeled", datasetBuilder.getLabeledSetPath()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /** 两次 run 对比，差值口径 b - a。 */
    @GetMapping("/compare")
    public ResponseEntity<?> compare(@RequestParam("a") long a, @RequestParam("b") long b) {
        try {
            return ResponseEntity.ok(evalService.compare(a, b));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/history")
    public ResponseEntity<?> history(@RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(evalService.history(limit));
    }
}
