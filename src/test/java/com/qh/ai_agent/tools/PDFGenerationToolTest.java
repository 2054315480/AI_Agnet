package com.qh.ai_agent.tools;

import com.qh.ai_agent.AiAgentApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * PDFGenerationTool 测试类
 */
@SpringBootTest(classes = AiAgentApplication.class)
public class PDFGenerationToolTest {

    @Autowired
    private PDFGenerationTool pdfGenerationTool;

    @Test
    public void testGetPdfDirectory() {
        // 测试获取PDF目录
        String result = pdfGenerationTool.getPdfDirectory();
        System.out.println("PDF目录信息:");
        System.out.println(result);
        System.out.println("\n" + "=".repeat(80) + "\n");
    }

    @Test
    public void testCreateSimplePdf() {
        // 测试创建简单PDF
        String result = pdfGenerationTool.createSimplePdf(
                "test-simple",
                "测试文档",
                "这是一个测试PDF文档。\n\n它包含了基本的文本内容。\n\n用于验证PDF生成功能是否正常工作。"
        );
        System.out.println("创建简单PDF结果:");
        System.out.println(result);
        System.out.println("\n" + "=".repeat(80) + "\n");
    }

    @Test
    public void testCreatePdfWithTable() {
        // 测试创建带表格的PDF
        String result = pdfGenerationTool.createPdfWithTable(
                "test-table",
                "员工信息表",
                "姓名,年龄,职位",
                "张三,25,软件工程师;李四,30,产品经理;王五,28,设计师;赵六,35,技术总监"
        );
        System.out.println("创建带表格的PDF结果:");
        System.out.println(result);
        System.out.println("\n" + "=".repeat(80) + "\n");
    }

    @Test
    public void testCreatePdfWithList() {
        // 测试创建带列表的PDF
        String result = pdfGenerationTool.createPdfWithList(
                "test-list",
                "任务清单",
                "完成项目文档,代码审查,单元测试,部署上线,编写用户手册"
        );
        System.out.println("创建带列表的PDF结果:");
        System.out.println(result);
        System.out.println("\n" + "=".repeat(80) + "\n");
    }

    @Test
    public void testCreatePdfWithChapters() {
        // 测试创建多章节PDF
        String result = pdfGenerationTool.createPdfWithChapters(
                "test-chapters",
                "项目周报",
                "本周工作:完成了用户模块开发,修复了5个bug,优化了数据库查询;下周计划:开始订单模块开发,编写技术文档"
        );
        System.out.println("创建多章节PDF结果:");
        System.out.println(result);
        System.out.println("\n" + "=".repeat(80) + "\n");
    }

    @Test
    public void testCreateReportPdf() {
        // 测试创建报告PDF
        String content = "# 项目简介\n\n这是一个AI智能体项目。\n\n" +
                "# 技术栈\n\nSpring Boot + Spring AI\n\n" +
                "## 主要功能\n\n- 网页搜索\n- 文件操作\n- PDF生成\n\n" +
                "# 总结\n\n项目进展顺利。";

        String result = pdfGenerationTool.createReportPdf(
                "test-report",
                "AI智能体项目报告",
                content
        );
        System.out.println("创建报告PDF结果:");
        System.out.println(result);
        System.out.println("\n" + "=".repeat(80) + "\n");
    }

    @Test
    public void testListPdfFiles() {
        // 测试列出PDF文件
        String result = pdfGenerationTool.listPdfFiles();
        System.out.println("PDF文件列表:");
        System.out.println(result);
        System.out.println("\n" + "=".repeat(80) + "\n");
    }

    @Test
    public void testCreateMultiplePdfs() {
        // 批量创建测试
        System.out.println("批量创建PDF文件:\n");

        String[] titles = {"文档1", "文档2", "文档3"};
        for (int i = 0; i < titles.length; i++) {
            String result = pdfGenerationTool.createSimplePdf(
                    "batch-test-" + (i + 1),
                    titles[i],
                    "这是第 " + (i + 1) + " 个测试文档的内容。\n\n包含一些示例文本。"
            );
            System.out.println(result);
            System.out.println();
        }
    }

    @Test
    public void testCreateCompanyReport() {
        // 创建公司报告示例
        String result = pdfGenerationTool.createPdfWithTable(
                "company-report",
                "2024年第一季度销售报告",
                "产品名称,销售额,增长率,负责人",
                "产品A,120万,+15%,张三;产品B,85万,+8%,李四;产品C,200万,+22%,王五;产品D,150万,+12%,赵六"
        );
        System.out.println("创建公司报告PDF结果:");
        System.out.println(result);
        System.out.println("\n" + "=".repeat(80) + "\n");
    }
}
