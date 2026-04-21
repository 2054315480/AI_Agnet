package com.qh.ai_agent.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
@Slf4j
public class DatabaseQueryTool {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseQueryTool(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.jdbcTemplate.setQueryTimeout(30);
    }

    private static final Set<String> FORBIDDEN_KEYWORDS = Set.of(
            "INSERT", "UPDATE", "DELETE", "DROP", "ALTER", "CREATE",
            "TRUNCATE", "EXECUTE", "GRANT", "REVOKE", "COPY", "VACUUM"
    );

    @Tool(description = "执行只读 SQL 查询（仅允许 SELECT 语句），返回格式化的表格结果")
    public String executeSelectQuery(@ToolParam(description = "SQL 查询语句，仅支持 SELECT") String sql) {
        String trimmed = sql.trim();
        if (!isSafeSelect(trimmed)) {
            return "安全限制：仅允许 SELECT 查询语句";
        }
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(trimmed);
            if (rows.isEmpty()) {
                return "查询结果为空";
            }
            if (rows.size() > 100) {
                rows = rows.subList(0, 100);
            }
            return formatAsTable(rows);
        } catch (Exception e) {
            log.warn("SQL 查询失败: {}", e.getMessage());
            return "查询失败: " + e.getMessage();
        }
    }

    @Tool(description = "列出当前数据库中所有用户表")
    public String listTables() {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT tablename FROM pg_tables WHERE schemaname = 'public' ORDER BY tablename"
            );
            if (rows.isEmpty()) return "没有找到用户表";
            return rows.stream()
                    .map(row -> row.get("tablename").toString())
                    .collect(Collectors.joining("\n"));
        } catch (Exception e) {
            return "查询失败: " + e.getMessage();
        }
    }

    @Tool(description = "查看指定表的结构（列名、数据类型、是否允许为空）")
    public String describeTable(@ToolParam(description = "表名") String tableName) {
        if (!isValidIdentifier(tableName)) {
            return "非法表名";
        }
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT column_name, data_type, is_nullable " +
                            "FROM information_schema.columns " +
                            "WHERE table_schema = 'public' AND table_name = ? " +
                            "ORDER BY ordinal_position",
                    tableName
            );
            if (rows.isEmpty()) return "表 '" + tableName + "' 不存在";
            return formatAsTable(rows);
        } catch (Exception e) {
            return "查询失败: " + e.getMessage();
        }
    }

    @Tool(description = "查询指定表的行数")
    public String countRows(@ToolParam(description = "表名") String tableName) {
        if (!isValidIdentifier(tableName)) {
            return "非法表名";
        }
        try {
            Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + tableName, Long.class
            );
            return "表 " + tableName + " 共有 " + count + " 行";
        } catch (Exception e) {
            return "查询失败: " + e.getMessage();
        }
    }

    private boolean isSafeSelect(String sql) {
        String upper = sql.toUpperCase().trim();
        if (!upper.startsWith("SELECT")) return false;
        if (upper.contains(";")) {
            String afterSemicolon = upper.substring(upper.indexOf(';') + 1).trim();
            if (!afterSemicolon.isEmpty()) return false;
        }
        for (String keyword : FORBIDDEN_KEYWORDS) {
            if (upper.contains(keyword)) return false;
        }
        return true;
    }

    private boolean isValidIdentifier(String name) {
        return name != null && name.matches("[a-zA-Z_][a-zA-Z0-9_]*");
    }

    private String formatAsTable(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) return "";
        List<String> headers = new ArrayList<>(rows.get(0).keySet());
        StringBuilder sb = new StringBuilder();
        sb.append("| ").append(String.join(" | ", headers)).append(" |\n");
        sb.append("| ").append(headers.stream().map(h -> "---").collect(Collectors.joining(" | "))).append(" |\n");
        for (Map<String, Object> row : rows) {
            sb.append("| ");
            for (String header : headers) {
                Object val = row.get(header);
                sb.append(val != null ? val.toString() : "NULL").append(" | ");
            }
            sb.append("\n");
        }
        sb.append("共 ").append(rows.size()).append(" 行");
        return sb.toString();
    }
}
