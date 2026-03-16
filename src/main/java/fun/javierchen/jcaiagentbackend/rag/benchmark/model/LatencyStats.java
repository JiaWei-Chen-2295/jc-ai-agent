package fun.javierchen.jcaiagentbackend.rag.benchmark.model;

import lombok.Builder;
import lombok.Data;

/**
 * 延迟统计信息
 *
 * @author JavierChen
 */
@Data
@Builder
public class LatencyStats {

    private double p50Ms;
    private double p95Ms;
    private double p99Ms;
    private double meanMs;
    private double minMs;
    private double maxMs;
}