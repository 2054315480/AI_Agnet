package com.qh.ai_agent.tools;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 *  集中的工具注册类
 */
@Configuration
public class ToolRegistration {

    @Bean
    public ToolCallback[] allTools(
            FileOperationTool fileOperationTool,
            WebSearchTool webSearchTool,
            WebScrapingTool webScrapingTool,
            ResourceDownloadTool resourceDownloadTool,
            TerminalOperationTool terminalOperationTool,
            TerminateTool terminateTool,
            PDFGenerationTool pdfGenerationTool) {
        return ToolCallbacks.from(
                fileOperationTool,
                webSearchTool,
                webScrapingTool,
                resourceDownloadTool,
                terminalOperationTool,
                terminateTool,
                pdfGenerationTool
                );
    }

}
