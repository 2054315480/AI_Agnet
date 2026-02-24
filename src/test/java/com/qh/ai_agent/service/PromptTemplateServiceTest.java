package com.qh.ai_agent.service;

import com.qh.ai_agent.exception.PromptTemplateException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class PromptTemplateServiceTest {

    @InjectMocks
    private PromptTemplateService promptTemplateService;

    @Test
    void testLoadExistingTemplate() {
        String result = promptTemplateService.loadTemplate("love-advisor").render();
        assertNotNull(result);
        assertTrue(result.contains("情感顾问"));
        assertTrue(result.contains("恋爱大师"));
    }

    @Test
    void testLoadTemplateWithVariables() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("task_description", "分析这张图片的布局");

        String result = promptTemplateService.renderTemplate("image-analyst", variables);
        assertNotNull(result);
        assertTrue(result.contains("分析这张图片的布局"));
    }

    @Test
    void testLoadNonExistentTemplate() {
        assertThrows(PromptTemplateException.class, () -> {
            promptTemplateService.loadTemplate("non-existent-template");
        });
    }

    @Test
    void testLoadTemplateWithNullName() {
        assertThrows(IllegalArgumentException.class, () -> {
            promptTemplateService.loadTemplate(null);
        });
    }

    @Test
    void testLoadTemplateWithEmptyName() {
        assertThrows(IllegalArgumentException.class, () -> {
            promptTemplateService.loadTemplate("");
        });
    }

    @Test
    void testTemplateCaching() {
        var template1 = promptTemplateService.loadTemplate("love-advisor");
        var template2 = promptTemplateService.loadTemplate("love-advisor");

        assertSame(template1, template2, "Template should be cached");
    }

    @Test
    void testClearCache() {
        promptTemplateService.loadTemplate("love-advisor");
        assertFalse(promptTemplateService.getCachedTemplates().isEmpty());

        promptTemplateService.clearCache();
        assertTrue(promptTemplateService.getCachedTemplates().isEmpty());
    }

    @Test
    void testPreloadTemplates() {
        promptTemplateService.preloadTemplates();
        Map<String, com.qh.ai_agent.model.PromptTemplate> templates = promptTemplateService.getCachedTemplates();

        assertFalse(templates.isEmpty());
        assertTrue(templates.containsKey("love-advisor"));
        assertTrue(templates.containsKey("image-analyst"));
    }

    @Test
    void testRenderTemplateWithEmptyVariables() {
        String result = promptTemplateService.renderTemplate("love-advisor", new HashMap<>());
        assertNotNull(result);
        assertTrue(result.contains("情感顾问"));
    }

    @Test
    void testRenderTemplateWithNullVariables() {
        String result = promptTemplateService.renderTemplate("love-advisor", null);
        assertNotNull(result);
    }
}
