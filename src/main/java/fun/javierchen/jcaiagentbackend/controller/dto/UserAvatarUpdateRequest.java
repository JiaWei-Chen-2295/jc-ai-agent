package fun.javierchen.jcaiagentbackend.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "更新用户头像请求")
public record UserAvatarUpdateRequest(
        @Schema(description = "头像 Key（推荐）或本系统域名下的 URL", example = "avatar/1024/2026/01/uuid.png")
        String avatarKey
) {
}

