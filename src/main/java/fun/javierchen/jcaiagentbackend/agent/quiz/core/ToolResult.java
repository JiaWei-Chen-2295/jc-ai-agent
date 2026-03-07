package fun.javierchen.jcaiagentbackend.agent.quiz.core;

import lombok.Builder;
import lombok.Data;

/**
 * 工具执行结果
 * 表示 Agent 调用工具后的返回结果
 *
 * @author JavierChen
 */
@Data
@Builder
public class ToolResult {

    /**
     * 是否执行成功
     */
    private boolean success;

    /**
     * 结果数据
     */
    private Object data;

    /**
     * 执行的动作名称
     */
    private String actionName;

    /**
     * 是否为终止状态 (任务完成)
     */
    private boolean terminal;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 执行耗时 (毫秒)
     */
    private Long executionTimeMs;

    /**
     * 创建成功结果
     */
    public static ToolResult success(Object data, String actionName) {
        return ToolResult.builder()
                .success(true)
                .data(data)
                .actionName(actionName)
                .terminal(false)
                .build();
    }

    /**
     * 创建成功且终止的结果
     */
    public static ToolResult successAndTerminal(Object data, String actionName) {
        return ToolResult.builder()
                .success(true)
                .data(data)
                .actionName(actionName)
                .terminal(true)
                .build();
    }

    /**
     * 创建失败结果
     */
    public static ToolResult failure(String errorMessage, String actionName) {
        return ToolResult.builder()
                .success(false)
                .errorMessage(errorMessage)
                .actionName(actionName)
                .terminal(false)
                .build();
    }

    /**
     * 创建失败且终止的结果
     */
    public static ToolResult failureAndTerminal(String errorMessage, String actionName) {
        return ToolResult.builder()
                .success(false)
                .errorMessage(errorMessage)
                .actionName(actionName)
                .terminal(true)
                .build();
    }
}
