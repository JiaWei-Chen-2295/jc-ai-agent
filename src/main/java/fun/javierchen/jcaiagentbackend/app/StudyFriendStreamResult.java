package fun.javierchen.jcaiagentbackend.app;

import reactor.core.publisher.Flux;

import java.util.List;

public record StudyFriendStreamResult(
        Flux<String> contentStream,
        boolean webSearchUsed,
        List<StudyFriendSource> sources
) {
}