package com.qh.ai_agent.controller;

import com.qh.ai_agent.service.AnalyticsService;
import com.qh.ai_agent.service.ExportService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 对话分析统计 API
 */
@Slf4j
@RestController
@RequestMapping("/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final ExportService exportService;

    /** 总览统计卡片 */
    @GetMapping("/overview")
    public Map<String, Object> getOverview() {
        return analyticsService.getOverview();
    }

    /** 意图分布统计 */
    @GetMapping("/intent-distribution")
    public List<Map<String, Object>> getIntentDistribution() {
        return analyticsService.getIntentDistribution();
    }

    /** 会话列表（含分析摘要） */
    @GetMapping("/sessions")
    public List<Map<String, Object>> getSessionList() {
        return analyticsService.getSessionList();
    }

    /** 单会话详情（完整对话回放） */
    @GetMapping("/sessions/{conversationId}")
    public Map<String, Object> getSessionDetail(@PathVariable String conversationId) {
        return analyticsService.getSessionDetail(conversationId);
    }

    /** 导出全部对话日志（CSV） */
    @GetMapping("/export/csv")
    public void exportCsv(HttpServletResponse response) throws Exception {
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=dialog_logs.csv");
        exportService.exportAllCsv(response.getWriter());
    }

    /** 导出单个会话对话日志（CSV） */
    @GetMapping("/export/csv/{conversationId}")
    public void exportSessionCsv(@PathVariable String conversationId, HttpServletResponse response) throws Exception {
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=dialog_" + conversationId + ".csv");
        exportService.exportSessionCsv(conversationId, response.getWriter());
    }
}
