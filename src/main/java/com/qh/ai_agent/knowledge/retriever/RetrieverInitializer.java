package com.qh.ai_agent.knowledge.retriever;

import com.qh.ai_agent.knowledge.KnowledgeReindexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 检索器初始化器。
 * 在应用启动完成后，重建主 BM25 索引（比赛知识库 + FAQ）以及 FAQ 独立 Lucene 索引。
 *
 * 实际构建逻辑统一收敛到 KnowledgeReindexService.rebuildBm25()，
 * 这样启动、管理员上传后的重建、手动 reindex 接口共用同一套流程。
 * 构建失败只记录日志，不影响应用启动。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RetrieverInitializer {

    private final KnowledgeReindexService knowledgeReindexService;

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        try {
            KnowledgeReindexService.StepResult result = knowledgeReindexService.rebuildBm25();
            if (result.success()) {
                log.info("[RetrieverInitializer] 检索索引初始化完成: {} ({}ms)",
                        result.message(), result.elapsedMs());
            } else {
                log.error("[RetrieverInitializer] 检索索引初始化失败: {}", result.message());
            }
        } catch (Exception e) {
            log.error("[RetrieverInitializer] 初始化异常，应用继续启动", e);
        }
    }
}