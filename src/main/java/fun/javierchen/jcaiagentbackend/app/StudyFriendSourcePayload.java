package fun.javierchen.jcaiagentbackend.app;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "SSE 来源事件载荷")
public record StudyFriendSourcePayload(
        @Schema(description = "本次是否实际使用了联网搜索")
        boolean webSearchUsed,
        @Schema(description = "联网搜索来源列表")
        List<StudyFriendSource> sources
) {
}