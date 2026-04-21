package com.qh.ai_agent.rag;


import com.qh.ai_agent.rag.enricher.DocumentMetadataEnricher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 恋爱大师应用文挡加载器
 */
@Component
@Slf4j
public class LoveAppDocumentLoder {

    private final ResourcePatternResolver resourcePatternResolver;
    private final DocumentMetadataEnricher metadataEnricher;

    public LoveAppDocumentLoder(ResourcePatternResolver resourcePatternResolver,
                                DocumentMetadataEnricher metadataEnricher) {
        this.resourcePatternResolver = resourcePatternResolver;
        this.metadataEnricher = metadataEnricher;
    }
    /*
    加载Markdown
     */
    public List<Document> loadMarkdown(){
        List<Document> alldocuments = new ArrayList<>();
        try {
            Resource[] resources = resourcePatternResolver.getResources("classpath:docs/*.md");
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename.contains("故障") || filename.contains("记录") || filename.contains("云RAG")) {
                    log.info("跳过非知识库文档: {}", filename);
                    continue;
                }

                // 安全提取 status
                String filename2 = resource.getFilename();  // 与 filename 相同，可直接用 filename
                int dashIndex = filename2.indexOf("-");
                int pianIndex = filename2.indexOf("篇");
                String status;
                if (dashIndex != -1 && pianIndex != -1 && dashIndex < pianIndex) {
                    status = filename2.substring(dashIndex + 1, pianIndex);
                } else {
                    log.warn("文件名 {} 不符合 'X-状态篇.md' 格式，status 使用默认值 'general'", filename2);
                    status = "general";
                }

                MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                        .withHorizontalRuleCreateDocument(true)
                        .withIncludeCodeBlock(false)
                        .withIncludeBlockquote(false)
                        .withAdditionalMetadata("filename", filename)
                        .withAdditionalMetadata("status", status)
                        .build();
                MarkdownDocumentReader markdownDocumentReader = new MarkdownDocumentReader(resource, config);
                alldocuments.addAll(markdownDocumentReader.read());
            }
        } catch (IOException e) {
            log.error("Markdown 加载失败", e);
        }
        // 元信息丰富
        metadataEnricher.enrichDocuments(alldocuments);
        return alldocuments;
    }
}
