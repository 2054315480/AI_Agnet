package com.qh.ai_agent.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qh.ai_agent.knowledge.parser.KbEntry;
import com.qh.ai_agent.knowledge.parser.UnifiedKbLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 评测集构建器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EvalDatasetBuilder {

    private final UnifiedKbLoader unifiedKbLoader;
    private final ChatModel openAiChatModel;
    private final JdbcTemplate jdbcTemplate;

    private static final ObjectMapper JSON = new ObjectMapper();

    @Value("${cs.eval.labeled-set:./data/eval/labeled_set.jsonl}")
    private String labeledSetPath;

    /**
     * 只去 query 开头的机械序号，不碰正文里的数字。
     * 覆盖：10. xxx / 10、xxx / 第10条 xxx / Q10: xxx / (10) xxx。
     */
    private static final Pattern QUERY_PREFIX = Pattern.compile(
            "^\\s*(?:第\\s*[0-9一二三四五六七八九十百千]+\\s*条\\s*[:：、.．]?\\s*"
            + "|Q\\s*\\d+\\s*[:：、.．]?\\s*"
            + "|[（(]\\s*\\d+\\s*[)）]\\s*"
            + "|\\d+\\s*[.、．:：)）]\\s*)");

    public String getLabeledSetPath() {
        return labeledSetPath;
    }

    public List<EvalService.Sample> buildFromKnowledgeBase(int size) {
        return build(size, "question");
    }

    /**
     * 构建自动评测集。
     *
     * mode = question：直接用知识库问题当 query（简单模式，分数会接近 1.0）
     * mode = answer  ：用答案片段当 query（困难模式，要求语义检索能对上）
     */
    public List<EvalService.Sample> build(int size, String mode) {
        List<KbEntry> entries;
        try {
            entries = unifiedKbLoader.loadAll();
        } catch (Exception e) {
            log.error("[EvalDataset] 加载知识库失败", e);
            return List.of();
        }
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }

        // 固定随机种子，保证评测集可复现（同一 commit 两次跑结果一致）
        Random random = new Random(42);
        List<KbEntry> shuffled = new ArrayList<>(entries);
        Collections.shuffle(shuffled, random);

        List<EvalService.Sample> samples = new ArrayList<>();
        int n = Math.min(size, shuffled.size());
        for (int i = 0; i < n; i++) {
            KbEntry e = shuffled.get(i);
            String q;
            if ("answer".equalsIgnoreCase(mode)) {
                q = answerSnippet(e.getAnswer());
            } else if ("paraphrase".equalsIgnoreCase(mode)) {
                q = paraphrase(e.getQuestion());
            } else {
                q = e.getQuestion();
            }
            q = cleanQuery(q);
            if (q == null || q.isBlank() || e.getKbId() == null) {
                continue;
            }
            samples.add(new EvalService.Sample(
                    "s" + i, q, List.of(e.getKbId()), "auto-" + mode));
        }
        log.info("[EvalDataset] 构建自动评测集: 请求 {} 条，实际 {} 条", size, samples.size());
        return samples;
    }

    /**
     * 读取独立人工标注集，并把 expectedSources（文件名）反查成 kb_id 列表。
     * 一条 source 可能对应多个切片，因此全部纳入 expectedKbIds。
     */
    public List<EvalService.Sample> loadLabeledSet() {
        Path path = Path.of(labeledSetPath).toAbsolutePath().normalize();
        if (!Files.exists(path)) {
            throw new IllegalStateException("标注集文件不存在: " + path);
        }

        List<EvalService.Sample> samples = new ArrayList<>();
        List<String> lines;
        try {
            lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("读取标注集失败: " + path + " -> " + e.getMessage(), e);
        }

        int lineNo = 0;
        for (String raw : lines) {
            lineNo++;
            String line = raw == null ? "" : raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            try {
                JsonNode node = JSON.readTree(line);
                String id = text(node, "id");
                String query = cleanQuery(text(node, "query"));
                String difficulty = text(node, "difficulty");
                String note = text(node, "note");

                List<String> sources = new ArrayList<>();
                JsonNode sourceNode = node.get("expectedSources");
                if (sourceNode != null && sourceNode.isArray()) {
                    sourceNode.forEach(x -> {
                        if (x != null && !x.asText().isBlank()) {
                            sources.add(x.asText());
                        }
                    });
                }
                if (id.isBlank() || query == null || query.isBlank() || sources.isEmpty()) {
                    throw new IllegalArgumentException("id/query/expectedSources 不能为空");
                }

                LinkedHashSet<String> kbIds = new LinkedHashSet<>();
                for (String source : sources) {
                    List<String> found = jdbcTemplate.queryForList(
                            "SELECT DISTINCT metadata->>'kb_id' FROM cs_vector_store " +
                            "WHERE metadata->>'source' = ? AND metadata->>'kb_id' IS NOT NULL",
                            String.class, source);
                    if (found == null || found.isEmpty()) {
                        throw new IllegalStateException("source 在 cs_vector_store 中找不到 kb_id: " + source);
                    }
                    kbIds.addAll(found);
                }
                if (kbIds.isEmpty()) {
                    throw new IllegalStateException("expectedSources 未解析出 kb_id");
                }

                String sampleNote = "difficulty=" + difficulty
                        + "; expectedSources=" + String.join(",", sources)
                        + (note == null || note.isBlank() ? "" : "; note=" + note);
                samples.add(new EvalService.Sample(id, query, new ArrayList<>(kbIds), sampleNote,
                        new ArrayList<>(sources)));
            } catch (Exception e) {
                throw new IllegalStateException("解析标注集第 " + lineNo + " 行失败: " + e.getMessage(), e);
            }
        }

        if (samples.isEmpty()) {
            throw new IllegalStateException("标注集为空: " + path);
        }
        log.info("[EvalDataset] 加载独立标注集: path={}, samples={}", path, samples.size());
        return samples;
    }

    /** 去掉 query 开头的机械序号前缀，trim 后返回。 */
    public static String cleanQuery(String query) {
        if (query == null) {
            return null;
        }
        String out = query.trim();
        String previous;
        do {
            previous = out;
            out = QUERY_PREFIX.matcher(out).replaceFirst("").trim();
        } while (!out.equals(previous));
        return out;
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? "" : v.asText("");
    }

    private String paraphrase(String question) {
        if (question == null || question.isBlank()) {
            return question;
        }
        try {
            String prompt = "请把下面这个问题换一种说法表达，要求：\n"
                    + "1. 意思完全不变，但用词和句式要明显不同\n"
                    + "2. 不要出现原句中连续 6 个字以上\n"
                    + "3. 只输出改写后的句子，不要解释\n\n原问题：" + question;
            String out = openAiChatModel.call(new Prompt(prompt)).getResult().getOutput().getText();
            if (out == null || out.isBlank()) {
                return question;
            }
            String cleaned = out.trim().replaceAll("^[\\s\\\"']+|[\\s\\\"']+$", "");
            return cleanQuery(cleaned.length() > 100 ? cleaned.substring(0, 100) : cleaned);
        } catch (Exception e) {
            log.debug("[EvalDataset] 改写失败，回退原问题: {}", e.getMessage());
            return question;
        }
    }

    /** 从答案里取一段作为 query（去掉 markdown 标记，截断到 40 字） */
    private String answerSnippet(String answer) {
        if (answer == null || answer.isBlank()) {
            return null;
        }
        String t = answer.replaceAll("[#*`>\\-]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (t.length() > 40) {
            t = t.substring(0, 40);
        }
        return cleanQuery(t);
    }
}
