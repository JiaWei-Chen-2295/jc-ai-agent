package fun.javierchen.jcaiagentbackend.rag.benchmark.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * RAG 基准测试完整报告
 *
 * @author JavierChen
 */
@Data
@Builder
public class BenchmarkReport {

    private String datasetName;
    private String datasetDescription;
    private int totalQueries;
    private LocalDateTime executedAt;
    private long totalDurationMs;

    // --- 聚合检索指标 ---

    private double hitRateAt1;
    private double hitRateAt3;
    private double hitRateAt5;

    private double precisionAt1;
    private double precisionAt3;
    private double precisionAt5;

    private double recallAt1;
    private double recallAt3;
    private double recallAt5;

    private double f1At1;
    private double f1At3;
    private double f1At5;

    private double mrr;

    private double mapAt1;
    private double mapAt3;
    private double mapAt5;

    private double ndcgAt1;
    private double ndcgAt3;
    private double ndcgAt5;

    private double averageSimilarityScore;

    // --- 性能指标 ---

    private LatencyStats latency;
    private double queriesPerSecond;

    // --- 每条查询详情 ---

    private List<BenchmarkQueryResult> queryResults;
}