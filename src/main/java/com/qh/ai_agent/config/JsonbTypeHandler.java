package com.qh.ai_agent.config;

import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

/**
 * PostgreSQL jsonb 专用 TypeHandler。
 *
 * MyBatis-Plus 自带的 JacksonTypeHandler 用 ps.setString 传 varchar，
 * 写 jsonb 列会报 "字段类型为 jsonb，但表达式类型为 character varying"。
 * 这里改为 setObject(i, json, Types.OTHER)，驱动会以 unknown 类型发送，由 PostgreSQL 推断为 jsonb。
 * 读取仍复用父类的字符串解析。
 */
@MappedTypes({ Object.class })
@MappedJdbcTypes({ JdbcType.OTHER })
public class JsonbTypeHandler extends JacksonTypeHandler {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public JsonbTypeHandler(Class<?> type) {
        super(type);
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Object parameter, JdbcType jdbcType)
            throws SQLException {
        String json;
        try {
            json = MAPPER.writeValueAsString(parameter);
        } catch (Exception e) {
            throw new SQLException("序列化 JSON 失败: " + e.getMessage(), e);
        }
        ps.setObject(i, json, Types.OTHER);
    }
}
