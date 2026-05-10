package com.qh.ai_agent.config;

import com.qh.ai_agent.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 应用启动后自动回填缺失的 dialog_analytics 记录
 * 修复因竞态条件导致持久化失败的历史数据
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataBackfillRunner implements ApplicationRunner {

    private final AnalyticsService analyticsService;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("[DataBackfill] 开始检查并回填缺失的 analytics 记录...");
        try {
            int count = analyticsService.backfillMissingAnalytics();
            log.info("[DataBackfill] 回填完成，共修复 {} 条记录", count);
        } catch (Exception e) {
            log.error("[DataBackfill] 回填过程中发生错误", e);
        }
    }
}
