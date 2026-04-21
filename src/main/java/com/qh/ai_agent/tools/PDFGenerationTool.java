package com.qh.ai_agent.tools;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.qh.ai_agent.chatmemory.FileConstant;
import com.qh.ai_agent.service.OssService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * PDF生成工具类
 * 使用 iText 库实现 PDF 文档生成功能
 *
 * 支持功能：
 * 1. 创建基本 PDF 文档
 * 2. 添加文本段落
 * 3. 添加标题
 * 4. 添加表格
 * 5. 添加图片
 * 6. 添加列表
 * 7. 设置页面属性
 * 8. 中文支持
 */
@Slf4j
@Component
public class PDFGenerationTool {

    private final OssService ossService;

    public PDFGenerationTool(OssService ossService) {
        this.ossService = ossService;
    }

    private final String PDF_DIR = FileConstant.FILE_SAVE_DIR + "/PDF";

    private String buildResult(File file) {
        StringBuilder sb = new StringBuilder();
        sb.append("PDF创建成功！\n");
        if (ossService.isEnabled()) {
            try {
                String ossUrl = ossService.uploadFile("pdf/" + file.getName(), file);
                sb.append("下载链接: ").append(ossUrl).append("\n");
            } catch (Exception e) {
                log.warn("OSS 上传失败，返回本地路径: {}", e.getMessage());
            }
        }
        sb.append("本地路径: ").append(file.getAbsolutePath()).append("\n");
        sb.append("文件大小: ").append(formatFileSize(file.length()));
        return sb.toString();
    }

    /**
     * 创建简单的 PDF 文档
     *
     * @param fileName 文件名
     * @param title 文档标题
     * @param content 文档内容
     * @return 生成结果
     */
    @Tool(description = "创建一个简单的PDF文档，包含标题和内容")
    public String createSimplePdf(
            @ToolParam(description = "PDF文件名（不需要扩展名）") String fileName,
            @ToolParam(description = "文档标题") String title,
            @ToolParam(description = "文档内容，支持多行文本") String content) {

        try {
            File dir = new File(PDF_DIR);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            String filePath = PDF_DIR + "/" + fileName + ".pdf";
            File file = new File(filePath);

            PdfWriter writer = new PdfWriter(file);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf);

            PdfFont font = getChineseFont();

            // 添加标题
            if (StrUtil.isNotBlank(title)) {
                Paragraph titleParagraph = new Paragraph()
                        .add(new Text(title).setFont(font).setFontSize(24))
                        .setTextAlignment(TextAlignment.CENTER)
                        .setMarginBottom(20);
                document.add(titleParagraph);
            }

            // 添加内容
            if (StrUtil.isNotBlank(content)) {
                String[] paragraphs = content.split("\n");
                for (String para : paragraphs) {
                    if (StrUtil.isNotBlank(para)) {
                        Paragraph contentParagraph = new Paragraph()
                                .add(new Text(para).setFont(font).setFontSize(12))
                                .setMarginBottom(10);
                        document.add(contentParagraph);
                    }
                }
            }

            document.close();

            log.info("PDF文档创建成功: {}", filePath);
            return buildResult(file);

        } catch (Exception e) {
            log.error("创建PDF文档失败", e);
            return "创建PDF文档失败: " + e.getMessage();
        }
    }

    /**
     * 创建带表格的 PDF 文档
     *
     * @param fileName 文件名
     * @param title 文档标题
     * @param headers 表头（用逗号分隔）
     * @param rows 表格数据（每行用逗号分隔，行与行用分号分隔）
     * @return 生成结果
     */
    @Tool(description = "创建包含表格的PDF文档")
    public String createPdfWithTable(
            @ToolParam(description = "PDF文件名（不需要扩展名）") String fileName,
            @ToolParam(description = "文档标题") String title,
            @ToolParam(description = "表头，用逗号分隔，如：姓名,年龄,职位") String headers,
            @ToolParam(description = "表格数据，每行用逗号分隔，行与行用分号分隔，如：张三,25,工程师;李四,30,经理") String rows) {

        try {
            File dir = new File(PDF_DIR);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            String filePath = PDF_DIR + "/" + fileName + ".pdf";
            File file = new File(filePath);

            PdfWriter writer = new PdfWriter(file);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf);

            PdfFont font = getChineseFont();

            // 添加标题
            if (StrUtil.isNotBlank(title)) {
                document.add(new Paragraph()
                        .add(new Text(title).setFont(font).setFontSize(20))
                        .setTextAlignment(TextAlignment.CENTER)
                        .setMarginBottom(20));
            }

            // 创建表格
            if (StrUtil.isNotBlank(headers)) {
                String[] headerArray = headers.split(",");
                Table table = new Table(UnitValue.createPercentArray(headerArray.length))
                        .useAllAvailableWidth();

                // 添加表头
                for (String header : headerArray) {
                    Cell headerCell = new Cell()
                            .add(new Paragraph().add(new Text(header.trim()).setFont(font)))
                            .setBackgroundColor(ColorConstants.LIGHT_GRAY);
                    table.addHeaderCell(headerCell);
                }

                // 添加数据行
                if (StrUtil.isNotBlank(rows)) {
                    String[] rowArray = rows.split(";");
                    for (String row : rowArray) {
                        String[] cells = row.split(",");
                        for (String cell : cells) {
                            table.addCell(new Cell().add(new Paragraph()
                                    .add(new Text(cell.trim()).setFont(font))));
                        }
                    }
                }

                document.add(table);
            }

            document.close();

            log.info("带表格的PDF文档创建成功: {}", filePath);
            return buildResult(file);

        } catch (Exception e) {
            log.error("创建带表格的PDF文档失败", e);
            return "创建PDF文档失败: " + e.getMessage();
        }
    }

    /**
     * 创建带列表的 PDF 文档
     *
     * @param fileName 文件名
     * @param title 文档标题
     * @param items 列表项（用逗号分隔）
     * @return 生成结果
     */
    @Tool(description = "创建包含列表的PDF文档")
    public String createPdfWithList(
            @ToolParam(description = "PDF文件名（不需要扩展名）") String fileName,
            @ToolParam(description = "文档标题") String title,
            @ToolParam(description = "列表项，用逗号分隔") String items) {

        try {
            File dir = new File(PDF_DIR);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            String filePath = PDF_DIR + "/" + fileName + ".pdf";
            File file = new File(filePath);

            PdfWriter writer = new PdfWriter(file);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf);

            PdfFont font = getChineseFont();

            // 添加标题
            if (StrUtil.isNotBlank(title)) {
                document.add(new Paragraph()
                        .add(new Text(title).setFont(font).setFontSize(20))
                        .setTextAlignment(TextAlignment.CENTER)
                        .setMarginBottom(20));
            }

            // 添加列表
            if (StrUtil.isNotBlank(items)) {
                List list = new List()
                        .setSymbolIndent(12)
                        .setListSymbol("\u2022")
                        .setFontSize(12);

                String[] itemArray = items.split(",");
                for (String item : itemArray) {
                    if (StrUtil.isNotBlank(item.trim())) {
                        Paragraph para = new Paragraph().add(new Text(item.trim()).setFont(font));
                        ListItem listItem = new ListItem();
                        listItem.add(para);
                        list.add(listItem);
                    }
                }

                document.add(list);
            }

            document.close();

            log.info("带列表的PDF文档创建成功: {}", filePath);
            return buildResult(file);

        } catch (Exception e) {
            log.error("创建带列表的PDF文档失败", e);
            return "创建PDF文档失败: " + e.getMessage();
        }
    }

    /**
     * 创建多章节 PDF 文档
     *
     * @param fileName 文件名
     * @param title 主标题
     * @param chapters 章节内容（格式：章节标题1:内容1;章节标题2:内容2）
     * @return 生成结果
     */
    @Tool(description = "创建包含多个章节的PDF文档")
    public String createPdfWithChapters(
            @ToolParam(description = "PDF文件名（不需要扩展名）") String fileName,
            @ToolParam(description = "文档主标题") String title,
            @ToolParam(description = "章节内容，格式：章节标题1:内容1;章节标题2:内容2") String chapters) {

        try {
            File dir = new File(PDF_DIR);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            String filePath = PDF_DIR + "/" + fileName + ".pdf";
            File file = new File(filePath);

            PdfWriter writer = new PdfWriter(file);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf);

            PdfFont font = getChineseFont();

            // 添加主标题
            if (StrUtil.isNotBlank(title)) {
                document.add(new Paragraph()
                        .add(new Text(title).setFont(font).setFontSize(24))
                        .setTextAlignment(TextAlignment.CENTER)
                        .setMarginBottom(30));
            }

            // 添加章节
            if (StrUtil.isNotBlank(chapters)) {
                String[] chapterArray = chapters.split(";");
                for (String chapter : chapterArray) {
                    String[] parts = chapter.split(":", 2);
                    if (parts.length == 2) {
                        String chapterTitle = parts[0].trim();
                        String chapterContent = parts[1].trim();

                        // 章节标题
                        document.add(new Paragraph()
                                .add(new Text(chapterTitle).setFont(font).setFontSize(16))
                                .setMarginTop(20)
                                .setMarginBottom(10));

                        // 章节内容
                        String[] paragraphs = chapterContent.split("\n");
                        for (String para : paragraphs) {
                            if (StrUtil.isNotBlank(para)) {
                                document.add(new Paragraph()
                                        .add(new Text(para).setFont(font).setFontSize(12))
                                        .setMarginBottom(8));
                            }
                        }
                    }
                }
            }

            document.close();

            log.info("多章节PDF文档创建成功: {}", filePath);
            return buildResult(file);

        } catch (Exception e) {
            log.error("创建多章节PDF文档失败", e);
            return "创建PDF文档失败: " + e.getMessage();
        }
    }

    /**
     * 创建带图片的 PDF 文档
     *
     * @param fileName 文件名
     * @param title 文档标题
     * @param imagePath 图片路径（本地路径）
     * @param caption 图片说明
     * @return 生成结果
     */
    @Tool(description = "创建包含图片的PDF文档")
    public String createPdfWithImage(
            @ToolParam(description = "PDF文件名（不需要扩展名）") String fileName,
            @ToolParam(description = "文档标题") String title,
            @ToolParam(description = "图片的本地路径") String imagePath,
            @ToolParam(description = "图片说明文字") String caption) {

        try {
            File dir = new File(PDF_DIR);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            String filePath = PDF_DIR + "/" + fileName + ".pdf";
            File file = new File(filePath);

            PdfWriter writer = new PdfWriter(file);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf);

            PdfFont font = getChineseFont();

            // 添加标题
            if (StrUtil.isNotBlank(title)) {
                document.add(new Paragraph()
                        .add(new Text(title).setFont(font).setFontSize(20))
                        .setTextAlignment(TextAlignment.CENTER)
                        .setMarginBottom(20));
            }

            // 添加图片
            if (StrUtil.isNotBlank(imagePath)) {
                File imgFile = new File(imagePath);
                if (imgFile.exists()) {
                    Image image = new Image(com.itextpdf.io.image.ImageDataFactory.create(imagePath));
                    image.setMaxWidth(400);
                    image.setAutoScale(true);
                    image.setTextAlignment(TextAlignment.CENTER);
                    document.add(image);

                    // 添加图片说明
                    if (StrUtil.isNotBlank(caption)) {
                        document.add(new Paragraph()
                                .add(new Text(caption).setFont(font).setFontSize(10))
                                .setTextAlignment(TextAlignment.CENTER)
                                .setMarginTop(5)
                                .setFontColor(ColorConstants.GRAY));
                    }
                } else {
                    document.add(new Paragraph()
                            .add(new Text("图片未找到: " + imagePath).setFont(font))
                            .setFontColor(ColorConstants.RED));
                }
            }

            document.close();

            log.info("带图片的PDF文档创建成功: {}", filePath);
            return buildResult(file);

        } catch (Exception e) {
            log.error("创建带图片的PDF文档失败", e);
            return "创建PDF文档失败: " + e.getMessage();
        }
    }

    /**
     * 创建完整的报告 PDF
     *
     * @param fileName 文件名
     * @param reportTitle 报告标题
     * @param content 报告内容
     * @return 生成结果
     */
    @Tool(description = "创建一个完整的PDF报告，包含标题、目录、内容和页码")
    public String createReportPdf(
            @ToolParam(description = "PDF文件名（不需要扩展名）") String fileName,
            @ToolParam(description = "报告标题") String reportTitle,
            @ToolParam(description = "报告内容，支持Markdown格式的标题用#开头") String content) {

        try {
            File dir = new File(PDF_DIR);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            String filePath = PDF_DIR + "/" + fileName + ".pdf";
            File file = new File(filePath);

            PdfWriter writer = new PdfWriter(file);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf);

            PdfFont font = getChineseFont();

            // 添加封面标题
            document.add(new Paragraph()
                    .add(new Text(reportTitle).setFont(font).setFontSize(28))
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginTop(100)
                    .setMarginBottom(50));

            // 添加日期
            document.add(new Paragraph()
                    .add(new Text(java.time.LocalDate.now().toString())
                            .setFont(font)
                            .setFontSize(12))
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontColor(ColorConstants.GRAY));

            // 新页面
            document.add(new AreaBreak());

            // 解析内容并添加
            if (StrUtil.isNotBlank(content)) {
                String[] lines = content.split("\n");
                for (String line : lines) {
                    if (StrUtil.isBlank(line)) {
                        continue;
                    }

                    if (line.startsWith("# ")) {
                        // 一级标题
                        document.add(new Paragraph()
                                .add(new Text(line.substring(2).trim())
                                        .setFont(font)
                                        .setFontSize(20))
                                .setMarginTop(20)
                                .setMarginBottom(10));
                    } else if (line.startsWith("## ")) {
                        // 二级标题
                        document.add(new Paragraph()
                                .add(new Text(line.substring(3).trim())
                                        .setFont(font)
                                        .setFontSize(16))
                                .setMarginTop(15)
                                .setMarginBottom(8));
                    } else if (line.startsWith("### ")) {
                        // 三级标题
                        document.add(new Paragraph()
                                .add(new Text(line.substring(4).trim())
                                        .setFont(font)
                                        .setFontSize(14))
                                .setMarginTop(12)
                                .setMarginBottom(6));
                    } else {
                        // 普通段落
                        document.add(new Paragraph()
                                .add(new Text(line.trim()).setFont(font).setFontSize(12))
                                .setMarginBottom(8));
                    }
                }
            }

            document.close();

            log.info("报告PDF创建成功: {}", filePath);
            return buildResult(file);

        } catch (Exception e) {
            log.error("创建报告PDF失败", e);
            return "创建PDF文档失败: " + e.getMessage();
        }
    }

    /**
     * 获取PDF保存目录
     */
    @Tool(description = "获取PDF文件保存目录的绝对路径")
    public String getPdfDirectory() {
        File dir = new File(PDF_DIR);
        return "PDF保存目录: " + dir.getAbsolutePath();
    }

    /**
     * 列出已生成的PDF文件
     */
    @Tool(description = "列出PDF目录中所有已生成的PDF文件")
    public String listPdfFiles() {
        File dir = new File(PDF_DIR);
        if (!dir.exists()) {
            return "PDF目录不存在";
        }

        File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".pdf"));
        if (files == null || files.length == 0) {
            return "PDF目录中没有文件";
        }

        StringBuilder result = new StringBuilder("PDF文件列表:\n\n");
        for (File file : files) {
            result.append("- ").append(file.getName())
                    .append(" (").append(formatFileSize(file.length())).append(")")
                    .append("\n");
        }

        return result.toString();
    }

    /**
     * 获取中文字体
     */
    private PdfFont getChineseFont() {
        try {
            return PdfFontFactory.createFont("STSong-Light", "UniGB-UCS2-H");
        } catch (Exception e) {
            log.warn("无法加载中文字体，使用默认字体", e);
            try {
                return PdfFontFactory.createFont(StandardFonts.HELVETICA);
            } catch (Exception ex) {
                throw new RuntimeException("无法创建字体", ex);
            }
        }
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
}
