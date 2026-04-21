package com.qh.ai_agent.rag.model;

import lombok.Data;

@Data
public class RagRequest {
    private String message;
    private String chatId;
    private String status;
    private String sourceType;
    private String category;
    private boolean useHybrid;
    private boolean useCheapTransformer;
}
