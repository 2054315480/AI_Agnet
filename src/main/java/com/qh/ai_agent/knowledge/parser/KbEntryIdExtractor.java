package com.qh.ai_agent.knowledge.parser;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从文本中提取 [条目编号:kb_qa_xxx] 标记。
 *
 * 兼容性：PDF 解析时反引号、星号、方括号可能变形，
 * 所以直接用正则匹配 ID 模式，不依赖周围格式标记。
 */
public class KbEntryIdExtractor {

    /** 直接匹配 kb_qa_ 前缀 + 10位hex 的 ID 模式 */
    private static final Pattern KB_ID_PATTERN =
            Pattern.compile("kb_qa_[a-f0-9]{10}");

    /** 兼容 Markdown 中的完整标注格式：
     *  [条目编号:kb_qa_xxx] 或 *[条目编号:`kb_qa_xxx`]*  */
    private static final Pattern FULL_TAG_PATTERN =
            Pattern.compile("\\[条目编号[:：]\\s*`?\\s*(kb_qa_[a-f0-9]{10})\\s*`?\\s*]");

    /**
     * 从文本中提取 kb_qa_xxx 编号。
     * @return 提取到的编号，未找到返回 null
     */
    public static String extract(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        // 先尝试完整标注格式
        Matcher fullMatcher = FULL_TAG_PATTERN.matcher(text);
        if (fullMatcher.find()) {
            return fullMatcher.group(1);
        }
        // 回退：直接匹配 ID 模式（兼容 PDF 格式变形）
        Matcher idMatcher = KB_ID_PATTERN.matcher(text);
        if (idMatcher.find()) {
            return idMatcher.group();
        }
        return null;
    }
}
