package com.qh.ai_agent.eval;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 评测接口。
 *
 * 用法：
 *   POST /admin/eval/run        跑一次评测（传 runName + 样本）
 *   GET  /admin/eval/history    历史结果（版本对比）
 *   POST /admin/eval/auto       用知识库自带问题自动生成评测集并跑（免手工标注）
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
        return ResponseEntity.ok(evalService.run(runName, samples, topK));
    }

    /** 自动构建评测集并跑（从知识库反推，无需人工标注） */
    @PostMapping("/auto")
    public ResponseEntity<?> auto(@RequestBody(required = false) Map<String, Object> body) {
        int size = Integer.parseInt(String.valueOf((body == null ? Map.of() : body).getOrDefault("size", 60)));
        int topK = Integer.parseInt(String.valueOf((body == null ? Map.of() : body).getOrDefault("topK", 5)));
        String runName = String.valueOf((body == null ? Map.of() : body).getOrDefault("runName", "auto-" + System.currentTimeMillis()));

        String mode = String.valueOf((body == null ? Map.of() : body).getOrDefault("mode", "question"));
        List<EvalService.Sample> samples = datasetBuilder.build(size, mode);
        if (samples.isEmpty()) {
            return ResponseEntity.status(500).body(Map.of("error", "评测集构建失败（知识库为空？）"));
        }
        return ResponseEntity.ok(evalService.run(runName, samples, topK));
    }

    @GetMapping("/history")
    public ResponseEntity<?> history(@RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(evalService.history(limit));
    }
}
