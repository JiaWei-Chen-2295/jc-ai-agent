package fun.javierchen.jcaiagentbackend.agent.event;

/**
 * 文档检索开始的语义载荷
 */
public class DocumentSearchStartPayload {
    private final String query;
    private final String source;

    public DocumentSearchStartPayload(String query, String source) {
        this.query = query;
        this.source = source;
    }

    public String getQuery() {
        return query;
    }

    public String getSource() {
        return source;
    }
}
