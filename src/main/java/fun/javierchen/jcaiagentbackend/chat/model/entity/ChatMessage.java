package fun.javierchen.jcaiagentbackend.chat.model.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "Chat message entity")
public class ChatMessage {

    @Schema(description = "Message id")
    private Long id;

    @Schema(description = "Chat session id")
    private String chatId;

    @Schema(description = "Tenant id")
    private Long tenantId;

    @Schema(description = "User id")
    private Long userId;

    @Schema(description = "Role (user/assistant)")
    private String role;

    @Schema(description = "Client message id for idempotency")
    private String clientMessageId;

    @Schema(description = "Message content")
    private String content;

    @Schema(description = "Optional metadata as JSON string")
    private String metadata;

    @Schema(description = "Created time")
    private LocalDateTime createdAt;
}
