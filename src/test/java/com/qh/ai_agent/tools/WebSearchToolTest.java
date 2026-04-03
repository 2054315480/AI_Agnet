package com.qh.ai_agent.tools;

import com.qh.ai_agent.AiAgentApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * WebSearchTool 测试类
 */
@SpringBootTest(classes = AiAgentApplication.class)
public class WebSearchToolTest {

    @Autowired
    private WebSearchTool webSearchTool;

    @Test
    public void testWebSearch() {
        // 测试基本搜索
        String result = webSearchTool.webSearch("阿里巴巴2024年的esg报告");
        System.out.println("基本搜索结果:");
        System.out.println(result);
        System.out.println("\n" + "=".repeat(80) + "\n");
    }

    @Test
    public void testWebSearchWithParams() {
        // 测试带参数的搜索
        String result = webSearchTool.webSearchAdvanced("人工智能最新进展", 5, "oneWeek", true);
        System.out.println("带参数搜索结果:");
        System.out.println(result);
        System.out.println("\n" + "=".repeat(80) + "\n");
    }

    @Test
    public void testQuickSearch() {
        // 测试快速搜索
        String result = webSearchTool.quickSearch("Spring AI");
        System.out.println("快速搜索结果:");
        System.out.println(result);
        System.out.println("\n" + "=".repeat(80) + "\n");
    }

    @Test
    public void testFreshnessSearch() {
        // 测试时间过滤搜索
        String result = webSearchTool.webSearchAdvanced("AI技术新闻", 10, "oneDay", true);
        System.out.println("最新搜索结果（一天内）:");
        System.out.println(result);
        System.out.println("\n" + "=".repeat(80) + "\n");
    }

    @Test
    public void testSimpleSearch() {
        // 测试简单搜索
        String result = webSearchTool.webSearch("Java编程");
        System.out.println("简单搜索结果:");
        System.out.println(result);
        System.out.println("\n" + "=".repeat(80) + "\n");
    }
}
