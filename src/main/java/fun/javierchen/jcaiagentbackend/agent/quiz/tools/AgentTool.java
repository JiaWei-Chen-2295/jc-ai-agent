package fun.javierchen.jcaiagentbackend.agent.quiz.tools;

import fun.javierchen.jcaiagentbackend.agent.quiz.core.ToolResult;

import java.util.Map;

/**
 * Agent 工具接口
 * 定义 Agent 可调用的工具标准
 *
 * @author JavierChen
 */
public interface AgentTool {

    /**
     * 获取工具名称
     */
    String getName();

    /**
     * 获取工具描述
     */
    String getDescription();

    /**
     * 执行工具
     *
     * @param params 工具参数
     * @return 执行结果
     */
    ToolResult execute(Map<String, Object> params);

    /**
     * 获取参数描述
     */
    default Map<String, String> getParameterDescriptions() {
        return Map.of();
    }
}
