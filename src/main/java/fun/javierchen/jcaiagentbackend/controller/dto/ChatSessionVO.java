package fun.javierchen.jcaiagentbackend.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "Chat session view")
public class ChatSessionVO {

    @Schema(description = "Chat session id")
    private String chatId;

    @Schema(description = "Session title")
    private String title;

    @Schema(description = "Last message timestamp")
    private LocalDateTime lastMessageAt;

    @Schema(description = "Created time")
    private LocalDateTime createdAt;

    @Schema(description = "AI model ID used in this session")
    private String modelId;

    @Schema(description = "AI model display name")
    private String modelDisplayName;
}
