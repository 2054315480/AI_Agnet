package com.qh.ai_agent.rag;


import com.networknt.schema.Keyword;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.model.transformer.KeywordMetadataEnricher;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MykeywordEnricher {

    @Resource
    private ChatModel dashscopeChatModel;

    /**
     *基于AI的文档源信息增强器，为文档补充源信息
     */
    public List<Document> enrichDocuments( List<Document> documents) {
        KeywordMetadataEnricher keywordMetadataEnricher = new KeywordMetadataEnricher(dashscopeChatModel, 5);
        return keywordMetadataEnricher.apply(documents);
    }
}
