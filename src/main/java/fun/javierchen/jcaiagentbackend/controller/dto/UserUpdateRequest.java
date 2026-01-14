package fun.javierchen.jcaiagentbackend.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "管理员更新用户请求")
public class UserUpdateRequest {

    @Schema(description = "用户 id")
    private Long id;

    @Schema(description = "账号")
    private String userAccount;

    @Schema(description = "密码")
    private String userPassword;

    @Schema(description = "昵称")
    private String userName;

    @Schema(description = "头像 URL")
    private String userAvatar;

    @Schema(description = "用户简介")
    private String userProfile;

    @Schema(description = "角色：user/admin/ban")
    private String userRole;

    @Schema(description = "微信开放平台 id")
    private String unionId;

    @Schema(description = "公众号 openId")
    private String mpOpenId;
}
