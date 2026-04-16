package fun.javierchen.jcaiagentbackend.app;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "学习伙伴聊天结果")
public record StudyFriendChatResult(
        @Schema(description = "模型回答正文")
        String content,
        @Schema(description = "本次是否实际使用了联网搜索")
        boolean webSearchUsed,
        @Schema(description = "联网搜索来源列表")
        List<StudyFriendSource> sources
) {
}