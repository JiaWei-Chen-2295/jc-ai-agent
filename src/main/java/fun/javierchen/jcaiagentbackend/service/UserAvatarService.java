package fun.javierchen.jcaiagentbackend.service;

import fun.javierchen.jcaiagentbackend.common.ErrorCode;
import fun.javierchen.jcaiagentbackend.controller.dto.UserVO;
import fun.javierchen.jcaiagentbackend.exception.BusinessException;
import fun.javierchen.jcaiagentbackend.exception.ThrowUtils;
import fun.javierchen.jcaiagentbackend.model.entity.User;
import fun.javierchen.jcaiagentbackend.repository.UserRepository;
import fun.javierchen.jcaiagentbackend.storage.StorageUrlResolver;
import fun.javierchen.jcaiagentbackend.storage.avatar.AvatarStorageService;
import fun.javierchen.jcaiagentbackend.storage.avatar.AvatarUploadToken;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserAvatarService {

    private static final int DEFAULT_IS_DELETE = 0;

    private final UserService userService;
    private final UserRepository userRepository;
    private final AvatarStorageService avatarStorageService;
    private final StorageUrlResolver storageUrlResolver;

    public AvatarUploadToken getAvatarUploadToken(String fileName, HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        String safeFileName = StringUtils.trimToNull(fileName);
        ThrowUtils.throwIf(safeFileName == null, ErrorCode.PARAMS_ERROR, "fileName is required");
        ThrowUtils.throwIf(safeFileName.length() > 256, ErrorCode.PARAMS_ERROR, "fileName is too long");
        return avatarStorageService.createAvatarUploadToken(loginUser.getId(), safeFileName);
    }

    @Transactional
    public UserVO updateMyAvatar(String avatarKeyOrUrl, HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        String avatarKey = storageUrlResolver.toKeyIfMatchesDomain(avatarKeyOrUrl);
        ThrowUtils.throwIf(StringUtils.isBlank(avatarKey), ErrorCode.PARAMS_ERROR, "avatarKey is required");
        ThrowUtils.throwIf(avatarKey.length() > 1024, ErrorCode.PARAMS_ERROR, "avatarKey is too long");
        avatarKey = StringUtils.removeStart(avatarKey, "/");
        ThrowUtils.throwIf(!avatarStorageService.isAvatarKeyOwnedByUser(avatarKey, loginUser.getId()),
                ErrorCode.NO_AUTH_ERROR, "avatarKey 不属于当前用户");

        User user = userRepository.findByIdAndIsDelete(loginUser.getId(), DEFAULT_IS_DELETE)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "User not found"));
        user.setUserAvatar(avatarKey);
        userRepository.save(user);
        return userService.getUserVO(user);
    }
}
