package fun.javierchen.jcaiagentbackend.app;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "联网搜索来源")
public record StudyFriendSource(
        @Schema(description = "来源标题", example = "Spring AI Reference")
        String title,
        @Schema(description = "来源链接", example = "https://docs.spring.io/spring-ai/reference/")
        String url,
        @Schema(description = "来源摘要", example = "Spring AI reference documentation for MCP client integration.")
        String snippet
) {
}