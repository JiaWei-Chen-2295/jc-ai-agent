package fun.javierchen.jcaiagentbackend.rag.benchmark.metrics;

import fun.javierchen.jcaiagentbackend.rag.benchmark.model.LatencyStats;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * RetrievalMetrics 指标计算单元测试
 *
 * @author JavierChen
 */
class RetrievalMetricsTest {

    // ==================== Hit Rate@K ====================

    @Nested
    @DisplayName("Hit Rate@K")
    class HitAtKTests {

        @Test
        @DisplayName("第1位命中 → hit@1=true, hit@3=true, hit@5=true")
        void firstPositionHit() {
            List<Boolean> flags = Arrays.asList(true, false, false, false, false);
            assertThat(RetrievalMetrics.hitAtK(flags, 1)).isTrue();
            assertThat(RetrievalMetrics.hitAtK(flags, 3)).isTrue();
            assertThat(RetrievalMetrics.hitAtK(flags, 5)).isTrue();
        }

        @Test
        @DisplayName("第3位命中 → hit@1=false, hit@3=true")
        void thirdPositionHit() {
            List<Boolean> flags = Arrays.asList(false, false, true, false, false);
            assertThat(RetrievalMetrics.hitAtK(flags, 1)).isFalse();
            assertThat(RetrievalMetrics.hitAtK(flags, 3)).isTrue();
        }

        @Test
        @DisplayName("全部未命中 → hit@K=false")
        void noHit() {
            List<Boolean> flags = Arrays.asList(false, false, false, false, false);
            assertThat(RetrievalMetrics.hitAtK(flags, 1)).isFalse();
            assertThat(RetrievalMetrics.hitAtK(flags, 3)).isFalse();
            assertThat(RetrievalMetrics.hitAtK(flags, 5)).isFalse();
        }

        @Test
        @DisplayName("空列表 → false")
        void emptyList() {
            assertThat(RetrievalMetrics.hitAtK(Collections.emptyList(), 3)).isFalse();
            assertThat(RetrievalMetrics.hitAtK(null, 3)).isFalse();
        }

        @Test
        @DisplayName("K 大于结果数 → 仅在可用范围内判断")
        void kLargerThanList() {
            List<Boolean> flags = Arrays.asList(false, true);
            assertThat(RetrievalMetrics.hitAtK(flags, 5)).isTrue();
        }
    }

    // ==================== Reciprocal Rank ====================

    @Nested
    @DisplayName("Reciprocal Rank")
    class ReciprocalRankTests {

        @Test
        @DisplayName("第1位命中 → RR=1.0")
        void firstPosition() {
            List<Boolean> flags = Arrays.asList(true, false, false);
            assertThat(RetrievalMetrics.reciprocalRank(flags)).isCloseTo(1.0, within(1e-9));
        }

        @Test
        @DisplayName("第3位命中 → RR=1/3")
        void thirdPosition() {
            List<Boolean> flags = Arrays.asList(false, false, true, false);
            assertThat(RetrievalMetrics.reciprocalRank(flags)).isCloseTo(1.0 / 3, within(1e-9));
        }

        @Test
        @DisplayName("无命中 → RR=0.0")
        void noHit() {
            List<Boolean> flags = Arrays.asList(false, false, false);
            assertThat(RetrievalMetrics.reciprocalRank(flags)).isCloseTo(0.0, within(1e-9));
        }

        @Test
        @DisplayName("空列表 → RR=0.0")
        void emptyList() {
            assertThat(RetrievalMetrics.reciprocalRank(Collections.emptyList())).isCloseTo(0.0, within(1e-9));
        }
    }

    // ==================== Precision@K ====================

    @Nested
    @DisplayName("Precision@K")
    class PrecisionTests {

        @Test
        @DisplayName("top-3 中 2 个相关 → P@3=2/3")
        void normalCase() {
            List<Boolean> flags = Arrays.asList(true, false, true, false, false);
            assertThat(RetrievalMetrics.precisionAtK(flags, 3)).isCloseTo(2.0 / 3, within(1e-9));
        }

        @Test
        @DisplayName("全部相关 → P@K=1.0")
        void allRelevant() {
            List<Boolean> flags = Arrays.asList(true, true, true);
            assertThat(RetrievalMetrics.precisionAtK(flags, 3)).isCloseTo(1.0, within(1e-9));
        }

        @Test
        @DisplayName("全部不相关 → P@K=0.0")
        void noneRelevant() {
            List<Boolean> flags = Arrays.asList(false, false, false);
            assertThat(RetrievalMetrics.precisionAtK(flags, 3)).isCloseTo(0.0, within(1e-9));
        }

        @Test
        @DisplayName("K 大于结果数 → 以 K 为分母")
        void kLargerThanList() {
            // 2 个结果中 1 个相关, K=5 → P@5 = 1/5
            List<Boolean> flags = Arrays.asList(true, false);
            assertThat(RetrievalMetrics.precisionAtK(flags, 5)).isCloseTo(1.0 / 5, within(1e-9));
        }
    }

    // ==================== Recall@K ====================

    @Nested
    @DisplayName("Recall@K")
    class RecallTests {

        @Test
        @DisplayName("top-3 中检索到 2 个，总相关 4 个 → R@3=2/4=0.5")
        void normalCase() {
            List<Boolean> flags = Arrays.asList(true, false, true, false, false);
            assertThat(RetrievalMetrics.recallAtK(flags, 3, 4)).isCloseTo(0.5, within(1e-9));
        }

        @Test
        @DisplayName("全部召回 → R@K=1.0")
        void fullRecall() {
            List<Boolean> flags = Arrays.asList(true, true);
            assertThat(RetrievalMetrics.recallAtK(flags, 2, 2)).isCloseTo(1.0, within(1e-9));
        }

        @Test
        @DisplayName("totalRelevant=0 → R@K=0.0")
        void zeroTotalRelevant() {
            List<Boolean> flags = Arrays.asList(true, false);
            assertThat(RetrievalMetrics.recallAtK(flags, 2, 0)).isCloseTo(0.0, within(1e-9));
        }
    }

    // ==================== F1@K ====================

    @Nested
    @DisplayName("F1@K")
    class F1Tests {

        @Test
        @DisplayName("P=0.5, R=0.5 → F1=0.5")
        void balanced() {
            assertThat(RetrievalMetrics.f1AtK(0.5, 0.5)).isCloseTo(0.5, within(1e-9));
        }

        @Test
        @DisplayName("P=1.0, R=1.0 → F1=1.0")
        void perfect() {
            assertThat(RetrievalMetrics.f1AtK(1.0, 1.0)).isCloseTo(1.0, within(1e-9));
        }

        @Test
        @DisplayName("P=0, R=0 → F1=0.0")
        void zeroZero() {
            assertThat(RetrievalMetrics.f1AtK(0.0, 0.0)).isCloseTo(0.0, within(1e-9));
        }

        @Test
        @DisplayName("P=1.0, R=0.5 → F1=2/3")
        void asymmetric() {
            assertThat(RetrievalMetrics.f1AtK(1.0, 0.5)).isCloseTo(2.0 / 3, within(1e-9));
        }
    }

    // ==================== Average Precision@K ====================

    @Nested
    @DisplayName("Average Precision@K (MAP)")
    class AveragePrecisionTests {

        @Test
        @DisplayName("标准 AP 计算: [T,F,T,F,T] totalRelevant=3, K=5")
        void standardCase() {
            // 位置1: relevant → P@1=1/1=1.0
            // 位置3: relevant → P@3=2/3
            // 位置5: relevant → P@5=3/5
            // AP@5 = (1.0 + 2/3 + 3/5) / min(3,5) = (1.0 + 0.6667 + 0.6) / 3 = 0.7556
            List<Boolean> flags = Arrays.asList(true, false, true, false, true);
            double ap = RetrievalMetrics.averagePrecisionAtK(flags, 5, 3);
            assertThat(ap).isCloseTo((1.0 + 2.0 / 3 + 3.0 / 5) / 3, within(1e-9));
        }

        @Test
        @DisplayName("全部命中 → AP=1.0")
        void allRelevant() {
            List<Boolean> flags = Arrays.asList(true, true, true);
            double ap = RetrievalMetrics.averagePrecisionAtK(flags, 3, 3);
            // (1/1 + 2/2 + 3/3) / 3 = 3/3 = 1.0
            assertThat(ap).isCloseTo(1.0, within(1e-9));
        }

        @Test
        @DisplayName("全部未命中 → AP=0.0")
        void noneRelevant() {
            List<Boolean> flags = Arrays.asList(false, false, false);
            assertThat(RetrievalMetrics.averagePrecisionAtK(flags, 3, 3)).isCloseTo(0.0, within(1e-9));
        }
    }

    // ==================== NDCG@K ====================

    @Nested
    @DisplayName("NDCG@K")
    class NdcgTests {

        @Test
        @DisplayName("理想排序 → NDCG=1.0")
        void idealOrder() {
            // gains 已经按降序排列
            List<Integer> gains = Arrays.asList(3, 2, 1, 0);
            assertThat(RetrievalMetrics.ndcgAtK(gains, 4)).isCloseTo(1.0, within(1e-9));
        }

        @Test
        @DisplayName("逆序排列 → NDCG<1.0")
        void reverseOrder() {
            List<Integer> gains = Arrays.asList(0, 1, 2, 3);
            double ndcg = RetrievalMetrics.ndcgAtK(gains, 4);
            assertThat(ndcg).isGreaterThan(0.0);
            assertThat(ndcg).isLessThan(1.0);
        }

        @Test
        @DisplayName("全部为 0 → NDCG=0.0")
        void allZero() {
            List<Integer> gains = Arrays.asList(0, 0, 0);
            assertThat(RetrievalMetrics.ndcgAtK(gains, 3)).isCloseTo(0.0, within(1e-9));
        }

        @Test
        @DisplayName("二元相关 [1,0,1] → 手工验证 NDCG@3")
        void binaryRelevance() {
            List<Integer> gains = Arrays.asList(1, 0, 1);
            // DCG@3 = (2^1-1)/log2(2) + (2^0-1)/log2(3) + (2^1-1)/log2(4)
            //       = 1/1 + 0 + 1/2 = 1.5
            // ideal = [1,1,0]
            // IDCG@3 = 1/1 + 1/log2(3) + 0 = 1 + 0.6309 = 1.6309
            // NDCG@3 = 1.5 / 1.6309 = 0.9197
            double dcg = 1.0 / 1.0 + 0.0 + 1.0 / 2.0;
            double idcg = 1.0 / 1.0 + 1.0 / (Math.log(3) / Math.log(2));
            double expectedNdcg = dcg / idcg;
            assertThat(RetrievalMetrics.ndcgAtK(gains, 3)).isCloseTo(expectedNdcg, within(1e-9));
        }

        @Test
        @DisplayName("K 大于列表长度 → 正常计算")
        void kLargerThanList() {
            List<Integer> gains = Arrays.asList(1, 1);
            assertThat(RetrievalMetrics.ndcgAtK(gains, 5)).isCloseTo(1.0, within(1e-9));
        }

        @Test
        @DisplayName("空列表 → NDCG=0.0")
        void emptyList() {
            assertThat(RetrievalMetrics.ndcgAtK(Collections.emptyList(), 3)).isCloseTo(0.0, within(1e-9));
        }
    }

    // ==================== Average Similarity Score ====================

    @Nested
    @DisplayName("Average Similarity Score")
    class SimilarityTests {

        @Test
        @DisplayName("正常计算平均相似度")
        void normalCase() {
            List<Double> scores = Arrays.asList(0.9, 0.8, 0.7);
            assertThat(RetrievalMetrics.averageSimilarityScore(scores)).isCloseTo(0.8, within(1e-9));
        }

        @Test
        @DisplayName("空列表 → 0.0")
        void emptyList() {
            assertThat(RetrievalMetrics.averageSimilarityScore(Collections.emptyList())).isCloseTo(0.0, within(1e-9));
        }

        @Test
        @DisplayName("null → 0.0")
        void nullList() {
            assertThat(RetrievalMetrics.averageSimilarityScore(null)).isCloseTo(0.0, within(1e-9));
        }
    }

    // ==================== Latency Stats ====================

    @Nested
    @DisplayName("Latency Stats")
    class LatencyTests {

        @Test
        @DisplayName("计算延迟分位数")
        void normalCase() {
            // 10 个延迟值（纳秒），1ms 到 10ms
            List<Long> latencies = Arrays.asList(
                    1_000_000L, 2_000_000L, 3_000_000L, 4_000_000L, 5_000_000L,
                    6_000_000L, 7_000_000L, 8_000_000L, 9_000_000L, 10_000_000L
            );
            LatencyStats stats = RetrievalMetrics.computeLatencyStats(latencies);

            assertThat(stats.getMinMs()).isCloseTo(1.0, within(1e-9));
            assertThat(stats.getMaxMs()).isCloseTo(10.0, within(1e-9));
            assertThat(stats.getMeanMs()).isCloseTo(5.5, within(1e-9));
            assertThat(stats.getP50Ms()).isCloseTo(5.0, within(1e-9));
            assertThat(stats.getP95Ms()).isCloseTo(10.0, within(1e-9));
            assertThat(stats.getP99Ms()).isCloseTo(10.0, within(1e-9));
        }

        @Test
        @DisplayName("单个延迟值")
        void singleValue() {
            List<Long> latencies = Collections.singletonList(5_000_000L);
            LatencyStats stats = RetrievalMetrics.computeLatencyStats(latencies);

            assertThat(stats.getP50Ms()).isCloseTo(5.0, within(1e-9));
            assertThat(stats.getP95Ms()).isCloseTo(5.0, within(1e-9));
            assertThat(stats.getP99Ms()).isCloseTo(5.0, within(1e-9));
            assertThat(stats.getMinMs()).isCloseTo(5.0, within(1e-9));
            assertThat(stats.getMaxMs()).isCloseTo(5.0, within(1e-9));
        }

        @Test
        @DisplayName("空列表 → 全部为 0")
        void emptyList() {
            LatencyStats stats = RetrievalMetrics.computeLatencyStats(Collections.emptyList());
            assertThat(stats.getP50Ms()).isCloseTo(0.0, within(1e-9));
            assertThat(stats.getMeanMs()).isCloseTo(0.0, within(1e-9));
        }
    }
}