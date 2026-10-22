package com.qh.ai_agent.knowledge.parser;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileOutputStream;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * XlsxKbParser 纯内存单测：不依赖外部文件。
 */
class XlsxKbParserTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldExpandMergedHeadersSkipEmptyRowsAndEvaluateFormula() throws Exception {
        Path xlsx = tempDir.resolve("test.xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("理赔材料");

            // 第一层表头：A1:B1 合并为理赔材料，C1 为备注
            Row header1 = sheet.createRow(0);
            header1.createCell(0).setCellValue("理赔材料");
            header1.createCell(2).setCellValue("备注");
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 1));

            // 第二层表头：身份证 / 户口本 / 说明
            Row header2 = sheet.createRow(1);
            header2.createCell(0).setCellValue("身份证");
            header2.createCell(1).setCellValue("户口本");
            header2.createCell(2).setCellValue("说明");
            // 数据行 1
            Row data1 = sheet.createRow(2);
            data1.createCell(0).setCellValue("张三");
            data1.createCell(1).setCellValue("身份证复印件");
            data1.createCell(2).setCellValue("无");

            // 数据行 2：公式单元格
            Row data2 = sheet.createRow(3);
            data2.createCell(0).setCellValue("李四");
            data2.createCell(1).setCellFormula("1+2");
            data2.createCell(2).setCellValue("有");

            // 空行：不应产生条目
            sheet.createRow(4);

            // 数据行 3
            Row data3 = sheet.createRow(5);
            data3.createCell(0).setCellValue("王五");
            data3.createCell(1).setCellValue("户口本复印件");
            data3.createCell(2).setCellValue("无");

            // 第二个 sheet：验证多 sheet 分别解析
            Sheet second = workbook.createSheet("理赔时效");
            Row secondHeader = second.createRow(0);
            secondHeader.createCell(0).setCellValue("险种");
            secondHeader.createCell(1).setCellValue("时效");
            Row secondData = second.createRow(1);
            secondData.createCell(0).setCellValue("意外险");
            secondData.createCell(1).setCellValue("3个工作日");

            try (FileOutputStream out = new FileOutputStream(xlsx.toFile())) {
                workbook.write(out);
            }
        }

        XlsxKbParser parser = new XlsxKbParser();
        List<KbEntry> entries = parser.parse(xlsx.toFile(), "insurance");

        assertEquals(4, entries.size(), "两个 sheet 共 4 条数据，空行不产生条目");
        assertEquals(List.of(
                        "理赔材料 - 张三",
                        "理赔材料 - 李四",
                        "理赔材料 - 王五",
                        "理赔时效 - 意外险"),
                entries.stream().map(KbEntry::getQuestion).toList());

        String allAnswers = entries.stream()
                .map(KbEntry::getAnswer)
                .reduce("", (left, right) -> left + "\n" + right);
        assertTrue(allAnswers.contains("理赔材料/身份证: 张三"), "多级表头应拼接为 理赔材料/身份证");
        assertTrue(allAnswers.contains("理赔材料/户口本: 户口本复印件"), "合并表头应覆盖 B 列");
        assertTrue(allAnswers.contains("理赔材料/户口本: 3"), "公式单元格应取到计算结果");
        assertTrue(allAnswers.contains("备注/说明: 无"), "普通多级表头应拼接");
        assertFalse(allAnswers.contains("=1+2"), "公式可计算时不应退化为公式文本");
        assertFalse(entries.stream().anyMatch(entry -> entry.getAnswer().isBlank()));
    }
    @Test
    void shouldExpandMergedHeaderWhenMergeCoversAllColumns() throws Exception {
        Path xlsx = tempDir.resolve("two-columns.xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("理赔材料");
            Row header1 = sheet.createRow(0);
            header1.createCell(0).setCellValue("理赔材料");
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 1));

            Row header2 = sheet.createRow(1);
            header2.createCell(0).setCellValue("身份证");
            header2.createCell(1).setCellValue("户口本");

            Row data = sheet.createRow(2);
            data.createCell(0).setCellValue("张三");
            data.createCell(1).setCellValue("户口本复印件");

            try (FileOutputStream out = new FileOutputStream(xlsx.toFile())) {
                workbook.write(out);
            }
        }

        List<KbEntry> entries = new XlsxKbParser().parse(xlsx.toFile(), "insurance");
        assertEquals(1, entries.size());
        assertTrue(entries.get(0).getAnswer().contains("理赔材料/身份证: 张三"));
        assertTrue(entries.get(0).getAnswer().contains("理赔材料/户口本: 户口本复印件"));
    }
}