package com.qh.ai_agent.knowledge.retriever;

import com.qh.ai_agent.knowledge.parser.KbEntry;
import com.qh.ai_agent.knowledge.parser.UnifiedKbLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 检索器初始化器。
 * 在应用启动完成后，加载比赛知识库并构建 BM25 索引。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RetrieverInitializer {

    private final UnifiedKbLoader unifiedKbLoader;
    private final Bm25Retriever bm25Retriever;

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        try {
            List<KbEntry> entries = unifiedKbLoader.loadAll();
            if (entries.isEmpty()) {
                log.warn("[RetrieverInitializer] 未加载到任何知识库条目，BM25 索引跳过");
                return;
            }
            bm25Retriever.buildIndex(entries, "./kb-lucene-index");
            log.info("[RetrieverInitializer] BM25 索引初始化完成: {} 条", entries.size());
        } catch (Exception e) {
            log.error("[RetrieverInitializer] 初始化失败", e);
        }
    }
}
