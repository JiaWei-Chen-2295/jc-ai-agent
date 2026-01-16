package fun.javierchen.jcaiagentbackend.agent.event;

/**
 * 单条检索结果
 */
public class DocumentSearchResultItem {
    private final String title;
    private final String snippet;
    private final Double score;
    private final String sourceId;

    public DocumentSearchResultItem(String title, String snippet, Double score, String sourceId) {
        this.title = title;
        this.snippet = snippet;
        this.score = score;
        this.sourceId = sourceId;
    }

    public String getTitle() {
        return title;
    }

    public String getSnippet() {
        return snippet;
    }

    public Double getScore() {
        return score;
    }

    public String getSourceId() {
        return sourceId;
    }
}
