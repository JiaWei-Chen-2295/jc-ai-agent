package fun.javierchen.jcaiagentbackend.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "更新个人信息请求")
public class UserUpdateMyRequest {

    @Schema(description = "昵称")
    private String userName;

    @Schema(description = "头像 URL")
    private String userAvatar;

    @Schema(description = "用户简介")
    private String userProfile;
}
