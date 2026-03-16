package fun.javierchen.jcaiagentbackend.rag.benchmark.metrics;

import fun.javierchen.jcaiagentbackend.rag.benchmark.model.LatencyStats;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 信息检索评估指标计算工具类
 * 所有方法均为无状态纯函数
 *
 * @author JavierChen
 */
public final class RetrievalMetrics {

    private RetrievalMetrics() {
    }

    /**
     * Hit@K: top-K 结果中是否至少有一个相关结果
     *
     * @param relevanceFlags 按排名顺序的相关性标记列表
     * @param k              截断位置
     * @return true 如果 top-K 中至少有一个相关结果
     */
    public static boolean hitAtK(List<Boolean> relevanceFlags, int k) {
        if (relevanceFlags == null || relevanceFlags.isEmpty() || k <= 0) {
            return false;
        }
        int limit = Math.min(k, relevanceFlags.size());
        for (int i = 0; i < limit; i++) {
            if (Boolean.TRUE.equals(relevanceFlags.get(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Reciprocal Rank: 第一个相关结果排名的倒数
     * 如果没有相关结果则返回 0.0
     *
     * @param relevanceFlags 按排名顺序的相关性标记列表
     * @return 1/rank 或 0.0
     */
    public static double reciprocalRank(List<Boolean> relevanceFlags) {
        if (relevanceFlags == null || relevanceFlags.isEmpty()) {
            return 0.0;
        }
        for (int i = 0; i < relevanceFlags.size(); i++) {
            if (Boolean.TRUE.equals(relevanceFlags.get(i))) {
                return 1.0 / (i + 1);
            }
        }
        return 0.0;
    }

    /**
     * Precision@K: top-K 中相关文档的比例
     * P@K = (top-K 中相关数) / K
     *
     * @param relevanceFlags 按排名顺序的相关性标记列表
     * @param k              截断位置
     * @return 精确率 [0.0, 1.0]
     */
    public static double precisionAtK(List<Boolean> relevanceFlags, int k) {
        if (relevanceFlags == null || relevanceFlags.isEmpty() || k <= 0) {
            return 0.0;
        }
        int limit = Math.min(k, relevanceFlags.size());
        long relevant = 0;
        for (int i = 0; i < limit; i++) {
            if (Boolean.TRUE.equals(relevanceFlags.get(i))) {
                relevant++;
            }
        }
        return (double) relevant / k;
    }

    /**
     * Recall@K: top-K 中检索到的相关文档占全部相关文档的比例
     * R@K = (top-K 中相关数) / totalRelevant
     *
     * @param relevanceFlags 按排名顺序的相关性标记列表
     * @param k              截断位置
     * @param totalRelevant  语料库中该查询的总相关文档数
     * @return 召回率 [0.0, 1.0]
     */
    public static double recallAtK(List<Boolean> relevanceFlags, int k, int totalRelevant) {
        if (relevanceFlags == null || relevanceFlags.isEmpty() || k <= 0 || totalRelevant <= 0) {
            return 0.0;
        }
        int limit = Math.min(k, relevanceFlags.size());
        long relevant = 0;
        for (int i = 0; i < limit; i++) {
            if (Boolean.TRUE.equals(relevanceFlags.get(i))) {
                relevant++;
            }
        }
        return (double) relevant / totalRelevant;
    }

    /**
     * F1@K: Precision 和 Recall 的调和平均
     * F1 = 2 × P × R / (P + R)
     *
     * @param precision 精确率
     * @param recall    召回率
     * @return F1 分数 [0.0, 1.0]
     */
    public static double f1AtK(double precision, double recall) {
        if (precision + recall == 0) {
            return 0.0;
        }
        return 2.0 * precision * recall / (precision + recall);
    }

    /**
     * Average Precision@K (AP@K):
     * AP@K = (1 / min(totalRelevant, K)) × Σ_{i=1}^{K} [P@i × rel(i)]
     * 仅在相关位置累加 Precision@i
     *
     * @param relevanceFlags 按排名顺序的相关性标记列表
     * @param k              截断位置
     * @param totalRelevant  总相关文档数
     * @return AP 值 [0.0, 1.0]
     */
    public static double averagePrecisionAtK(List<Boolean> relevanceFlags, int k, int totalRelevant) {
        if (relevanceFlags == null || relevanceFlags.isEmpty() || k <= 0 || totalRelevant <= 0) {
            return 0.0;
        }
        int limit = Math.min(k, relevanceFlags.size());
        double sumPrecision = 0.0;
        int relevantSoFar = 0;

        for (int i = 0; i < limit; i++) {
            if (Boolean.TRUE.equals(relevanceFlags.get(i))) {
                relevantSoFar++;
                // P@(i+1) = relevantSoFar / (i+1)
                sumPrecision += (double) relevantSoFar / (i + 1);
            }
        }

        int denominator = Math.min(totalRelevant, k);
        return sumPrecision / denominator;
    }

    /**
     * NDCG@K: 归一化折损累积增益
     * DCG@K  = Σ_{i=1}^{K} (2^rel_i - 1) / log2(i + 1)
     * IDCG@K = DCG@K on ideal ordering (gains sorted descending)
     * NDCG@K = DCG@K / IDCG@K (若 IDCG=0 则返回 0.0)
     *
     * @param gains 按排名顺序的相关度等级列表
     * @param k     截断位置
     * @return NDCG 值 [0.0, 1.0]
     */
    public static double ndcgAtK(List<Integer> gains, int k) {
        if (gains == null || gains.isEmpty() || k <= 0) {
            return 0.0;
        }

        int limit = Math.min(k, gains.size());

        // DCG@K
        double dcg = 0.0;
        for (int i = 0; i < limit; i++) {
            int rel = gains.get(i);
            dcg += (Math.pow(2, rel) - 1) / log2(i + 2); // log2(i+1+1) because i is 0-based
        }

        // IDCG@K: sort all gains descending, take top-K
        List<Integer> idealGains = new ArrayList<>(gains);
        idealGains.sort(Collections.reverseOrder());
        int idealLimit = Math.min(k, idealGains.size());

        double idcg = 0.0;
        for (int i = 0; i < idealLimit; i++) {
            int rel = idealGains.get(i);
            idcg += (Math.pow(2, rel) - 1) / log2(i + 2);
        }

        if (idcg == 0.0) {
            return 0.0;
        }
        return dcg / idcg;
    }

    /**
     * 计算平均相似度分数
     *
     * @param scores 各文档的相似度分数列表
     * @return 平均值
     */
    public static double averageSimilarityScore(List<Double> scores) {
        if (scores == null || scores.isEmpty()) {
            return 0.0;
        }
        return scores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    /**
     * 计算延迟分位数统计
     *
     * @param latencyNanosList 各查询的延迟（纳秒）列表
     * @return 延迟统计信息（毫秒）
     */
    public static LatencyStats computeLatencyStats(List<Long> latencyNanosList) {
        if (latencyNanosList == null || latencyNanosList.isEmpty()) {
            return LatencyStats.builder()
                    .p50Ms(0).p95Ms(0).p99Ms(0)
                    .meanMs(0).minMs(0).maxMs(0)
                    .build();
        }

        long[] sorted = latencyNanosList.stream().mapToLong(Long::longValue).sorted().toArray();
        int n = sorted.length;

        return LatencyStats.builder()
                .p50Ms(sorted[percentileIndex(0.50, n)] / 1_000_000.0)
                .p95Ms(sorted[percentileIndex(0.95, n)] / 1_000_000.0)
                .p99Ms(sorted[percentileIndex(0.99, n)] / 1_000_000.0)
                .meanMs(Arrays.stream(sorted).average().orElse(0) / 1_000_000.0)
                .minMs(sorted[0] / 1_000_000.0)
                .maxMs(sorted[n - 1] / 1_000_000.0)
                .build();
    }

    // --- 内部辅助方法 ---

    private static double log2(double x) {
        return Math.log(x) / Math.log(2);
    }

    private static int percentileIndex(double percentile, int n) {
        int index = (int) Math.ceil(percentile * n) - 1;
        return Math.max(0, Math.min(index, n - 1));
    }
}