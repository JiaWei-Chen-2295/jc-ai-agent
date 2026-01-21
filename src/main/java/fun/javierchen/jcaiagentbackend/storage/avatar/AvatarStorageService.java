package fun.javierchen.jcaiagentbackend.storage.avatar;

public interface AvatarStorageService {

    /**
     * 返回头像上传凭证 让客户端上传头像
     * @param userId
     * @param fileName
     * @return
     */
    AvatarUploadToken createAvatarUploadToken(long userId, String fileName);

    /**
     * 判断头像 Key 是否属于某个用户
     * @param avatarKey
     * @param userId
     * @return
     */
    boolean isAvatarKeyOwnedByUser(String avatarKey, long userId);
}

