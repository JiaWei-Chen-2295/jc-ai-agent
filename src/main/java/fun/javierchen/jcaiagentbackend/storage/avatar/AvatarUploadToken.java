package fun.javierchen.jcaiagentbackend.storage.avatar;

import java.util.Map;

public record AvatarUploadToken(
        // 存储提供方标识（如 oss）
        String provider,
        // 上传地址（通常为 bucket host）
        String uploadUrl,
        // 对象 Key（用于保存到数据库）
        String objectKey,
        // 过期时间（秒级时间戳）
        long expireAtEpochSeconds,
        // 表单上传字段（前端直传需原样提交）
        Map<String, String> formFields,
        // 可访问的文件 URL（用于上传成功后预览）
        String fileUrl
) {
}
