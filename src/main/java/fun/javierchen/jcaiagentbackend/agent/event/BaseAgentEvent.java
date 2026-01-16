package fun.javierchen.jcaiagentbackend.agent.event;

/**
 * AgentEvent 的默认实现
 */
public class BaseAgentEvent<T> implements AgentEvent<T> {
    private final String type;
    private final AgentEventStage stage;
    private final T payload;
    private final AgentEventMeta meta;

    public BaseAgentEvent(String type, AgentEventStage stage, T payload, AgentEventMeta meta) {
        this.type = type;
        this.stage = stage;
        this.payload = payload;
        this.meta = meta;
    }

    public static <T> BaseAgentEvent<T> of(String type, AgentEventStage stage, T payload, AgentEventMeta meta) {
        return new BaseAgentEvent<T>(type, stage, payload, meta);
    }

    @Override
    public String type() {
        return type;
    }

    @Override
    public AgentEventStage stage() {
        return stage;
    }

    @Override
    public T payload() {
        return payload;
    }

    @Override
    public AgentEventMeta meta() {
        return meta;
    }
}
