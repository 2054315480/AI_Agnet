package com.qh.ai_agent.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 图片对话请求模型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImageChatRequest {
    /**
     * 图片URL
     */
    private String imageUrl;

    /**
     * 问题
     */
    private String question;
}
