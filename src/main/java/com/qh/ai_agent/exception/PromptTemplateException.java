package com.qh.ai_agent.exception;

public class PromptTemplateException extends RuntimeException {

    private final String templateName;

    public PromptTemplateException(String message, String templateName) {
        super(message);
        this.templateName = templateName;
    }

    public PromptTemplateException(String message, String templateName, Throwable cause) {
        super(message, cause);
        this.templateName = templateName;
    }

    public PromptTemplateException(String message) {
        super(message);
        this.templateName = null;
    }

    public PromptTemplateException(String message, Throwable cause) {
        this(message, null, cause);
    }

    public String getTemplateName() {
        return templateName;
    }
}
