package fun.javierchen.jcaiagentbackend.rag.benchmark.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 单条查询的基准测试执行结果
 *
 * @author JavierChen
 */
@Data
@Builder
public class BenchmarkQueryResult {

    private String testCaseId;
    private String query;
    private int topK;
    private List<RetrievedDocInfo> retrievedDocs;
    private long latencyNanos;
    private double latencyMs;

    // --- per-query 检索指标 ---

    private boolean hitAt1;
    private boolean hitAt3;
    private boolean hitAt5;

    private double precisionAt1;
    private double precisionAt3;
    private double precisionAt5;

    private double recallAt1;
    private double recallAt3;
    private double recallAt5;

    private double f1At1;
    private double f1At3;
    private double f1At5;

    private double reciprocalRank;

    private double averagePrecisionAt1;
    private double averagePrecisionAt3;
    private double averagePrecisionAt5;

    private double ndcgAt1;
    private double ndcgAt3;
    private double ndcgAt5;

    private double averageSimilarityScore;

    /**
     * 检索到的文档信息
     */
    @Data
    @Builder
    public static class RetrievedDocInfo {
        private String chunkId;
        private double score;
        private boolean relevant;
        private String contentSnippet;
    }
}