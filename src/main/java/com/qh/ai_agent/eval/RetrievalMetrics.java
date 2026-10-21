package com.qh.ai_agent.eval;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 检索指标：Recall@K / MRR / nDCG@K。
 *
 * 纯函数实现，便于单测；不依赖任何外部服务。
 */
public final class RetrievalMetrics {

    private RetrievalMetrics() {
    }

    /**
     * Recall@K = 命中的应有条目数 / 应有条目总数
     */
    public static double recallAtK(List<String> retrieved, Set<String> expected, int k) {
        if (expected == null || expected.isEmpty()) {
            return 1.0;
        }
        Set<String> topK = new HashSet<>(retrieved.subList(0, Math.min(k, retrieved.size())));
        int hit = 0;
        for (String e : expected) {
            if (topK.contains(e)) {
                hit++;
            }
        }
        return (double) hit / expected.size();
    }

    /**
     * MRR = 平均(1 / 第一个正确结果的排名)
     */
    public static double mrr(List<String> retrieved, Set<String> expected) {
        if (expected == null || expected.isEmpty()) {
            return 1.0;
        }
        for (int i = 0; i < retrieved.size(); i++) {
            if (expected.contains(retrieved.get(i))) {
                return 1.0 / (i + 1);
            }
        }
        return 0.0;
    }

    /**
     * nDCG@K = DCG@K / IDCG@K，其中 DCG = sum(rel_i / log2(i+1))
     * 这里采用二值相关性（命中=1，未命中=0）
     */
    public static double ndcgAtK(List<String> retrieved, Set<String> expected, int k) {
        if (expected == null || expected.isEmpty()) {
            return 1.0;
        }
        int limit = Math.min(k, retrieved.size());
        double dcg = 0.0;
        for (int i = 0; i < limit; i++) {
            if (expected.contains(retrieved.get(i))) {
                dcg += 1.0 / (Math.log(i + 2) / Math.log(2));
            }
        }
        int ideal = Math.min(expected.size(), k);
        double idcg = 0.0;
        for (int i = 0; i < ideal; i++) {
            idcg += 1.0 / (Math.log(i + 2) / Math.log(2));
        }
        return idcg == 0 ? 0.0 : dcg / idcg;
    }
}
