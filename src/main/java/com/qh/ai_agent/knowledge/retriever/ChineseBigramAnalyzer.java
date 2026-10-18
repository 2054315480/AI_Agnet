package com.qh.ai_agent.knowledge.retriever;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.cjk.CJKBigramFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;

/**
 * 中文 Bigram 分析器（注意：不是结巴分词）。
 *
 * 真实实现为 Lucene 内置的 StandardTokenizer + CJKBigramFilter：
 *   1. StandardTokenizer 负责把中英文切成基础 token；
 *   2. CJKBigramFilter 把连续 CJK 字符组合成相邻双字 bigram。
 *
 * 例如 查询信用卡年费 会得到：查询 / 询信 / 信用 / 用卡 / 卡年 / 年费。
 * 这种切分不需要额外词典依赖，适合小规模 FAQ/知识库的 BM25 召回，
 * 但局限也明确：不做词性、停用词、未登录词识别，会产生一定噪声 bigram，
 * 因此检索侧需要配合最少命中词比例等约束控制精确率。
 *
 * 本项目 pom.xml 没有 jieba 依赖，本类也不伪装成结巴分词；
 * 若后续要接入真实 jieba，应新增依赖并单独实现 JiebaAnalyzer。
 */
public class ChineseBigramAnalyzer extends Analyzer {

    @Override
    protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer tokenizer = new StandardTokenizer();
        TokenStream result = new CJKBigramFilter(tokenizer);
        return new TokenStreamComponents(tokenizer, result);
    }
}
