package fun.javierchen.jcaiagentbackend.agent.event;

/**
 * 已支持的 AgentEvent 类型常量
 */
public final class AgentEventTypes {
    public static final String DOCUMENT_SEARCH_START = "document_search_start";
    public static final String DOCUMENT_SEARCH_RESULT = "document_search_result";
    public static final String THINKING_START = "thinking_start";
    public static final String THINKING_PROGRESS = "thinking_progress";
    public static final String OUTPUT_START = "output_start";
    public static final String OUTPUT_DELTA = "output_delta";
    public static final String OUTPUT_COMPLETE = "output_complete";

    private AgentEventTypes() {
    }
}
