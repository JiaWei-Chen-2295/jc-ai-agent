package fun.javierchen.jcaiagentbackend.storage.avatar;

public interface AvatarStorageService {

    AvatarUploadToken createAvatarUploadToken(long userId, String fileName);

    boolean isAvatarKeyOwnedByUser(String avatarKey, long userId);
}

