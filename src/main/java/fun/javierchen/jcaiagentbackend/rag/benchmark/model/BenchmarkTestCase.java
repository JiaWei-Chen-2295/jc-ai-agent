package fun.javierchen.jcaiagentbackend.rag.benchmark.model;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 单条基准测试用例
 *
 * @author JavierChen
 */
@Data
public class BenchmarkTestCase {

    /**
     * 测试用例 ID
     */
    private String id;

    /**
     * 查询文本
     */
    private String query;

    /**
     * 本条用例的 topK 值（可选，缺省使用全局默认值）
     */
    private Integer topK;

    /**
     * 相关性匹配模式
     */
    private MatchMode matchMode;

    /**
     * 期望命中的向量 chunk ID 列表（matchMode=CHUNK_ID 时使用）
     */
    private List<String> expectedChunkIds;

    /**
     * 期望命中的关键词列表（matchMode=KEYWORD 时使用）
     */
    private List<String> expectedKeywords;

    /**
     * 分级相关度标签（可选，chunkId -> 相关度等级）
     * 用于 NDCG 计算，缺省时使用二元相关度（1/0）
     */
    private Map<String, Integer> relevanceLabels;

    /**
     * 语料库中该 query 的总相关文档数（可选，用于 Recall 计算）
     * 缺省时使用 expectedChunkIds 或 expectedKeywords 的数量
     */
    private Integer totalRelevantCount;
}