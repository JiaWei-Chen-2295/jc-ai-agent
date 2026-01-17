package fun.javierchen.jcaiagentbackend.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "获取头像上传凭证请求（前端直传）")
public record AvatarUploadTokenRequest(
        @Schema(description = "文件名（用于推断扩展名）", example = "avatar.png")
        String fileName
) {
}

