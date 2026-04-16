package fun.javierchen.jcaiagentbackend.controller.dto;

import fun.javierchen.jcaiagentbackend.app.StudyFriendSource;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "Chat message view")
public class ChatMessageVO {

    @Schema(description = "Message id")
    private Long id;

    @Schema(description = "Role (user/assistant)")
    private String role;

    @Schema(description = "Message content")
    private String content;

    @Schema(description = "Created time")
    private LocalDateTime createdAt;

    @Schema(description = "Whether web search was actually used for this assistant message")
    private Boolean webSearchUsed;

    @Schema(description = "Optional web search sources persisted with the assistant message")
    private List<StudyFriendSource> sources;
}
