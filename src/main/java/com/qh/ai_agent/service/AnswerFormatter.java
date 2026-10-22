package com.qh.ai_agent.service;

import java.util.regex.Pattern;

/**
 * 回答格式清洗：去掉知识库内部标记，保证输出是面向用户的自然语言。
 *
 * 背景：知识库条目以 "Q: ...\nA: ..." 存储，早期提示词要求"原文照搬"，
 * 导致用户会看到 "Q: 想换一家网络运营商... A: Q: 想换一家网络运营商..." 这种重复问句。
 * 这里做一层兜底：即使模型或旧数据带了标记，最终输出也会被清洗。
 */
public final class AnswerFormatter {

    /** 行首的 Q:/A:/问：/答： 标记 */
    private static final Pattern QA_MARK = Pattern.compile("(?m)^\\s*[QA][:：]\\s*");
    private static final Pattern QA_CN_MARK = Pattern.compile("(?m)^\\s*[问答][:：]\\s*");
    /** 整行 Q: 问句（通常是与用户问题重复的检索问句） */
    private static final Pattern QUESTION_LINE = Pattern.compile("(?m)^\\s*[Q问][:：].*$");

    private AnswerFormatter() {
    }

    public static String clean(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String out = QUESTION_LINE.matcher(text).replaceAll("");
        out = QA_MARK.matcher(out).replaceAll("");
        out = QA_CN_MARK.matcher(out).replaceAll("");
        out = out.replaceAll("\\n{3,}", "\n\n").trim();
        return out.isBlank() ? text.trim() : out;
    }
}
