package fun.javierchen.jcaiagentbackend.agent.quiz.core;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * Agent 决策
 * 表示 Agent 在 Thought 阶段做出的决策
 *
 * @author JavierChen
 */
@Data
@Builder
public class AgentDecision {

    /**
     * 决策类型
     */
    private DecisionType type;

    /**
     * 要调用的工具名称
     */
    private String toolName;

    /**
     * 工具调用参数
     */
    private Map<String, Object> toolParams;

    /**
     * 决策理由 (供日志和调试使用)
     */
    private String reasoning;

    /**
     * 置信度 (0-1)
     */
    private Double confidence;

    /**
     * 决策类型枚举
     */
    public enum DecisionType {
        /**
         * 调用工具
         */
        CALL_TOOL,

        /**
         * 完成任务
         */
        FINISH,

        /**
         * 需要更多信息
         */
        NEED_MORE_INFO,

        /**
         * 降级处理
         */
        FALLBACK
    }

    /**
     * 创建工具调用决策
     */
    public static AgentDecision callTool(String toolName, Map<String, Object> params, String reasoning) {
        return AgentDecision.builder()
                .type(DecisionType.CALL_TOOL)
                .toolName(toolName)
                .toolParams(params)
                .reasoning(reasoning)
                .build();
    }

    /**
     * 创建完成决策
     */
    public static AgentDecision finish(String reasoning) {
        return AgentDecision.builder()
                .type(DecisionType.FINISH)
                .reasoning(reasoning)
                .build();
    }

    /**
     * 创建降级决策
     */
    public static AgentDecision fallback(String reasoning) {
        return AgentDecision.builder()
                .type(DecisionType.FALLBACK)
                .reasoning(reasoning)
                .build();
    }
}
