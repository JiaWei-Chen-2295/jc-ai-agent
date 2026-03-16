package fun.javierchen.jcaiagentbackend.rag.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import fun.javierchen.jcaiagentbackend.common.TenantContextHolder;
import fun.javierchen.jcaiagentbackend.rag.benchmark.metrics.RetrievalMetrics;
import fun.javierchen.jcaiagentbackend.rag.benchmark.model.*;
import fun.javierchen.jcaiagentbackend.rag.config.VectorStoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * RAG 检索基准测试服务
 * 编排测试数据集加载、查询执行、延迟测量和指标聚合
 *
 * @author JavierChen
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RagBenchmarkService {

    private final VectorStoreService vectorStoreService;
    private final ObjectMapper objectMapper;

    private static final int CONTENT_SNIPPET_LENGTH = 200;

    /**
     * 从 JSON 字符串加载数据集并运行基准测试
     *
     * @param json       数据集 JSON 字符串
     * @param defaultTopK 默认 topK 值
     * @return 基准测试报告
     */
    public BenchmarkReport runFromJson(String json, int defaultTopK) {
        try {
            BenchmarkDataset dataset = objectMapper.readValue(json, BenchmarkDataset.class);
            return execute(dataset, defaultTopK);
        } catch (Exception e) {
            throw new RuntimeException("解析基准测试数据集失败: " + e.getMessage(), e);
        }
    }

    /**
     * 执行基准测试
     *
     * @param dataset    数据集
     * @param defaultTopK 默认 topK 值
     * @return 基准测试报告
     */
    public BenchmarkReport execute(BenchmarkDataset dataset, int defaultTopK) {
        if (dataset.getTestCases() == null || dataset.getTestCases().isEmpty()) {
            throw new IllegalArgumentException("数据集测试用例为空");
        }

        log.info("开始 RAG 基准测试: dataset={}, testCases={}, defaultTopK={}",
                dataset.getName(), dataset.getTestCases().size(), defaultTopK);

        List<BenchmarkQueryResult> results = new ArrayList<>();

        long benchStartNanos = System.nanoTime();
        for (BenchmarkTestCase testCase : dataset.getTestCases()) {
            BenchmarkQueryResult result = executeTestCase(testCase, defaultTopK, dataset.getTenantId());
            results.add(result);
        }
        long totalDurationMs = (System.nanoTime() - benchStartNanos) / 1_000_000;

        BenchmarkReport report = aggregateResults(dataset, results, totalDurationMs);
        log.info("RAG 基准测试完成: dataset={}, totalQueries={}, totalDurationMs={}, MRR={}, HitRate@3={}",
                dataset.getName(), results.size(), totalDurationMs,
                String.format("%.4f", report.getMrr()),
                String.format("%.4f", report.getHitRateAt3()));

        return report;
    }

    /**
     * 执行单条测试用例
     */
    private BenchmarkQueryResult executeTestCase(BenchmarkTestCase testCase, int defaultTopK, Long tenantId) {
        int effectiveTopK = testCase.getTopK() != null ? testCase.getTopK() : defaultTopK;

        try {
            if (tenantId != null) {
                TenantContextHolder.setTenantId(tenantId);
            }

            // 测量检索延迟
            long startNanos = System.nanoTime();
            List<Document> docs = vectorStoreService.similaritySearch(testCase.getQuery(), effectiveTopK);
            long latencyNanos = System.nanoTime() - startNanos;

            // 构建相关性标记和分级相关度列表
            List<Boolean> relevanceFlags = new ArrayList<>();
            List<Integer> gains = new ArrayList<>();
            List<Double> scores = new ArrayList<>();
            List<BenchmarkQueryResult.RetrievedDocInfo> docInfos = new ArrayList<>();

            for (Document doc : docs) {
                boolean relevant = isRelevant(doc, testCase);
                int grade = getRelevanceGrade(doc, testCase);
                double score = doc.getScore() != null ? doc.getScore() : 0.0;

                relevanceFlags.add(relevant);
                gains.add(grade);
                scores.add(score);

                docInfos.add(BenchmarkQueryResult.RetrievedDocInfo.builder()
                        .chunkId(doc.getId())
                        .score(score)
                        .relevant(relevant)
                        .contentSnippet(truncate(doc.getText(), CONTENT_SNIPPET_LENGTH))
                        .build());
            }

            int totalRelevant = resolveTotalRelevant(testCase);

            // 计算各指标
            double p1 = RetrievalMetrics.precisionAtK(relevanceFlags, 1);
            double p3 = RetrievalMetrics.precisionAtK(relevanceFlags, 3);
            double p5 = RetrievalMetrics.precisionAtK(relevanceFlags, 5);

            double r1 = RetrievalMetrics.recallAtK(relevanceFlags, 1, totalRelevant);
            double r3 = RetrievalMetrics.recallAtK(relevanceFlags, 3, totalRelevant);
            double r5 = RetrievalMetrics.recallAtK(relevanceFlags, 5, totalRelevant);

            return BenchmarkQueryResult.builder()
                    .testCaseId(testCase.getId())
                    .query(testCase.getQuery())
                    .topK(effectiveTopK)
                    .retrievedDocs(docInfos)
                    .latencyNanos(latencyNanos)
                    .latencyMs(latencyNanos / 1_000_000.0)
                    // Hit Rate
                    .hitAt1(RetrievalMetrics.hitAtK(relevanceFlags, 1))
                    .hitAt3(RetrievalMetrics.hitAtK(relevanceFlags, 3))
                    .hitAt5(RetrievalMetrics.hitAtK(relevanceFlags, 5))
                    // Precision
                    .precisionAt1(p1)
                    .precisionAt3(p3)
                    .precisionAt5(p5)
                    // Recall
                    .recallAt1(r1)
                    .recallAt3(r3)
                    .recallAt5(r5)
                    // F1
                    .f1At1(RetrievalMetrics.f1AtK(p1, r1))
                    .f1At3(RetrievalMetrics.f1AtK(p3, r3))
                    .f1At5(RetrievalMetrics.f1AtK(p5, r5))
                    // MRR
                    .reciprocalRank(RetrievalMetrics.reciprocalRank(relevanceFlags))
                    // MAP
                    .averagePrecisionAt1(RetrievalMetrics.averagePrecisionAtK(relevanceFlags, 1, totalRelevant))
                    .averagePrecisionAt3(RetrievalMetrics.averagePrecisionAtK(relevanceFlags, 3, totalRelevant))
                    .averagePrecisionAt5(RetrievalMetrics.averagePrecisionAtK(relevanceFlags, 5, totalRelevant))
                    // NDCG
                    .ndcgAt1(RetrievalMetrics.ndcgAtK(gains, 1))
                    .ndcgAt3(RetrievalMetrics.ndcgAtK(gains, 3))
                    .ndcgAt5(RetrievalMetrics.ndcgAtK(gains, 5))
                    // 相似度
                    .averageSimilarityScore(RetrievalMetrics.averageSimilarityScore(scores))
                    .build();
        } finally {
            TenantContextHolder.clear();
        }
    }

    /**
     * 判断检索到的文档是否相关
     */
    private boolean isRelevant(Document doc, BenchmarkTestCase testCase) {
        if (testCase.getMatchMode() == MatchMode.CHUNK_ID) {
            return testCase.getExpectedChunkIds() != null
                    && testCase.getExpectedChunkIds().contains(doc.getId());
        } else {
            // KEYWORD 模式
            if (testCase.getExpectedKeywords() == null || testCase.getExpectedKeywords().isEmpty()) {
                return false;
            }
            String content = doc.getText();
            if (content == null || content.isEmpty()) {
                return false;
            }
            String contentLower = content.toLowerCase();
            return testCase.getExpectedKeywords().stream()
                    .anyMatch(kw -> contentLower.contains(kw.toLowerCase()));
        }
    }

    /**
     * 获取文档的分级相关度
     */
    private int getRelevanceGrade(Document doc, BenchmarkTestCase testCase) {
        if (testCase.getRelevanceLabels() != null && testCase.getRelevanceLabels().containsKey(doc.getId())) {
            return testCase.getRelevanceLabels().get(doc.getId());
        }
        return isRelevant(doc, testCase) ? 1 : 0;
    }

    /**
     * 解析总相关文档数
     */
    private int resolveTotalRelevant(BenchmarkTestCase testCase) {
        if (testCase.getTotalRelevantCount() != null && testCase.getTotalRelevantCount() > 0) {
            return testCase.getTotalRelevantCount();
        }
        if (testCase.getMatchMode() == MatchMode.CHUNK_ID && testCase.getExpectedChunkIds() != null) {
            return testCase.getExpectedChunkIds().size();
        }
        if (testCase.getMatchMode() == MatchMode.KEYWORD && testCase.getExpectedKeywords() != null) {
            return testCase.getExpectedKeywords().size();
        }
        return 1;
    }

    /**
     * 聚合所有查询结果为最终报告
     */
    private BenchmarkReport aggregateResults(BenchmarkDataset dataset,
                                              List<BenchmarkQueryResult> results,
                                              long totalDurationMs) {
        int n = results.size();

        // 聚合各指标（取所有查询的平均值）
        double hitRateAt1 = results.stream().mapToDouble(r -> r.isHitAt1() ? 1.0 : 0.0).average().orElse(0);
        double hitRateAt3 = results.stream().mapToDouble(r -> r.isHitAt3() ? 1.0 : 0.0).average().orElse(0);
        double hitRateAt5 = results.stream().mapToDouble(r -> r.isHitAt5() ? 1.0 : 0.0).average().orElse(0);

        double avgP1 = results.stream().mapToDouble(BenchmarkQueryResult::getPrecisionAt1).average().orElse(0);
        double avgP3 = results.stream().mapToDouble(BenchmarkQueryResult::getPrecisionAt3).average().orElse(0);
        double avgP5 = results.stream().mapToDouble(BenchmarkQueryResult::getPrecisionAt5).average().orElse(0);

        double avgR1 = results.stream().mapToDouble(BenchmarkQueryResult::getRecallAt1).average().orElse(0);
        double avgR3 = results.stream().mapToDouble(BenchmarkQueryResult::getRecallAt3).average().orElse(0);
        double avgR5 = results.stream().mapToDouble(BenchmarkQueryResult::getRecallAt5).average().orElse(0);

        double mrr = results.stream().mapToDouble(BenchmarkQueryResult::getReciprocalRank).average().orElse(0);

        double mapAt1 = results.stream().mapToDouble(BenchmarkQueryResult::getAveragePrecisionAt1).average().orElse(0);
        double mapAt3 = results.stream().mapToDouble(BenchmarkQueryResult::getAveragePrecisionAt3).average().orElse(0);
        double mapAt5 = results.stream().mapToDouble(BenchmarkQueryResult::getAveragePrecisionAt5).average().orElse(0);

        double avgNdcg1 = results.stream().mapToDouble(BenchmarkQueryResult::getNdcgAt1).average().orElse(0);
        double avgNdcg3 = results.stream().mapToDouble(BenchmarkQueryResult::getNdcgAt3).average().orElse(0);
        double avgNdcg5 = results.stream().mapToDouble(BenchmarkQueryResult::getNdcgAt5).average().orElse(0);

        double avgSimilarity = results.stream().mapToDouble(BenchmarkQueryResult::getAverageSimilarityScore).average().orElse(0);

        // 延迟统计
        List<Long> latencies = results.stream().map(BenchmarkQueryResult::getLatencyNanos).toList();
        LatencyStats latencyStats = RetrievalMetrics.computeLatencyStats(latencies);

        // QPS
        double qps = totalDurationMs > 0 ? (double) n / (totalDurationMs / 1000.0) : 0;

        return BenchmarkReport.builder()
                .datasetName(dataset.getName())
                .datasetDescription(dataset.getDescription())
                .totalQueries(n)
                .executedAt(LocalDateTime.now())
                .totalDurationMs(totalDurationMs)
                // Hit Rate
                .hitRateAt1(hitRateAt1)
                .hitRateAt3(hitRateAt3)
                .hitRateAt5(hitRateAt5)
                // Precision
                .precisionAt1(avgP1)
                .precisionAt3(avgP3)
                .precisionAt5(avgP5)
                // Recall
                .recallAt1(avgR1)
                .recallAt3(avgR3)
                .recallAt5(avgR5)
                // F1
                .f1At1(RetrievalMetrics.f1AtK(avgP1, avgR1))
                .f1At3(RetrievalMetrics.f1AtK(avgP3, avgR3))
                .f1At5(RetrievalMetrics.f1AtK(avgP5, avgR5))
                // MRR
                .mrr(mrr)
                // MAP
                .mapAt1(mapAt1)
                .mapAt3(mapAt3)
                .mapAt5(mapAt5)
                // NDCG
                .ndcgAt1(avgNdcg1)
                .ndcgAt3(avgNdcg3)
                .ndcgAt5(avgNdcg5)
                // 相似度
                .averageSimilarityScore(avgSimilarity)
                // 性能
                .latency(latencyStats)
                .queriesPerSecond(qps)
                // 明细
                .queryResults(results)
                .build();
    }

    private String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }
}