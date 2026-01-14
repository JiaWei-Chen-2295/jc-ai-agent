package fun.javierchen.jcaiagentbackend.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

@Schema(description = "用户信息")
public record UserVO(
        @Schema(description = "用户 id")
        Long id,

        @Schema(description = "账号")
        String userAccount,

        @Schema(description = "昵称")
        String userName,

        @Schema(description = "头像 URL")
        String userAvatar,

        @Schema(description = "用户简介")
        String userProfile,

        @Schema(description = "角色")
        String userRole,

        @Schema(description = "创建时间")
        OffsetDateTime createTime,

        @Schema(description = "更新时间")
        OffsetDateTime updateTime
) {
}
