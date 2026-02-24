package com.qh.ai_agent.model;

import lombok.Data;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Data
public class PromptTemplate {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{([^}]+)\\}");

    private String templateName;
    private String content;
    private Map<String, Object> variables;

    public PromptTemplate(String templateName, String content) {
        this.templateName = templateName;
        this.content = content;
    }

    /**
     * 渲染模板，替换变量占位符
     * 变量格式：{variable_name}
     */
    public String render() {
        if (content == null) {
            return "";
        }
        if (variables == null || variables.isEmpty()) {
            return content;
        }

        String result = content;
        Matcher matcher = VARIABLE_PATTERN.matcher(result);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String varName = matcher.group(1);
            Object varValue = variables.get(varName);
            String replacement = varValue != null ? varValue.toString() : matcher.group(0);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);

        return sb.toString();
    }

    /**
     * 使用指定变量渲染模板
     */
    public String render(Map<String, Object> vars) {
        this.variables = vars;
        return render();
    }
}
