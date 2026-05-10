package com.qh.ai_agent.knowledge.retriever;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.cjk.CJKBigramFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;

/**
 * 中文文本分析器。
 * 使用 Lucene 内置的 StandardTokenizer + CJKBigramFilter 处理中文分词，
 * 无需额外依赖第三方分词库。
 *
 * CJKBigramFilter 将 CJK 字符序列转为 bigram（双字词组），
 * 例如："查询信用卡年费" → "查询", "询信", "信用", "用卡", "卡年", "年费"
 * 这对于 BM25 中文检索有较好的效果。
 */
public class JiebaAnalyzer extends Analyzer {

    @Override
    protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer tokenizer = new StandardTokenizer();
        TokenStream result = new CJKBigramFilter(tokenizer);
        return new TokenStreamComponents(tokenizer, result);
    }
}
