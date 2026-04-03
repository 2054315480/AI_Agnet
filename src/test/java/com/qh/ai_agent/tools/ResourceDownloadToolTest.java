package com.qh.ai_agent.tools;

import com.qh.ai_agent.AiAgentApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * ResourceDownloadTool 测试类
 */
@SpringBootTest(classes = AiAgentApplication.class)
public class ResourceDownloadToolTest {

    @Autowired
    private ResourceDownloadTool resourceDownloadTool;

    @Test
    public void testGetDownloadDirectory() {
        // 测试获取下载目录
        String result = resourceDownloadTool.getDownloadDirectory();
        System.out.println("下载目录信息:");
        System.out.println(result);
        System.out.println("\n" + "=".repeat(80) + "\n");
    }

    @Test
    public void testGetFileInfo() {
        // 测试获取文件信息（不下载）
        String result = resourceDownloadTool.getFileInfo("https://www.baidu.com/img/flexible/logo/pc/result.png");
        System.out.println("获取文件信息:");
        System.out.println(result);
        System.out.println("\n" + "=".repeat(80) + "\n");
    }

    @Test
    public void testFetchUrlContent() {
        // 测试获取URL内容
        String result = resourceDownloadTool.fetchUrlContent("https://www.baidu.com");
        System.out.println("获取URL内容:");
        System.out.println(result);
        System.out.println("\n" + "=".repeat(80) + "\n");
    }

    @Test
    public void testFetchUrlContentWithHeaders() {
        // 测试带请求头获取内容
        String result = resourceDownloadTool.fetchUrlContentWithHeaders(
                "https://www.baidu.com",
                "User-Agent:Mozilla/5.0;Accept:text/html,application/xhtml+xml"
        );
        System.out.println("带请求头获取URL内容:");
        System.out.println(result);
        System.out.println("\n" + "=".repeat(80) + "\n");
    }

    @Test
    public void testDownloadFile() {
        // 测试下载文件
        String result = resourceDownloadTool.downloadFile(
                "https://www.baidu.com/img/flexible/logo/pc/result.png"
        );
        System.out.println("下载文件结果:");
        System.out.println(result);
        System.out.println("\n" + "=".repeat(80) + "\n");
    }

    @Test
    public void testDownloadFileWithFileName() {
        // 测试下载文件并自定义文件名
        String result = resourceDownloadTool.downloadFileWithName(
                "https://www.baidu.com/img/flexible/logo/pc/result.png",
                "baidu-logo.png"
        );
        System.out.println("下载文件（自定义文件名）结果:");
        System.out.println(result);
        System.out.println("\n" + "=".repeat(80) + "\n");
    }

    @Test
    public void testDownloadImage() {
        // 测试下载图片到images子目录
        String result = resourceDownloadTool.downloadImage(
                "https://www.baidu.com/img/flexible/logo/pc/result.png"
        );
        System.out.println("下载图片结果:");
        System.out.println(result);
        System.out.println("\n" + "=".repeat(80) + "\n");
    }

    @Test
    public void testDownloadWithProgress() {
        // 测试带进度的下载
        String result = resourceDownloadTool.downloadWithProgress(
                "https://www.baidu.com/img/flexible/logo/pc/result.png",
                "baidu-logo-with-progress.png"
        );
        System.out.println("带进度的下载结果:");
        System.out.println(result);
        System.out.println("\n" + "=".repeat(80) + "\n");
    }

    @Test
    public void testBatchDownload() {
        // 测试批量下载
        String result = resourceDownloadTool.batchDownload(
                "https://www.baidu.com/img/flexible/logo/pc/result.png"
        );
        System.out.println("批量下载结果:");
        System.out.println(result);
        System.out.println("\n" + "=".repeat(80) + "\n");
    }

    @Test
    public void testDownloadJsonData() {
        // 测试获取JSON数据
        String result = resourceDownloadTool.fetchUrlContent(
                "https://api.github.com/users/github"
        );
        System.out.println("获取JSON数据:");
        System.out.println(result);
        System.out.println("\n" + "=".repeat(80) + "\n");
    }
}
