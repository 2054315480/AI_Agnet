package com.qh.ai_agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 博查AI搜索配置类
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "ai.bocha")
public class BochaSearchConfig {

    /**
     * 博查AI的API Key
     */
    private String apiKey;

    /**
     * 博查AI的API URL
     */
    private String apiUrl = "https://api.bocha.cn/v1/web-search";
}
