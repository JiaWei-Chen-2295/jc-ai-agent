package fun.javierchen.jcaiagentbackend.model.entity.enums;

/**
 * Agent 执行阶段枚举
 * 对应 ReAct 循环的三个阶段
 *
 * @author JavierChen
 */
public enum AgentPhase {
    /**
     * 思考阶段 - LLM 分析当前状态，制定计划
     */
    THOUGHT("thought", "思考"),

    /**
     * 行动阶段 - 调用外部工具
     */
    ACTION("action", "行动"),

    /**
     * 观察阶段 - 接收工具返回结果
     */
    OBSERVATION("observation", "观察");

    private final String code;
    private final String description;

    AgentPhase(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 根据编码获取阶段
     *
     * @param code 编码
     * @return AgentPhase 或 null
     */
    public static AgentPhase fromCode(String code) {
        for (AgentPhase phase : values()) {
            if (phase.code.equalsIgnoreCase(code)) {
                return phase;
            }
        }
        return null;
    }
}
