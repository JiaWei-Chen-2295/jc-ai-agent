package fun.javierchen.jcaiagentbackend.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

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
}
