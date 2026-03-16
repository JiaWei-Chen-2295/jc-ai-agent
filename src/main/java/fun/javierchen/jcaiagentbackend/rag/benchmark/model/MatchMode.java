package fun.javierchen.jcaiagentbackend.rag.benchmark.model;

/**
 * 基准测试相关性匹配模式
 *
 * @author JavierChen
 */
public enum MatchMode {

    /**
     * 按向量库 chunk ID 匹配
     */
    CHUNK_ID,

    /**
     * 按关键词匹配（检索内容包含关键词即视为相关）
     */
    KEYWORD
}