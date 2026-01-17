package fun.javierchen.jcaiagentbackend.controller;

import fun.javierchen.jcaiagentbackend.common.BaseResponse;
import fun.javierchen.jcaiagentbackend.common.ResultUtils;
import fun.javierchen.jcaiagentbackend.controller.dto.AvatarUploadTokenRequest;
import fun.javierchen.jcaiagentbackend.controller.dto.AvatarUploadTokenResponse;
import fun.javierchen.jcaiagentbackend.controller.dto.UserAvatarUpdateRequest;
import fun.javierchen.jcaiagentbackend.controller.dto.UserVO;
import fun.javierchen.jcaiagentbackend.service.UserAvatarService;
import fun.javierchen.jcaiagentbackend.storage.avatar.AvatarUploadToken;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user/avatar")
@RequiredArgsConstructor
@Tag(name = "用户头像", description = "头像上传凭证 + 头像更新（前端直传）")
public class UserAvatarController {

    private final UserAvatarService userAvatarService;

    @PostMapping("/upload-token")
    @Operation(summary = "获取头像上传凭证（前端直传）")
    public BaseResponse<AvatarUploadTokenResponse> getUploadToken(
            @RequestBody AvatarUploadTokenRequest request,
            HttpServletRequest httpRequest
    ) {
        AvatarUploadToken token = userAvatarService.getAvatarUploadToken(request == null ? null : request.fileName(), httpRequest);
        return ResultUtils.success(new AvatarUploadTokenResponse(
                token.provider(),
                token.uploadUrl(),
                token.objectKey(),
                token.expireAtEpochSeconds(),
                token.formFields(),
                token.fileUrl()
        ));
    }

    @PostMapping
    @Operation(summary = "更新当前用户头像")
    public BaseResponse<UserVO> updateMyAvatar(@RequestBody UserAvatarUpdateRequest request, HttpServletRequest httpRequest) {
        UserVO userVO = userAvatarService.updateMyAvatar(request == null ? null : request.avatarKey(), httpRequest);
        return ResultUtils.success(userVO);
    }
}

