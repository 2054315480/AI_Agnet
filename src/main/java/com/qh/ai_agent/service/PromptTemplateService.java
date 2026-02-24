package com.qh.ai_agent.service;

import com.qh.ai_agent.exception.PromptTemplateException;
import com.qh.ai_agent.model.PromptTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class PromptTemplateService {

    private static final String TEMPLATE_LOCATION = "classpath:prompts/*.txt";

    private final Map<String, PromptTemplate> templateCache = new ConcurrentHashMap<>();
    private final PathMatchingResourcePatternResolver resourceResolver = new PathMatchingResourcePatternResolver();

    /**
     * 加载指定名称的模板
     */
    public PromptTemplate loadTemplate(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Template name cannot be null or empty");
        }

        // 先从缓存中查找
        if (templateCache.containsKey(name)) {
            log.debug("Template '{}' found in cache", name);
            return templateCache.get(name);
        }

        // 从文件系统加载
        return loadTemplateFromFile(name);
    }

    /**
     * 渲染模板
     */
    public String renderTemplate(String name, Map<String, Object> variables) {
        PromptTemplate template = loadTemplate(name);
        return template.render(variables);
    }

    /**
     * 预加载所有模板
     */
    public void preloadTemplates() {
        try {
            Resource[] resources = resourceResolver.getResources(TEMPLATE_LOCATION);
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename != null) {
                    String templateName = filename.substring(0, filename.lastIndexOf('.'));
                    loadTemplateFromFile(templateName);
                }
            }
            log.info("Preloaded {} prompt templates", templateCache.size());
        } catch (IOException e) {
            log.error("Failed to preload templates", e);
        }
    }

    /**
     * 清除模板缓存
     */
    public void clearCache() {
        templateCache.clear();
        log.info("Template cache cleared");
    }

    /**
     * 获取缓存中的所有模板名称
     */
    public Map<String, PromptTemplate> getCachedTemplates() {
        return new HashMap<>(templateCache);
    }

    private PromptTemplate loadTemplateFromFile(String name) {
        String templatePath = "classpath:prompts/" + name + ".txt";
        try {
            Resource resource = resourceResolver.getResource(templatePath);
            if (!resource.exists()) {
                throw new PromptTemplateException("Template not found: " + name);
            }

            String content = resource.getContentAsString(StandardCharsets.UTF_8);
            PromptTemplate template = new PromptTemplate(name, content);
            templateCache.put(name, template);
            log.info("Loaded template '{}' from {}", name, templatePath);
            return template;
        } catch (IOException e) {
            throw new PromptTemplateException("Failed to load template: " + name, e);
        }
    }
}
