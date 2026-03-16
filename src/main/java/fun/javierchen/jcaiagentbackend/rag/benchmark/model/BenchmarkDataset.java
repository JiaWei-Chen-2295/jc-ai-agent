package fun.javierchen.jcaiagentbackend.rag.benchmark.model;

import lombok.Data;

import java.util.List;

/**
 * 基准测试数据集
 *
 * @author JavierChen
 */
@Data
public class BenchmarkDataset {

    /**
     * 数据集名称
     */
    private String name;

    /**
     * 数据集描述
     */
    private String description;

    /**
     * 租户 ID（用于多租户隔离查询）
     */
    private Long tenantId;

    /**
     * 测试用例列表
     */
    private List<BenchmarkTestCase> testCases;
}