package com.qh.imagesearchmcpserver.Tools;

import cn.hutool.core.util.URLUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 图片搜索工具 - 基于 Pexels API
 */
@Service
@Slf4j
public class ImageSearchTool {

    @Value("${pexels.api-key}")
    private String apiKey;

    @Tool(description = "根据关键词搜索高清图片，返回图片链接、摄影师信息、尺寸等详细信息")
    public String searchImage(
            @ToolParam(description = "搜索关键词，例如：nature、cat、city") String query,
            @ToolParam(required = false, description = "返回图片数量，默认5张，最大80张") Integer perPage
    ) {
        if (query == null || query.trim().isEmpty()) {
            return "搜索关键词不能为空";
        }

        int count = (perPage == null || perPage <= 0) ? 5 : Math.min(perPage, 80);

        String url = "https://api.pexels.com/v1/search?query="
                + URLUtil.encode(query.trim())
                + "&per_page=" + count
                + "&locale=zh-CN";

        log.info("搜索图片: query={}, perPage={}", query, count);

        try {
            String body = HttpRequest.get(url)
                    .header("Authorization", apiKey)
                    .timeout(10000)
                    .execute()
                    .body();

            JSONObject json = JSONUtil.parseObj(body);
            JSONArray photos = json.getJSONArray("photos");

            if (photos == null || photos.isEmpty()) {
                return "未找到与 \"" + query + "\" 相关的图片";
            }

            StringBuilder sb = new StringBuilder();
            int total = json.getInt("total_results", 0);
            sb.append("共找到 ").append(total).append(" 张与 \"").append(query).append("\" 相关的图片，展示前 ").append(photos.size()).append(" 张：\n\n");

            for (int i = 0; i < photos.size(); i++) {
                JSONObject photo = photos.getJSONObject(i);
                sb.append("图片").append(i + 1).append(":\n");
                sb.append("  描述: ").append(photo.getStr("alt", "无描述")).append("\n");
                sb.append("  摄影师: ").append(photo.getStr("photographer")).append("\n");
                sb.append("  尺寸: ").append(photo.getInt("width")).append("x").append(photo.getInt("height")).append("\n");
                sb.append("  主色调: ").append(photo.getStr("avg_color")).append("\n");
                JSONObject src = photo.getJSONObject("src");
                sb.append("  原图: ").append(src.getStr("original")).append("\n");
                sb.append("  大图: ").append(src.getStr("large")).append("\n");
                sb.append("  中图: ").append(src.getStr("medium")).append("\n");
                sb.append("  缩略图: ").append(src.getStr("small")).append("\n");
                sb.append("  详情页: ").append(photo.getStr("url")).append("\n\n");
            }

            return sb.toString();

        } catch (Exception e) {
            log.error("搜索图片失败: query={}", query, e);
            return "搜索图片失败: " + e.getMessage();
        }
    }
}
