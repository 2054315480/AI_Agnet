package com.qh.ai_agent.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

/**
 * 图片对话配置
 */
@Configuration
@Slf4j
public class ImageChatConfig {
    // 使用默认的 ChatModel 自动配置
    // 多模态功能通过 ChatClient 的 media API 实现
}
