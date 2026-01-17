package fun.javierchen.jcaiagentbackend.storage.avatar;

import java.util.Map;

public record AvatarUploadToken(
        String provider,
        String uploadUrl,
        String objectKey,
        long expireAtEpochSeconds,
        Map<String, String> formFields,
        String fileUrl
) {
}

