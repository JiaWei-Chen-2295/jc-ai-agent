package fun.javierchen.jcaiagentbackend.storage.avatar;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

@Component
/**
 * 自定义 Key 生成器
 * <p>
 * Key 格式：avatar/{userId}/{yyyy}/{mm}/{uuid}.{ext}
 * <p>
 * 例如：avatar/12345/2023/04/1234567890.jpg
 * 保证 Key 的唯一性和安全性
 */
public class AvatarObjectKeyFactory {

    private static final Set<String> ALLOWED_EXT = Set.of("jpg", "jpeg", "png", "gif", "webp");

    public String createAvatarKey(long userId, String fileName) {
        String ext = extractExt(fileName);
        if (ext == null) {
            ext = "jpg";
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String yyyy = String.format("%04d", now.getYear());
        String mm = String.format("%02d", now.getMonthValue());
        return "avatar/" + userId + "/" + yyyy + "/" + mm + "/" + UUID.randomUUID() + "." + ext;
    }

    private String extractExt(String fileName) {
        String name = StringUtils.trimToNull(fileName);
        if (name == null) {
            return null;
        }
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return null;
        }
        String ext = name.substring(dot + 1).toLowerCase();
        if (!ALLOWED_EXT.contains(ext)) {
            return null;
        }
        return ext;
    }
}

