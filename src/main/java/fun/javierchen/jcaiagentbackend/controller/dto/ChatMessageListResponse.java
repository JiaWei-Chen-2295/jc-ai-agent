package fun.javierchen.jcaiagentbackend.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "Cursor page for chat messages")
public class ChatMessageListResponse {

    @Schema(description = "Message list")
    private List<ChatMessageVO> records;

    @Schema(description = "Whether more data exists")
    private Boolean hasMore;

    @Schema(description = "Cursor id for next page")
    private Long nextBeforeId;
}
