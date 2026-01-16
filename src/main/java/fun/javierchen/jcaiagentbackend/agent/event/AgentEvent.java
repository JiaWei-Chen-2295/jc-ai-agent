package fun.javierchen.jcaiagentbackend.agent.event;

/**
 * Agent 语义事件定义
 */
public interface AgentEvent<T> {
    String type();

    AgentEventStage stage();

    T payload();

    AgentEventMeta meta();
}
