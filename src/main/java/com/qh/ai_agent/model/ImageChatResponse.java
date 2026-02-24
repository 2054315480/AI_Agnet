package com.qh.ai_agent.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 图片对话响应模型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImageChatResponse {
    /**
     * AI生成的内容
     */
    private String content;

    /**
     * 时间戳
     */
    private LocalDateTime timestamp;

    public static ImageChatResponse success(String content) {
        return new ImageChatResponse(content, LocalDateTime.now());
    }
}
