package fun.javierchen.jcaiagentbackend.agent.event;

import java.util.List;

/**
 * 文档检索结果载荷
 */
public class DocumentSearchResultPayload {
    private final List<DocumentSearchResultItem> results;

    public DocumentSearchResultPayload(List<DocumentSearchResultItem> results) {
        this.results = results;
    }

    public List<DocumentSearchResultItem> getResults() {
        return results;
    }
}
