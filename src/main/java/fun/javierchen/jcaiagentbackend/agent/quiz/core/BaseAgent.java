package fun.javierchen.jcaiagentbackend.agent.quiz.core;

/**
 * Agent 基础接口
 * 定义 Agent 的核心执行方法
 *
 * @author JavierChen
 */
public interface BaseAgent {

    /**
     * 执行 Agent 任务
     *
     * @param context Agent 上下文
     * @return Agent 响应
     */
    AgentResponse execute(AgentContext context);

    /**
     * 获取 Agent 名称
     *
     * @return Agent 名称
     */
    String getName();

    /**
     * 获取 Agent 描述
     *
     * @return Agent 描述
     */
    String getDescription();
}
