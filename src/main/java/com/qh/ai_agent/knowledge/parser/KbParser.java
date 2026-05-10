package com.qh.ai_agent.knowledge.parser;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * 知识库文档解析器接口。
 * 每种文件格式（MD/DOCX/PDF）一个实现。
 */
public interface KbParser {

    /**
     * 解析知识库文件，提取所有 QA 条目。
     *
     * @param file   知识库文件
     * @param domain 所属行业（banking / telecom / ecommerce）
     * @return 解析出的 KbEntry 列表
     */
    List<KbEntry> parse(File file, String domain) throws IOException;

    /**
     * 返回此解析器支持的文件扩展名（不含点）。
     */
    String supportedExtension();
}
