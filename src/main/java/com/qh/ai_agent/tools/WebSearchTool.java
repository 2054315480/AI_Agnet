package com.qh.ai_agent.tools;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qh.ai_agent.config.BochaSearchConfig;
import com.qh.ai_agent.entity.bocha.BochaSearchRequest;
import com.qh.ai_agent.entity.bocha.BochaSearchResponse;
import com.qh.ai_agent.entity.bocha.BochaSearchResponse.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/**
 * 网页搜索工具类
 * 使用博查AI API 进行网页搜索
 */
@Slf4j
@Component
public class WebSearchTool {

    @Autowired
    private BochaSearchConfig bochaSearchConfig;

    /**
     * 配置Jackson ObjectMapper，忽略未知字段
     */
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final String DEFAULT_FRESHNESS = "noLimit";
    private static final Integer DEFAULT_COUNT = 10;
    private static final Boolean DEFAULT_SUMMARY = true;

    /**
     * 执行网页搜索
     *
     * @param query 搜索关键字或语句
     * @return 搜索结果的格式化字符串
     */
    @Tool(description = "在互联网上搜索信息，包括新闻、图片、百科、文库等内容")
    public String webSearch(
            @ToolParam(description = "搜索的关键词或问题") String query) {

        return webSearch(query, DEFAULT_COUNT, DEFAULT_FRESHNESS, DEFAULT_SUMMARY);
    }

    /**
     * 执行网页搜索（带参数）
     *
     * @param query 搜索关键字或语句
     * @param count 返回的搜索结果数量（1-50）
     * @param freshness 时间范围过滤（noLimit、oneDay、oneWeek、oneMonth、oneYear）
     * @param summary 是否包含摘要
     * @return 搜索结果的格式化字符串
     */
    @Tool(description = "在互联网上搜索信息，支持自定义结果数量、时间范围和摘要选项")
    public String webSearch(
            @ToolParam(description = "搜索的关键词或问题") String query,
            @ToolParam(description = "返回的搜索结果数量（1-50）") Integer count,
            @ToolParam(description = "时间范围过滤（noLimit、oneDay、oneWeek、oneMonth、oneYear）") String freshness,
            @ToolParam(description = "是否包含摘要") Boolean summary) {

        try {
            // 构建请求
            BochaSearchRequest request = new BochaSearchRequest();
            request.setQuery(query);
            request.setCount(count != null && count > 0 && count <= 50 ? count : DEFAULT_COUNT);
            request.setFreshness(freshness != null ? freshness : DEFAULT_FRESHNESS);
            request.setSummary(summary != null ? summary : DEFAULT_SUMMARY);

            // 发送请求
            HttpResponse response = HttpRequest.post(bochaSearchConfig.getApiUrl())
                    .header("Authorization", "Bearer " + bochaSearchConfig.getApiKey())
                    .header("Content-Type", "application/json")
                    .body(JSONUtil.toJsonStr(request))
                    .execute();

            String responseBody = response.body();
            log.info("博查AI搜索响应: {}", responseBody);

            // 解析响应
            BochaSearchResponse searchResponse = objectMapper.readValue(responseBody, BochaSearchResponse.class);

            // 检查响应状态
            if (searchResponse.getCode() != null && searchResponse.getCode() == 200) {
                return formatSearchResult(searchResponse);
            } else {
                return "搜索失败: " + (searchResponse.getMsg() != null ? searchResponse.getMsg() : "未知错误");
            }

        } catch (Exception e) {
            log.error("网页搜索异常", e);
            return "搜索过程中发生异常: " + e.getMessage();
        }
    }

    /**
     * 格式化搜索结果
     *
     * @param searchResponse 搜索响应对象
     * @return 格式化后的搜索结果字符串
     */
    private String formatSearchResult(BochaSearchResponse searchResponse) {
        StringBuilder result = new StringBuilder();

        SearchData data = searchResponse.getData();
        if (data == null) {
            return "搜索结果为空";
        }

        // 添加查询信息
        if (data.getQueryContext() != null) {
            result.append("搜索关键词: ").append(data.getQueryContext().getOriginalQuery()).append("\n\n");
        }

        // 添加网页搜索结果
        WebPages webPages = data.getWebPages();
        if (webPages != null && webPages.getValue() != null && !webPages.getValue().isEmpty()) {
            result.append("找到约 ").append(webPages.getTotalEstimatedMatches())
                    .append(" 条结果（前").append(webPages.getValue().size()).append("条）:\n\n");

            int index = 1;
            for (WebPage page : webPages.getValue()) {
                result.append(index).append(". ").append(page.getName()).append("\n");
                result.append("   链接: ").append(page.getUrl()).append("\n");

                if (page.getSnippet() != null && !page.getSnippet().isEmpty()) {
                    result.append("   摘要: ").append(page.getSnippet()).append("\n");
                }

                if (page.getSummary() != null && !page.getSummary().isEmpty()) {
                    result.append("   详细摘要: ").append(page.getSummary()).append("\n");
                }

                if (page.getSiteName() != null) {
                    result.append("   来源: ").append(page.getSiteName()).append("\n");
                }

                if (page.getDateLastCrawled() != null) {
                    result.append("   收录时间: ").append(page.getDateLastCrawled()).append("\n");
                }

                result.append("\n");
                index++;
            }
        }

        // 添加图片搜索结果（如果有）
        Images images = data.getImages();
        if (images != null && images.getValue() != null && !images.getValue().isEmpty()) {
            result.append("相关图片（前5张）:\n");
            images.getValue().stream()
                    .limit(5)
                    .forEach(img -> {
                        result.append("  - ").append(img.getContentUrl()).append("\n");
                        if (img.getHostPageUrl() != null) {
                            result.append("    来源: ").append(img.getHostPageUrl()).append("\n");
                        }
                    });
            result.append("\n");
        }

        return result.toString();
    }

    /**
     * 简化的搜索方法，只返回网页标题和链接
     *
     * @param query 搜索关键字
     * @return 简化的搜索结果
     */
    @Tool(description = "快速搜索，只返回网页标题和链接列表")
    public String quickSearch(
            @ToolParam(description = "搜索的关键词") String query) {

        try {
            BochaSearchRequest request = new BochaSearchRequest();
            request.setQuery(query);
            request.setCount(10);
            request.setFreshness(DEFAULT_FRESHNESS);
            request.setSummary(false);

            HttpResponse response = HttpRequest.post(bochaSearchConfig.getApiUrl())
                    .header("Authorization", "Bearer " + bochaSearchConfig.getApiKey())
                    .header("Content-Type", "application/json")
                    .body(JSONUtil.toJsonStr(request))
                    .execute();

            String responseBody = response.body();
            BochaSearchResponse searchResponse = objectMapper.readValue(responseBody, BochaSearchResponse.class);

            if (searchResponse.getCode() != null && searchResponse.getCode() == 200 &&
                searchResponse.getData() != null &&
                searchResponse.getData().getWebPages() != null &&
                searchResponse.getData().getWebPages().getValue() != null) {

                return searchResponse.getData().getWebPages().getValue().stream()
                        .map(page -> page.getName() + " - " + page.getUrl())
                        .collect(Collectors.joining("\n"));
            } else {
                return "搜索失败: " + (searchResponse.getMsg() != null ? searchResponse.getMsg() : "未知错误");
            }

        } catch (Exception e) {
            log.error("快速搜索异常", e);
            return "搜索过程中发生异常: " + e.getMessage();
        }
    }
}
