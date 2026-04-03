package com.qh.ai_agent.tools;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.StreamProgress;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import com.qh.ai_agent.chatmemory.FileConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * 资源下载工具类
 * 使用 Hutool 的 HttpUtil 实现文件下载功能
 *
 * 支持功能：
 * 1. 简单文件下载
 * 2. 自定义文件名
 * 3. 带请求头的下载（如 User-Agent）
 * 4. 下载进度跟踪
 * 5. URL 内容获取（文本/HTML）
 * 6. 图片下载
 */
@Slf4j
@Component
public class ResourceDownloadTool {

    /**
     * 下载文件保存目录
     */
    private final String DOWNLOAD_DIR = FileConstant.FILE_SAVE_DIR + "/Download";

    /**
     * 连接超时时间（毫秒）
     */
    private static final int CONNECT_TIMEOUT = 30000;

    /**
     * 读取超时时间（毫秒）
     */
    private static final int READ_TIMEOUT = 60000;

    /**
     * 下载文件到默认目录
     *
     * @param url 资源URL
     * @return 下载结果信息
     */
    @Tool(description = "从指定URL下载文件到默认下载目录")
    public String downloadFile(
            @ToolParam(description = "要下载的文件URL") String url) {

        return downloadFileWithName(url, null);
    }

    /**
     * 下载文件到指定目录和文件名
     *
     * @param url 资源URL
     * @param fileName 自定义文件名（可选）
     * @return 下载结果信息
     */
    @Tool(description = "从指定URL下载文件，支持自定义文件名")
    public String downloadFileWithName(
            @ToolParam(description = "要下载的文件URL") String url,
            @ToolParam(description = "自定义文件名（可选，默认从URL中提取）") String fileName) {

        try {
            // 参数验证
            if (StrUtil.isBlank(url)) {
                return "下载失败: URL不能为空";
            }

            // 创建保存目录
            File dir = new File(DOWNLOAD_DIR);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            // 确定文件名
            String actualFileName = StrUtil.isNotBlank(fileName) ? fileName : getFileNameFromUrl(url);
            File targetFile = new File(dir, actualFileName);

            log.info("开始下载文件: URL={}, 保存路径={}", url, targetFile.getAbsolutePath());

            // 下载文件
            HttpUtil.downloadFile(url, targetFile);

            if (targetFile.exists()) {
                long fileSize = targetFile.length();
                String fileSizeStr = formatFileSize(fileSize);

                log.info("文件下载成功: {}, 大小: {}", targetFile.getAbsolutePath(), fileSizeStr);
                return String.format("下载成功！\n" +
                        "文件路径: %s\n" +
                        "文件大小: %s\n" +
                        "文件名: %s",
                        targetFile.getAbsolutePath(),
                        fileSizeStr,
                        targetFile.getName());
            } else {
                return "下载失败: 无法保存文件到指定目录";
            }

        } catch (Exception e) {
            log.error("文件下载异常", e);
            return "下载失败: " + e.getMessage();
        }
    }

    /**
     * 获取URL的内容（文本/HTML）
     *
     * @param url 资源URL
     * @return URL内容
     */
    @Tool(description = "获取指定URL的文本内容，适用于获取HTML页面、JSON数据等")
    public String fetchUrlContent(
            @ToolParam(description = "要获取内容的URL") String url) {

        try {
            if (StrUtil.isBlank(url)) {
                return "URL不能为空";
            }

            log.info("获取URL内容: {}", url);

            String content = HttpUtil.get(url, CONNECT_TIMEOUT);

            if (content != null && !content.isEmpty()) {
                // 限制返回内容长度，避免过长
                if (content.length() > 10000) {
                    content = content.substring(0, 10000) + "\n\n... (内容过长，已截断)";
                }
                return String.format("成功获取内容（长度: %d 字符）:\n\n%s",
                        content.length(), content);
            } else {
                return "获取内容失败: 返回内容为空";
            }

        } catch (Exception e) {
            log.error("获取URL内容异常", e);
            return "获取内容失败: " + e.getMessage();
        }
    }

    /**
     * 获取URL的内容（带自定义请求头）
     *
     * @param url 资源URL
     * @param headers 请求头（格式：key:value，多个用分号分隔）
     * @return URL内容
     */
    @Tool(description = "获取指定URL的文本内容，支持自定义请求头（如User-Agent）")
    public String fetchUrlContentWithHeaders(
            @ToolParam(description = "要获取内容的URL") String url,
            @ToolParam(description = "自定义请求头，格式：key:value，多个用分号分隔，如：User-Agent:Mozilla;Accept:text/html") String headers) {

        try {
            if (StrUtil.isBlank(url)) {
                return "URL不能为空";
            }

            log.info("获取URL内容（带请求头）: {}", url);

            // 解析请求头
            Map<String, String> headerMap = new HashMap<>();
            if (StrUtil.isNotBlank(headers)) {
                String[] headerArray = headers.split(";");
                for (String header : headerArray) {
                    String[] kv = header.split(":", 2);
                    if (kv.length == 2) {
                        headerMap.put(kv[0].trim(), kv[1].trim());
                    }
                }
            }

            // 发送请求
            HttpResponse response = HttpRequest.get(url)
                    .timeout(CONNECT_TIMEOUT)
                    .addHeaders(headerMap)
                    .execute();

            String content = response.body();

            if (content != null && !content.isEmpty()) {
                // 限制返回内容长度
                if (content.length() > 10000) {
                    content = content.substring(0, 10000) + "\n\n... (内容过长，已截断)";
                }
                return String.format("成功获取内容（状态码: %d, 长度: %d 字符）:\n\n%s",
                        response.getStatus(), content.length(), content);
            } else {
                return "获取内容失败: 返回内容为空";
            }

        } catch (Exception e) {
            log.error("获取URL内容异常", e);
            return "获取内容失败: " + e.getMessage();
        }
    }

    /**
     * 下载图片
     *
     * @param imageUrl 图片URL
     * @return 下载结果信息
     */
    @Tool(description = "下载图片文件到默认下载目录的images子目录")
    public String downloadImage(
            @ToolParam(description = "图片URL") String imageUrl) {

        try {
            if (StrUtil.isBlank(imageUrl)) {
                return "图片URL不能为空";
            }

            // 创建图片保存目录
            File imageDir = new File(DOWNLOAD_DIR, "images");
            if (!imageDir.exists()) {
                imageDir.mkdirs();
            }

            log.info("开始下载图片: {}", imageUrl);

            String fileName = getFileNameFromUrl(imageUrl);
            File targetFile = new File(imageDir, fileName);

            HttpUtil.downloadFile(imageUrl, targetFile);

            if (targetFile.exists()) {
                long fileSize = targetFile.length();
                String fileSizeStr = formatFileSize(fileSize);

                log.info("图片下载成功: {}, 大小: {}", targetFile.getAbsolutePath(), fileSizeStr);
                return String.format("图片下载成功！\n" +
                        "文件路径: %s\n" +
                        "文件大小: %s\n" +
                        "文件名: %s",
                        targetFile.getAbsolutePath(),
                        fileSizeStr,
                        targetFile.getName());
            } else {
                return "图片下载失败";
            }

        } catch (Exception e) {
            log.error("图片下载异常", e);
            return "图片下载失败: " + e.getMessage();
        }
    }

    /**
     * 下载文件并显示进度
     *
     * @param url 资源URL
     * @param fileName 保存文件名
     * @return 下载结果信息
     */
    @Tool(description = "下载文件并显示下载进度信息")
    public String downloadWithProgress(
            @ToolParam(description = "要下载的文件URL") String url,
            @ToolParam(description = "保存的文件名") String fileName) {

        try {
            if (StrUtil.isBlank(url) || StrUtil.isBlank(fileName)) {
                return "URL和文件名不能为空";
            }

            File dir = new File(DOWNLOAD_DIR);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            File targetFile = new File(dir, fileName);

            log.info("开始下载文件（显示进度）: {}", targetFile.getAbsolutePath());

            // 使用带进度监控的下载
            HttpUtil.downloadFile(url, targetFile, new StreamProgress() {
                @Override
                public void start() {
                    log.info("开始下载: {}", fileName);
                }

                @Override
                public void progress(long progressSize, long dataSize) {
                    double percent = dataSize > 0 ? (double) progressSize / dataSize * 100 : 0;
                    log.info("下载进度: {}/{} ({}%)", formatFileSize(progressSize),
                            formatFileSize(dataSize), String.format("%.2f", percent));
                }

                @Override
                public void finish() {
                    log.info("下载完成: {}", fileName);
                }
            });

            if (targetFile.exists()) {
                long fileSize = targetFile.length();
                return String.format("下载成功！\n" +
                        "文件路径: %s\n" +
                        "文件大小: %s\n" +
                        "文件名: %s",
                        targetFile.getAbsolutePath(),
                        formatFileSize(fileSize),
                        targetFile.getName());
            } else {
                return "下载失败: 文件未保存成功";
            }

        } catch (Exception e) {
            log.error("文件下载异常", e);
            return "下载失败: " + e.getMessage();
        }
    }

    /**
     * 获取文件信息（不下载，只获取元数据）
     *
     * @param url 资源URL
     * @return 文件信息
     */
    @Tool(description = "获取远程文件的信息，如文件大小、Content-Type等，不实际下载文件")
    public String getFileInfo(
            @ToolParam(description = "要获取信息的文件URL") String url) {

        try {
            if (StrUtil.isBlank(url)) {
                return "URL不能为空";
            }

            HttpResponse response = HttpRequest.head(url)
                    .timeout(CONNECT_TIMEOUT)
                    .execute();

            StringBuilder info = new StringBuilder();
            info.append("远程文件信息:\n");
            info.append("URL: ").append(url).append("\n");
            info.append("状态码: ").append(response.getStatus()).append("\n");

            // Content-Type
            String contentType = response.header("Content-Type");
            if (StrUtil.isNotBlank(contentType)) {
                info.append("Content-Type: ").append(contentType).append("\n");
            }

            // Content-Length
            String contentLength = response.header("Content-Length");
            if (StrUtil.isNotBlank(contentLength)) {
                try {
                    long size = Long.parseLong(contentLength);
                    info.append("Content-Length: ").append(contentLength)
                            .append(" (").append(formatFileSize(size)).append(")\n");
                } catch (NumberFormatException e) {
                    info.append("Content-Length: ").append(contentLength).append("\n");
                }
            }

            // Content-Disposition（文件名）
            String disposition = response.header("Content-Disposition");
            if (StrUtil.isNotBlank(disposition)) {
                info.append("Content-Disposition: ").append(disposition).append("\n");
            }

            // Last-Modified
            String lastModified = response.header("Last-Modified");
            if (StrUtil.isNotBlank(lastModified)) {
                info.append("Last-Modified: ").append(lastModified).append("\n");
            }

            return info.toString();

        } catch (Exception e) {
            log.error("获取文件信息异常", e);
            return "获取文件信息失败: " + e.getMessage();
        }
    }

    /**
     * 批量下载文件
     *
     * @param urls URL列表，用逗号分隔
     * @return 批量下载结果
     */
    @Tool(description = "批量下载多个文件，URL用逗号分隔")
    public String batchDownload(
            @ToolParam(description = "要下载的文件URL列表，用逗号分隔") String urls) {

        if (StrUtil.isBlank(urls)) {
            return "URL列表不能为空";
        }

        String[] urlArray = urls.split(",");
        StringBuilder result = new StringBuilder();
        result.append("批量下载结果:\n\n");

        int successCount = 0;
        int failCount = 0;

        for (String url : urlArray) {
            url = url.trim();
            if (StrUtil.isBlank(url)) {
                continue;
            }

            String downloadResult = downloadFile(url);
            result.append("- ").append(url).append("\n");

            if (downloadResult.startsWith("下载成功")) {
                successCount++;
                result.append("  ✓ 成功\n");
            } else {
                failCount++;
                result.append("  ✗ 失败: ").append(downloadResult).append("\n");
            }
            result.append("\n");
        }

        result.append(String.format("总计: %d 个文件, 成功: %d, 失败: %d",
                urlArray.length, successCount, failCount));

        return result.toString();
    }

    /**
     * 格式化文件大小
     */
    private String formatFileSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.2f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", size / (1024.0 * 1024));
        } else {
            return String.format("%.2f GB", size / (1024.0 * 1024 * 1024));
        }
    }

    /**
     * 从URL中提取文件名
     */
    private String getFileNameFromUrl(String url) {
        try {
            String fileName = url.substring(url.lastIndexOf("/") + 1);
            // 移除查询参数
            int queryIndex = fileName.indexOf("?");
            if (queryIndex > 0) {
                fileName = fileName.substring(0, queryIndex);
            }
            // 如果文件名为空，使用默认名
            if (StrUtil.isBlank(fileName)) {
                fileName = "downloaded_file_" + System.currentTimeMillis();
            }
            return fileName;
        } catch (Exception e) {
            return "downloaded_file_" + System.currentTimeMillis();
        }
    }

    /**
     * 获取默认下载目录路径
     */
    @Tool(description = "获取当前默认下载目录的绝对路径")
    public String getDownloadDirectory() {
        File dir = new File(DOWNLOAD_DIR);
        return "默认下载目录: " + dir.getAbsolutePath();
    }
}
