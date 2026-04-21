package com.qh.ai_agent.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;

@Slf4j
@Component
public class PdfParseTool {

    @Tool(description = "解析 PDF 文件并提取全部文本内容")
    public String parsePdf(@ToolParam(description = "PDF 文件的本地路径") String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            return "文件不存在: " + filePath;
        }
        if (!file.getName().toLowerCase().endsWith(".pdf")) {
            return "不是 PDF 文件: " + filePath;
        }
        try (org.apache.pdfbox.pdmodel.PDDocument document = org.apache.pdfbox.pdmodel.PDDocument.load(file)) {
            org.apache.pdfbox.text.PDFTextStripper stripper = new org.apache.pdfbox.text.PDFTextStripper();
            String text = stripper.getText(document);
            if (text == null || text.isBlank()) {
                return "PDF 中未提取到文本内容（可能是扫描件或图片 PDF）";
            }
            log.info("PDF 解析成功: {} 页, 文本长度: {}", document.getNumberOfPages(), text.length());
            return text;
        } catch (IOException e) {
            log.error("PDF 解析失败: {}", e.getMessage());
            return "解析失败: " + e.getMessage();
        }
    }

    @Tool(description = "获取 PDF 文件的基本信息（页数、文件大小、是否加密）")
    public String getPdfInfo(@ToolParam(description = "PDF 文件的本地路径") String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            return "文件不存在: " + filePath;
        }
        try (org.apache.pdfbox.pdmodel.PDDocument document = org.apache.pdfbox.pdmodel.PDDocument.load(file)) {
            StringBuilder info = new StringBuilder();
            info.append("文件: ").append(file.getName()).append("\n");
            info.append("页数: ").append(document.getNumberOfPages()).append("\n");
            info.append("文件大小: ").append(formatSize(file.length())).append("\n");
            info.append("是否加密: ").append(document.isEncrypted() ? "是" : "否");
            org.apache.pdfbox.pdmodel.PDDocumentInformation meta = document.getDocumentInformation();
            if (meta != null) {
                if (meta.getTitle() != null) info.append("\n标题: ").append(meta.getTitle());
                if (meta.getAuthor() != null) info.append("\n作者: ").append(meta.getAuthor());
            }
            return info.toString();
        } catch (IOException e) {
            return "读取失败: " + e.getMessage();
        }
    }

    @Tool(description = "提取 PDF 文件中指定页码的文本内容")
    public String extractPdfPage(
            @ToolParam(description = "PDF 文件的本地路径") String filePath,
            @ToolParam(description = "页码（从 1 开始）") int pageNum) {
        File file = new File(filePath);
        if (!file.exists()) {
            return "文件不存在: " + filePath;
        }
        try (org.apache.pdfbox.pdmodel.PDDocument document = org.apache.pdfbox.pdmodel.PDDocument.load(file)) {
            int totalPages = document.getNumberOfPages();
            if (pageNum < 1 || pageNum > totalPages) {
                return "页码超出范围，共 " + totalPages + " 页";
            }
            org.apache.pdfbox.text.PDFTextStripper stripper = new org.apache.pdfbox.text.PDFTextStripper();
            stripper.setStartPage(pageNum);
            stripper.setEndPage(pageNum);
            String text = stripper.getText(document);
            return text != null ? text : "该页无文本内容";
        } catch (IOException e) {
            return "解析失败: " + e.getMessage();
        }
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
