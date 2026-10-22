package com.qh.ai_agent.knowledge.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * JSON 知识库解析器。
 *
 * 支持：
 *   - 顶层数组；
 *   - { "items": [...] } / { "data": [...] }；
 *   - 兼容业务里按字段名挂数组的结构（如 cases / 流转）；
 *   - 每个对象展开成 key: value 文本，嵌套对象用 a.b 表示，数组用顿号连接。
 */
@Slf4j
@Component
public class JsonKbParser implements KbParser {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public List<KbEntry> parse(File file, String domain) throws IOException {
        List<KbEntry> entries = new ArrayList<>();
        JsonNode root = OBJECT_MAPPER.readTree(Files.readString(file.toPath(), StandardCharsets.UTF_8));
        List<JsonItem> items = extractItems(root);
        String tableName = stripExtension(file.getName());

        for (JsonItem item : items) {
            if (item.node() == null || item.node().isNull() || item.node().isMissingNode()) {
                continue;
            }

            String answer;
            String summary;
            if (item.node().isObject()) {
                List<String> lines = new ArrayList<>();
                flatten("", item.node(), lines);
                if (lines.isEmpty()) {
                    continue;
                }
                answer = String.join("\n", lines);
                summary = buildSummary(item.node(), lines);
            } else if (item.node().isValueNode()) {
                String value = trimToEmpty(item.node().asText());
                if (value.isEmpty()) {
                    continue;
                }
                answer = item.parentKey() == null || item.parentKey().isEmpty()
                        ? value
                        : item.parentKey() + ": " + value;
                summary = value;
            } else {
                continue;
            }

            if (summary.isEmpty()) {
                continue;
            }
            String question = tableName.isEmpty() ? summary : tableName + " - " + summary;
            entries.add(KbEntry.builder()
                    .question(question)
                    .answer(answer)
                    .domain(domain)
                    .category(tableName)
                    .sourceFile(file.getName())
                    .chunkIndex(entries.size())
                    .build());
        }
        log.info("[JsonKbParser] {} -> {} 条", file.getName(), entries.size());
        return entries;
    }
    /**
     * 从根节点提取待转换的条目集合。
     */
    private List<JsonItem> extractItems(JsonNode root) {
        List<JsonItem> items = new ArrayList<>();
        if (root == null || root.isNull()) {
            return items;
        }
        if (root.isArray()) {
            for (JsonNode node : root) {
                items.add(new JsonItem(null, node));
            }
            return items;
        }
        if (!root.isObject()) {
            items.add(new JsonItem(null, root));
            return items;
        }

        // 明确支持 items / data
        JsonNode preferred = root.get("items");
        if (preferred == null) {
            preferred = root.get("data");
        }
        if (preferred != null && preferred.isArray()) {
            for (JsonNode node : preferred) {
                items.add(new JsonItem(null, node));
            }
            return items;
        }

        // 兼容 { "cases": [...] } 这类按字段名挂数组的结构，优先对象数组
        for (Iterator<Map.Entry<String, JsonNode>> it = root.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> field = it.next();
            if (field.getValue().isArray() && containsObject(field.getValue())) {
                for (JsonNode node : field.getValue()) {
                    items.add(new JsonItem(field.getKey(), node));
                }
            }
        }
        if (!items.isEmpty()) {
            return items;
        }

        // 再兼容纯值数组（如状态列表）
        for (Iterator<Map.Entry<String, JsonNode>> it = root.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> field = it.next();
            if (field.getValue().isArray()) {
                for (JsonNode node : field.getValue()) {
                    items.add(new JsonItem(field.getKey(), node));
                }
            }
        }
        if (!items.isEmpty()) {
            return items;
        }

        // 最后把整个根对象当成一条
        items.add(new JsonItem(null, root));
        return items;
    }

    private boolean containsObject(JsonNode arrayNode) {
        for (JsonNode node : arrayNode) {
            if (node.isObject()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 递归展开对象为 key: value 文本。
     */
    private void flatten(String prefix, JsonNode node, List<String> lines) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return;
        }
        if (node.isObject()) {
            for (Iterator<Map.Entry<String, JsonNode>> it = node.fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> field = it.next();
                String key = prefix.isEmpty() ? field.getKey() : prefix + "." + field.getKey();
                flattenValue(key, field.getValue(), lines);
            }
        } else if (node.isArray()) {
            int index = 0;
            for (JsonNode child : node) {
                flattenValue(prefix + "[" + index + "]", child, lines);
                index++;
            }
        } else {
            String value = trimToEmpty(node.asText());
            if (!value.isEmpty()) {
                lines.add(prefix.isEmpty() ? value : prefix + ": " + value);
            }
        }
    }

    private void flattenValue(String key, JsonNode value, List<String> lines) {
        if (value == null || value.isNull() || value.isMissingNode()) {
            return;
        }
        if (value.isValueNode()) {
            String text = trimToEmpty(value.asText());
            if (!text.isEmpty()) {
                lines.add(key + ": " + text);
            }
            return;
        }
        if (value.isArray() && allValueNodes(value)) {
            List<String> values = new ArrayList<>();
            for (JsonNode child : value) {
                String text = trimToEmpty(child.asText());
                if (!text.isEmpty()) {
                    values.add(text);
                }
            }
            if (!values.isEmpty()) {
                lines.add(key + ": " + String.join("、", values));
            }
            return;
        }
        flatten(key, value, lines);
    }

    private boolean allValueNodes(JsonNode arrayNode) {
        for (JsonNode node : arrayNode) {
            if (!node.isValueNode()) {
                return false;
            }
        }
        return true;
    }
    /**
     * 生成可检索的问题摘要：优先取 question/问题/title/名称 等语义字段，
     * 否则取前 3 个标量值（如 caseId + type + sla）。
     */
    private String buildSummary(JsonNode node, List<String> lines) {
        List<String> preferred = new ArrayList<>();
        for (String fieldName : List.of("question", "问题", "title", "标题", "name", "名称",
                "type", "类型", "caseId", "id")) {
            JsonNode value = node.get(fieldName);
            if (value != null && value.isValueNode()) {
                String text = trimToEmpty(value.asText());
                if (!text.isEmpty()) {
                    preferred.add(text);
                }
            }
            if (preferred.size() >= 2) {
                break;
            }
        }
        if (!preferred.isEmpty()) {
            return String.join(" - ", preferred);
        }

        List<String> values = new ArrayList<>();
        collectScalarValues(node, values, 3);
        if (!values.isEmpty()) {
            return String.join(" - ", values);
        }
        return lines.isEmpty() ? "" : lines.get(0);
    }

    private void collectScalarValues(JsonNode node, List<String> values, int limit) {
        if (node == null || values.size() >= limit) {
            return;
        }
        if (node.isValueNode()) {
            String text = trimToEmpty(node.asText());
            if (!text.isEmpty()) {
                values.add(text);
            }
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                collectScalarValues(child, values, limit);
                if (values.size() >= limit) {
                    return;
                }
            }
            return;
        }
        if (node.isObject()) {
            for (Iterator<Map.Entry<String, JsonNode>> it = node.fields(); it.hasNext(); ) {
                collectScalarValues(it.next().getValue(), values, limit);
                if (values.size() >= limit) {
                    return;
                }
            }
        }
    }

    private String stripExtension(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private record JsonItem(String parentKey, JsonNode node) {
    }

    @Override
    public String supportedExtension() {
        return "json";
    }
}