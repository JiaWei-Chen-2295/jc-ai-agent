package fun.javierchen.jcaiagentbackend.chat.model.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "Chat session entity")
public class ChatSession {

    @Schema(description = "Chat session id")
    private String chatId;

    @Schema(description = "Tenant id")
    private Long tenantId;

    @Schema(description = "User id")
    private Long userId;

    @Schema(description = "App code")
    private String appCode;

    @Schema(description = "Session title")
    private String title;

    @Schema(description = "Last message timestamp")
    private LocalDateTime lastMessageAt;

    @Schema(description = "Created time")
    private LocalDateTime createdAt;

    @Schema(description = "Updated time")
    private LocalDateTime updatedAt;

    @Schema(description = "Soft delete flag")
    private Integer isDeleted;
}
