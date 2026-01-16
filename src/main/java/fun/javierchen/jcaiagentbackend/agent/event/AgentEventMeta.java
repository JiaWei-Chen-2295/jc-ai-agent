package fun.javierchen.jcaiagentbackend.agent.event;

/**
 * Agent 事件元信息
 */
public class AgentEventMeta {
    private final String traceId;
    private final long timestampMillis;

    public AgentEventMeta(String traceId, long timestampMillis) {
        this.traceId = traceId;
        this.timestampMillis = timestampMillis;
    }

    public String getTraceId() {
        return traceId;
    }

    public long getTimestampMillis() {
        return timestampMillis;
    }
}
