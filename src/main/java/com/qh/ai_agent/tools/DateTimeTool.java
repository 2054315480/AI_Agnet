package com.qh.ai_agent.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

@Component
@Slf4j
public class DateTimeTool {

    @Tool(description = "获取当前时间，格式为 HH:mm:ss")
    public String getCurrentTime() {
        return LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
    }

    @Tool(description = "获取当前日期，格式为 yyyy-MM-dd")
    public String getCurrentDate() {
        return LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    @Tool(description = "获取当前日期和时间，格式为 yyyy-MM-dd HH:mm:ss")
    public String getDateTime() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    @Tool(description = "获取当前时间戳（毫秒）")
    public String getCurrentTimestamp() {
        return String.valueOf(System.currentTimeMillis());
    }

    @Tool(description = "获取当前时区信息")
    public String getTimezoneInfo() {
        ZoneId zone = ZoneId.systemDefault();
        ZoneOffset offset = LocalDateTime.now().atZone(zone).getOffset();
        return "时区: " + zone + ", 偏移: " + offset;
    }

    @Tool(description = "获取指定日期是星期几")
    public String getWeekday(@ToolParam(description = "日期字符串，格式 yyyy-MM-dd") String dateStr) {
        try {
            LocalDate date = LocalDate.parse(dateStr);
            DayOfWeek day = date.getDayOfWeek();
            String[] chinese = {"星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日"};
            return chinese[day.getValue() - 1];
        } catch (DateTimeParseException e) {
            return "日期格式错误，请使用 yyyy-MM-dd 格式";
        }
    }

    @Tool(description = "计算两个日期之间的天数差")
    public String calculateDaysBetween(
            @ToolParam(description = "开始日期，格式 yyyy-MM-dd") String date1,
            @ToolParam(description = "结束日期，格式 yyyy-MM-dd") String date2) {
        try {
            LocalDate start = LocalDate.parse(date1);
            LocalDate end = LocalDate.parse(date2);
            long days = ChronoUnit.DAYS.between(start, end);
            return "相差 " + Math.abs(days) + " 天" + (days >= 0 ? "（结束日期晚于开始日期）" : "（结束日期早于开始日期）");
        } catch (DateTimeParseException e) {
            return "日期格式错误，请使用 yyyy-MM-dd 格式";
        }
    }

    @Tool(description = "日期格式化转换，将日期从一种格式转为另一种格式")
    public String formatDate(
            @ToolParam(description = "日期字符串") String dateStr,
            @ToolParam(description = "输入格式，如 yyyy-MM-dd") String inputPattern,
            @ToolParam(description = "输出格式，如 yyyy年MM月dd日") String outputPattern) {
        try {
            DateTimeFormatter inputFmt = DateTimeFormatter.ofPattern(inputPattern);
            DateTimeFormatter outputFmt = DateTimeFormatter.ofPattern(outputPattern);
            LocalDateTime dateTime = LocalDateTime.parse(dateStr, inputFmt);
            return dateTime.format(outputFmt);
        } catch (Exception e) {
            // 尝试按 LocalDate 解析
            try {
                DateTimeFormatter inputFmt = DateTimeFormatter.ofPattern(inputPattern);
                DateTimeFormatter outputFmt = DateTimeFormatter.ofPattern(outputPattern);
                LocalDate date = LocalDate.parse(dateStr, inputFmt);
                return date.format(outputFmt);
            } catch (Exception ex) {
                return "格式化失败: " + ex.getMessage();
            }
        }
    }

    @Tool(description = "在指定日期上增加或减少天数")
    public String addDays(
            @ToolParam(description = "基准日期，格式 yyyy-MM-dd") String dateStr,
            @ToolParam(description = "要增加的天数（负数表示减少）") int days) {
        try {
            LocalDate date = LocalDate.parse(dateStr);
            LocalDate result = date.plusDays(days);
            return result.format(DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException e) {
            return "日期格式错误，请使用 yyyy-MM-dd 格式";
        }
    }
}
