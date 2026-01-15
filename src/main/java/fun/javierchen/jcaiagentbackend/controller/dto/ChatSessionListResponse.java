package fun.javierchen.jcaiagentbackend.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "Cursor page for chat sessions")
public class ChatSessionListResponse {

    @Schema(description = "Session list")
    private List<ChatSessionVO> records;

    @Schema(description = "Whether more data exists")
    private Boolean hasMore;

    @Schema(description = "Cursor chat id for next page")
    private String nextChatId;

    @Schema(description = "Cursor last message time for next page")
    private LocalDateTime nextLastMessageAt;
}
