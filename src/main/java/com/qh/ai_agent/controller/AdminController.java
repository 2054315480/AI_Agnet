package com.qh.ai_agent.controller;

import com.qh.ai_agent.service.FaqManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final FaqManagementService faqManagementService;

    @PostMapping("/faq/upload")
    public ResponseEntity<Map<String, Object>> uploadFaq(@RequestParam("file") MultipartFile file) {
        try {
            Map<String, Object> result = faqManagementService.uploadFaq(file);
            if (result.containsKey("error")) {
                return ResponseEntity.badRequest().body(result);
            }
            return ResponseEntity.ok(result);
        } catch (IOException e) {
            log.error("[Admin] FAQ上传失败", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "上传失败: " + e.getMessage()));
        }
    }

    @GetMapping("/faq/list")
    public ResponseEntity<List<Map<String, Object>>> listFaq() {
        try {
            return ResponseEntity.ok(faqManagementService.listFaqFiles());
        } catch (IOException e) {
            log.error("[Admin] 获取FAQ列表失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @DeleteMapping("/faq/{filename}")
    public ResponseEntity<Map<String, Object>> deleteFaq(@PathVariable String filename) {
        try {
            boolean deleted = faqManagementService.deleteFaq(filename);
            if (deleted) {
                return ResponseEntity.ok(Map.of("status", "deleted", "filename", filename));
            }
            return ResponseEntity.notFound().build();
        } catch (IOException e) {
            log.error("[Admin] FAQ删除失败", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "删除失败: " + e.getMessage()));
        }
    }

    @PostMapping("/faq/reload")
    public ResponseEntity<Map<String, Object>> reloadKnowledge() {
        Map<String, Object> result = faqManagementService.reloadKnowledge();
        return ResponseEntity.ok(result);
    }
}
