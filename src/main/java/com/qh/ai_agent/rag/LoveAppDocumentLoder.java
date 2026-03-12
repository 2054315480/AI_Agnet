package com.qh.ai_agent.rag;


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

    public LoveAppDocumentLoder(ResourcePatternResolver resourcePatternResolver) {

        this.resourcePatternResolver = resourcePatternResolver;

    }
    /*
    加载Markdown
     */
    public List<Document> loadMarkdown(){
        List<Document>alldocuments=new ArrayList<>();
        // 加载多篇markdown 文档
        try {

           Resource[] resources=resourcePatternResolver.getResources("classpath:docs/*.md");
           for(Resource resource:resources){
               String filename = resource.getFilename();
               MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                       .withHorizontalRuleCreateDocument(true)
                       .withIncludeCodeBlock(false)
                       .withIncludeBlockquote(false)
                       .withAdditionalMetadata("filename", filename)
                       .build();
               MarkdownDocumentReader markdownDocumentReader = new MarkdownDocumentReader(resource, config);
               alldocuments.addAll(markdownDocumentReader.read());
           }
        } catch (IOException e) {
            log.error("Markdown 加载失败", e);
        }
        return  alldocuments;
    }
}
