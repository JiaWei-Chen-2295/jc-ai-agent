package fun.javierchen.jcaiagentbackend.websearch;

import fun.javierchen.jcaiagentbackend.app.StudyFriendSource;

import java.util.List;

public record FirecrawlSearchResult(
        String context,
        List<StudyFriendSource> sources
) {

    public static FirecrawlSearchResult empty() {
        return new FirecrawlSearchResult("", List.of());
    }

    public boolean hasSources() {
        return sources != null && !sources.isEmpty();
    }
}