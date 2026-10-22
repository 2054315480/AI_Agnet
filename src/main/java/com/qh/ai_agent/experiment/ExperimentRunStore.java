package com.qh.ai_agent.experiment;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 实验实测结果存储：每次跑实验都把结果落一行，
 * "优化成效"页读最近一次真实结果，避免页面上写死数字。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExperimentRunStore {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 保存一次实验结果（失败不影响实验本身返回） */
    public void save(String type, Map<String, Object> payload) {
        try {
            jdbcTemplate.update(
                    "INSERT INTO experiment_run (type, payload) VALUES (?, ?::jsonb)",
                    type, objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            log.warn("[ExperimentRunStore] 结果落库失败 type={}: {}", type, e.getMessage());
        }
    }

    /** 读某类实验最近一次结果，没有则返回 null */
    public Map<String, Object> latest(String type) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT payload::text AS payload, created_at FROM experiment_run "
                            + "WHERE type = ? ORDER BY created_at DESC LIMIT 1", type);
            if (rows.isEmpty()) {
                return null;
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("payload", objectMapper.readValue(
                    String.valueOf(rows.get(0).get("payload")), Map.class));
            out.put("at", String.valueOf(rows.get(0).get("created_at")));
            return out;
        } catch (Exception e) {
            log.warn("[ExperimentRunStore] 读取失败 type={}: {}", type, e.getMessage());
            return null;
        }
    }
}
