package com.qh.ai_agent.knowledge.parser;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.CellValue;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * XLSX 知识库解析器。
 *
 * 设计目标：把表格按行切成可检索的 QA 条目，而不是整张表塞成一段文本。
 * 关键点：
 *   1. 多 sheet 分别处理；
 *   2. 多级表头 + 横向/纵向合并单元格展开，列名用 / 拼接；
 *   3. 每个数据行生成一条 KbEntry，answer 中带表名、表头、行值；
 *   4. 公式优先取缓存值/计算结果，异常时退回公式文本；
 *   5. 空行、全空行、重复表头行不产生垃圾条目。
 */
@Slf4j
@Component
public class XlsxKbParser implements KbParser {

    private static final DataFormatter DATA_FORMATTER = new DataFormatter();

    /** 防止异常超大列导致内存问题，正常业务表格远小于该值 */
    private static final int MAX_COLUMNS = 256;
    @Override
    public List<KbEntry> parse(File file, String domain) throws IOException {
        List<KbEntry> entries = new ArrayList<>();
        try (InputStream in = new FileInputStream(file);
             Workbook workbook = WorkbookFactory.create(in)) {
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                List<KbEntry> sheetEntries = parseSheet(sheet, file.getName(), domain, evaluator, entries.size());
                entries.addAll(sheetEntries);
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception | LinkageError e) {
            throw new IOException("解析 XLSX 失败: " + file.getName(), e);
        }
        log.info("[XlsxKbParser] {} -> {} 条", file.getName(), entries.size());
        return entries;
    }
    /**
     * 解析单个 sheet：识别表头后逐行切片。
     */
    private List<KbEntry> parseSheet(Sheet sheet, String filename, String domain,
                                     FormulaEvaluator evaluator, int startIndex) {
        List<KbEntry> entries = new ArrayList<>();
        int lastRow = sheet.getLastRowNum();
        if (lastRow < 0) {
            return entries;
        }

        int maxCols = detectMaxColumns(sheet);
        if (maxCols <= 0) {
            return entries;
        }

        String[][] grid = buildGrid(sheet, maxCols, evaluator);
        List<Integer> headerRows = detectHeaderRows(grid, sheet, maxCols);
        if (headerRows.isEmpty()) {
            log.warn("[XlsxKbParser] sheet={} 未识别到表头，跳过", sheet.getSheetName());
            return entries;
        }

        String[] headers = buildHeaders(grid, headerRows, maxCols);
        String tableName = sheet.getSheetName() == null ? "Sheet" : sheet.getSheetName().trim();
        int dataStart = headerRows.get(headerRows.size() - 1) + 1;

        for (int r = dataStart; r < grid.length; r++) {
            if (isEmptyRow(grid[r]) || isTitleRow(grid, r, sheet, maxCols)) {
                continue;
            }
            // 重复表头行（例如分页后重复打印表头）不再生成条目
            if (isRepeatedHeaderRow(grid[r], headers)) {
                continue;
            }

            StringBuilder answer = new StringBuilder();
            answer.append("表名: ").append(tableName).append("\n");
            int nonEmptyValues = 0;
            for (int c = 0; c < maxCols; c++) {
                String value = trimToEmpty(grid[r][c]);
                if (value.isEmpty()) {
                    continue;
                }
                nonEmptyValues++;
                answer.append(headers[c]).append(": ").append(value).append("\n");
            }
            if (nonEmptyValues == 0) {
                continue;
            }

            // question 使用表名 + 首列值；首列为空时退化为该行第一个非空值
            String firstValue = trimToEmpty(grid[r][0]);
            if (firstValue.isEmpty()) {
                firstValue = firstNonEmptyValue(grid[r]);
            }
            if (firstValue.isEmpty()) {
                continue;
            }

            String question = tableName.isEmpty()
                    ? firstValue
                    : tableName + " - " + firstValue;

            entries.add(KbEntry.builder()
                    .question(question)
                    .answer(answer.toString().trim())
                    .domain(domain)
                    .category(tableName)
                    .sourceFile(filename)
                    .chunkIndex(startIndex + entries.size())
                    .build());
        }
        return entries;
    }
    /**
     * 计算有效列数：取所有行和合并区域的右边界最大值。
     */
    private int detectMaxColumns(Sheet sheet) {
        int maxCols = 0;
        for (int r = 0; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row != null && row.getLastCellNum() > maxCols) {
                maxCols = row.getLastCellNum();
            }
        }
        for (CellRangeAddress region : sheet.getMergedRegions()) {
            maxCols = Math.max(maxCols, region.getLastColumn() + 1);
        }
        return Math.min(maxCols, MAX_COLUMNS);
    }

    /**
     * 构建二维文本网格，并把合并单元格的值向整个合并区域展开。
     * 这样纵向合并的行头、横向合并的列名都能被后续逻辑直接回溯使用。
     */
    private String[][] buildGrid(Sheet sheet, int maxCols, FormulaEvaluator evaluator) {
        int lastRow = sheet.getLastRowNum();
        String[][] grid = new String[lastRow + 1][maxCols];

        for (int r = 0; r <= lastRow; r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            for (int c = 0; c < maxCols; c++) {
                Cell cell = row.getCell(c);
                if (cell != null) {
                    grid[r][c] = formatCell(cell, evaluator);
                }
            }
        }

        for (CellRangeAddress region : sheet.getMergedRegions()) {
            int firstRow = region.getFirstRow();
            int lastRowOfRegion = Math.min(region.getLastRow(), lastRow);
            int firstCol = region.getFirstColumn();
            int lastColOfRegion = Math.min(region.getLastColumn(), maxCols - 1);
            if (firstRow < 0 || firstCol < 0 || firstRow > lastRowOfRegion || firstCol > lastColOfRegion) {
                continue;
            }
            String mergedValue = trimToEmpty(grid[firstRow][firstCol]);
            if (mergedValue.isEmpty()) {
                continue;
            }
            for (int r = firstRow; r <= lastRowOfRegion; r++) {
                for (int c = firstCol; c <= lastColOfRegion; c++) {
                    grid[r][c] = mergedValue;
                }
            }
        }
        return grid;
    }
    /**
     * 识别表头行。规则：
     *   1. 跳过开头的空行、整行合并标题行；
     *   2. 第一个非标题非空行作为表头起点；
     *   3. 重复表头行继续纳入表头（后续拼接时会去重）；
     *   4. 若上一表头行存在横向合并，且当前行没有数字/公式，则视为多级表头下一层。
     */
    private List<Integer> detectHeaderRows(String[][] grid, Sheet sheet, int maxCols) {
        List<Integer> headerRows = new ArrayList<>();
        int lastRow = grid.length - 1;
        int first = firstNonEmptyRow(grid, 0);

        // 跳过标题行（标题可能占多行，如第1层/第2层/第3层）
        while (first >= 0 && first <= lastRow && isTitleRow(grid, first, sheet, maxCols)) {
            first = firstNonEmptyRow(grid, first + 1);
        }
        if (first < 0) {
            return headerRows;
        }

        headerRows.add(first);
        int cursor = firstNonEmptyRow(grid, first + 1);
        while (cursor >= 0) {
            if (isTitleRow(grid, cursor, sheet, maxCols)) {
                cursor = firstNonEmptyRow(grid, cursor + 1);
                continue;
            }
            if (isSameAsAnyHeaderRow(grid[cursor], grid, headerRows)) {
                headerRows.add(cursor);
                cursor = firstNonEmptyRow(grid, cursor + 1);
                continue;
            }
            if (looksLikeHeaderContinuation(grid, sheet, cursor, headerRows)) {
                headerRows.add(cursor);
                cursor = firstNonEmptyRow(grid, cursor + 1);
                continue;
            }
            break;
        }
        return headerRows;
    }

    /**
     * 多级表头续行判断：上一行存在横向合并，当前行是短文本且不含数字/公式。
     */
    private boolean looksLikeHeaderContinuation(String[][] grid, Sheet sheet,
                                                int row, List<Integer> headerRows) {
        int previousHeader = headerRows.get(headerRows.size() - 1);
        if (!hasHorizontalMerge(sheet, previousHeader)) {
            return false;
        }
        if (rowHasNumericOrFormula(sheet, row)) {
            return false;
        }
        if (firstNonEmptyRow(grid, row + 1) < 0) {
            return false;
        }
        if (countNonEmpty(grid[row]) == 0) {
            return false;
        }
        // 表头单元格通常较短；避免把长文本数据行误判成表头
        return maxValueLength(grid[row]) <= 50;
    }
    /**
     * 拼接多级表头：同一列按行从上到下取值，用 / 连接并去除连续重复。
     * 例如：A1:B1 合并 理赔材料 + A2 身份证 -> 理赔材料/身份证。
     */
    private String[] buildHeaders(String[][] grid, List<Integer> headerRows, int maxCols) {
        String[] headers = new String[maxCols];
        for (int c = 0; c < maxCols; c++) {
            Set<String> parts = new LinkedHashSet<>();
            for (Integer row : headerRows) {
                String value = trimToEmpty(grid[row][c]);
                if (!value.isEmpty()) {
                    parts.add(value);
                }
            }
            String header = String.join("/", parts);
            if (header.isEmpty()) {
                header = "列" + CellReference.convertNumToColString(c);
            }
            headers[c] = header;
        }
        return headers;
    }

    /**
     * 判断是否为标题行：单值横向合并覆盖全部列，或单个单元格标题后紧跟多列表头。
     */
    private boolean isTitleRow(String[][] grid, int row, Sheet sheet, int maxCols) {
        int nonEmpty = countNonEmpty(grid[row]);
        if (nonEmpty == 0) {
            return false;
        }
        Set<String> distinct = new LinkedHashSet<>();
        for (String value : grid[row]) {
            String trimmed = trimToEmpty(value);
            if (!trimmed.isEmpty()) {
                distinct.add(trimmed);
            }
        }
        if (distinct.size() == 1 && hasMergeCoveringAllColumns(sheet, row, maxCols)) {
            return isStandaloneFullWidthMergedRow(sheet, row, maxCols);
        }
        if (nonEmpty == 1) {
            int next = firstNonEmptyRow(grid, row + 1);
            return next >= 0 && countNonEmpty(grid[next]) >= 2;
        }
        return false;
    }

    /**
     * 全宽合并行既可能是标题，也可能是只有两列时的第一层表头。
     * 判为标题的条件：下一物理行为空，或上下相邻行仍是全宽合并（连续标题块）；
     * 否则视为多级表头的一层，参与列名拼接。
     */
    private boolean isStandaloneFullWidthMergedRow(Sheet sheet, int row, int maxCols) {
        if (isFullWidthMergedRow(sheet, row - 1, maxCols) || isFullWidthMergedRow(sheet, row + 1, maxCols)) {
            return true;
        }
        int nextRow = row + 1;
        return nextRow <= sheet.getLastRowNum() && isPhysicalRowEmpty(sheet, nextRow);
    }

    private boolean isFullWidthMergedRow(Sheet sheet, int row, int maxCols) {
        if (row < 0) {
            return false;
        }
        for (CellRangeAddress region : sheet.getMergedRegions()) {
            if (region.getFirstRow() <= row && row <= region.getLastRow()
                    && region.getLastColumn() - region.getFirstColumn() + 1 >= maxCols) {
                return true;
            }
        }
        return false;
    }

    private boolean isPhysicalRowEmpty(Sheet sheet, int rowIndex) {
        Row row = sheet.getRow(rowIndex);
        if (row == null) {
            return true;
        }
        for (Cell cell : row) {
            if (cell.getCellType() != CellType.BLANK && !trimToEmpty(DATA_FORMATTER.formatCellValue(cell)).isEmpty()) {
                return false;
            }
        }
        return true;
    }
    private boolean hasMergeCoveringAllColumns(Sheet sheet, int row, int maxCols) {
        for (CellRangeAddress region : sheet.getMergedRegions()) {
            if (region.getFirstRow() <= row && row <= region.getLastRow()
                    && region.getLastColumn() - region.getFirstColumn() + 1 >= maxCols) {
                return true;
            }
        }
        return false;
    }

    private boolean hasHorizontalMerge(Sheet sheet, int row) {
        for (CellRangeAddress region : sheet.getMergedRegions()) {
            if (region.getFirstRow() <= row && row <= region.getLastRow()
                    && region.getLastColumn() > region.getFirstColumn()) {
                return true;
            }
        }
        return false;
    }

    private boolean rowHasNumericOrFormula(Sheet sheet, int rowIndex) {
        Row row = sheet.getRow(rowIndex);
        if (row == null) {
            return false;
        }
        for (Cell cell : row) {
            CellType type = cell.getCellType();
            if (type == CellType.NUMERIC || type == CellType.BOOLEAN || type == CellType.FORMULA) {
                return true;
            }
        }
        return false;
    }
    private boolean isSameAsAnyHeaderRow(String[] row, String[][] grid, List<Integer> headerRows) {
        for (Integer headerRow : headerRows) {
            if (isSameRawRow(row, grid[headerRow])) {
                return true;
            }
        }
        return false;
    }

    private boolean isSameRawRow(String[] left, String[] right) {
        int nonEmpty = 0;
        for (int c = 0; c < Math.min(left.length, right.length); c++) {
            String lv = trimToEmpty(left[c]);
            String rv = trimToEmpty(right[c]);
            if (lv.isEmpty() && rv.isEmpty()) {
                continue;
            }
            if (!lv.equals(rv)) {
                return false;
            }
            nonEmpty++;
        }
        return nonEmpty > 0;
    }

    /**
     * 数据区里再次出现与表头完全相同的行时跳过，避免分页重复表头进库。
     */
    private boolean isRepeatedHeaderRow(String[] row, String[] headers) {
        int nonEmpty = 0;
        int matched = 0;
        for (int c = 0; c < Math.min(row.length, headers.length); c++) {
            String value = trimToEmpty(row[c]);
            if (value.isEmpty()) {
                continue;
            }
            nonEmpty++;
            String header = headers[c];
            if (value.equals(header) || header.endsWith("/" + value) || header.startsWith(value + "/")) {
                matched++;
            }
        }
        return nonEmpty > 0 && matched == nonEmpty;
    }

    private boolean isEmptyRow(String[] row) {
        return countNonEmpty(row) == 0;
    }

    private int countNonEmpty(String[] row) {
        int count = 0;
        for (String value : row) {
            if (!trimToEmpty(value).isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private int maxValueLength(String[] row) {
        int max = 0;
        for (String value : row) {
            max = Math.max(max, trimToEmpty(value).length());
        }
        return max;
    }

    private int firstNonEmptyRow(String[][] grid, int start) {
        for (int r = start; r < grid.length; r++) {
            if (!isEmptyRow(grid[r])) {
                return r;
            }
        }
        return -1;
    }

    private String firstNonEmptyValue(String[] row) {
        for (String value : row) {
            String trimmed = trimToEmpty(value);
            if (!trimmed.isEmpty()) {
                return trimmed;
            }
        }
        return "";
    }
    /**
     * 单元格取值：
     *   1. 公式单元格先取 POI 缓存值；
     *   2. 无缓存时用 FormulaEvaluator 计算；
     *   3. 计算异常时退回公式文本，保证不丢行。
     */
    private String formatCell(Cell cell, FormulaEvaluator evaluator) {
        if (cell == null) {
            return "";
        }
        try {
            if (cell.getCellType() == CellType.FORMULA) {
                String cached = cachedFormulaText(cell);
                if (!cached.isEmpty()) {
                    return cached;
                }
                try {
                    CellValue evaluated = evaluator.evaluate(cell);
                    String text = formatEvaluatedValue(cell, evaluated);
                    if (!text.isEmpty()) {
                        return text;
                    }
                } catch (Exception | LinkageError e) {
                    log.debug("[XlsxKbParser] 公式计算失败: {}", cell.getAddress(), e);
                }
                String formula = cell.getCellFormula();
                return formula == null || formula.isBlank() ? "" : "=" + formula.trim();
            }
            return trimToEmpty(DATA_FORMATTER.formatCellValue(cell));
        } catch (Exception | LinkageError e) {
            log.debug("[XlsxKbParser] 单元格格式化失败: {}", cell.getAddress(), e);
            try {
                String formula = cell.getCellFormula();
                return formula == null || formula.isBlank() ? "" : "=" + formula.trim();
            } catch (Exception ignored) {
                return "";
            }
        }
    }

    /**
     * 读取 xlsx 公式单元格的缓存值。未计算过的公式 getRawValue() 通常为空。
     */
    private String cachedFormulaText(Cell cell) {
        if (!(cell instanceof XSSFCell xssfCell)) {
            return "";
        }
        try {
            String raw = xssfCell.getRawValue();
            if (raw == null || raw.isBlank()) {
                return "";
            }
            CellType cachedType = xssfCell.getCachedFormulaResultType();
            if (cachedType == CellType.STRING) {
                return trimToEmpty(xssfCell.getStringCellValue());
            }
            if (cachedType == CellType.BOOLEAN) {
                return String.valueOf(xssfCell.getBooleanCellValue());
            }
            if (cachedType == CellType.NUMERIC) {
                return formatNumericCell(xssfCell, xssfCell.getNumericCellValue());
            }
            return trimToEmpty(raw);
        } catch (Exception | LinkageError e) {
            log.debug("[XlsxKbParser] 读取公式缓存值失败: {}", cell.getAddress(), e);
            return "";
        }
    }

    private String formatEvaluatedValue(Cell cell, CellValue value) {
        if (value == null) {
            return "";
        }
        return switch (value.getCellType()) {
            case STRING -> trimToEmpty(value.getStringValue());
            case BOOLEAN -> String.valueOf(value.getBooleanValue());
            case NUMERIC -> formatNumericCell(cell, value.getNumberValue());
            default -> "";
        };
    }

    private String formatNumericCell(Cell cell, double value) {
        try {
            return trimToEmpty(DATA_FORMATTER.formatRawCellContents(
                    value, cell.getCellStyle().getDataFormat(), cell.getCellStyle().getDataFormatString()));
        } catch (Exception e) {
            // DataFormatter 失败时退化为普通数字文本
            if (value == Math.rint(value)) {
                return String.valueOf((long) value);
            }
            return String.valueOf(value);
        }
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    @Override
    public String supportedExtension() {
        return "xlsx";
    }
}