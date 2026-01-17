package fun.javierchen.jcaiagentbackend.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

@Schema(description = "头像上传凭证（前端直传）")
public record AvatarUploadTokenResponse(
        @Schema(description = "存储提供方", example = "oss")
        String provider,

        @Schema(description = "上传地址（通常为 Bucket Host）", example = "https://bucket.oss-cn-xxx.aliyuncs.com")
        String uploadUrl,

        @Schema(description = "对象 Key（建议保存到数据库）", example = "avatar/1024/2026/01/uuid.png")
        String objectKey,

        @Schema(description = "过期时间（秒级时间戳）", example = "1700000000")
        long expireAtEpochSeconds,

        @Schema(description = "表单上传字段（前端直传时原样提交）")
        Map<String, String> formFields,

        @Schema(description = "文件可访问 URL（用于上传成功后预览）")
        String fileUrl
) {
}

