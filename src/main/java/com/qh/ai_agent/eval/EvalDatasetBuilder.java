package com.qh.ai_agent.eval;

import com.qh.ai_agent.knowledge.parser.KbEntry;
import com.qh.ai_agent.knowledge.parser.UnifiedKbLoader;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 评测集构建器：从现有知识库反推样本（用问题当 query，条目自身 kbId 当标准答案）。
 *
 * 为什么这样做：手工标注 240 条成本很高，而"知识库自带的问题"本身就是真实用户问法的近似，
 * 能快速得到一个可复现的基线。后续可以在这个基础上补充难负样本和模糊样本。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EvalDatasetBuilder {

    private final UnifiedKbLoader unifiedKbLoader;
    private final ChatModel openAiChatModel;

    public List<EvalService.Sample> buildFromKnowledgeBase(int size) {
        return build(size, "question");
    }

    /**
     * 构建评测集。
     *
     * mode = question：直接用知识库问题当 query（简单模式，等于开卷，分数会接近 1.0）
     * mode = answer  ：用答案片段当 query（困难模式，要求语义检索能对上，分数才有参考意义）
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
        java.util.Collections.shuffle(shuffled, random);

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
            if (q == null || q.isBlank() || e.getKbId() == null) {
                continue;
            }
            samples.add(new EvalService.Sample(
                    "s" + i, q, List.of(e.getKbId()), "auto-" + mode));
        }
        log.info("[EvalDataset] 构建评测集: 请求 {} 条，实际 {} 条", size, samples.size());
        return samples;
    }
    /**
     * 用 LLM 把知识库问题改写成"同义但不同措辞"的 query。
     *
     * 为什么需要：直接拿知识库问题当 query 等于开卷考试（向量索引里就是同一段文本），
     * 分数必然接近 1.0，没有参考价值。改写后才能测出真实的语义泛化能力。
     */
    private String paraphrase(String question) {
        if (question == null || question.isBlank()) {
            return question;
        }
        try {
            String prompt = "请把下面这个问题换一种说法表达，要求：\n"
                    + "1. 意思完全不变，但用词和句式要明显不同\n"
                    + "2. 不要出现原句中的连续 6 个字以上\n"
                    + "3. 只输出改写后的句子，不要解释\n\n原问题：" + question;
            String out = openAiChatModel.call(new Prompt(prompt)).getResult().getOutput().getText();
            if (out == null || out.isBlank()) {
                return question;
            }
            String cleaned = out.trim().replaceAll("^[\"'”\s]+|[\"'”\s]+$", "");
            return cleaned.length() > 100 ? cleaned.substring(0, 100) : cleaned;
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
        return t;
    }
}