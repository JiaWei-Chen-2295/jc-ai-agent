package fun.javierchen.jcaiagentbackend.storage.avatar;

import fun.javierchen.jcaiagentbackend.common.ErrorCode;
import fun.javierchen.jcaiagentbackend.exception.BusinessException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnMissingBean(AvatarStorageService.class)
public class NoopAvatarStorageService implements AvatarStorageService {
    @Override
    public AvatarUploadToken createAvatarUploadToken(long userId, String fileName) {
        throw new BusinessException(ErrorCode.OPERATION_ERROR, "对象存储未启用，请配置 storage.type");
    }

    @Override
    public boolean isAvatarKeyOwnedByUser(String avatarKey, long userId) {
        return false;
    }
}

