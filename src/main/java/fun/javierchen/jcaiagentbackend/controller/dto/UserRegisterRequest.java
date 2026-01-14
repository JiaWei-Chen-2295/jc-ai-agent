package fun.javierchen.jcaiagentbackend.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "用户注册请求")
public class UserRegisterRequest {

    @Schema(description = "账号")
    private String userAccount;

    @Schema(description = "密码")
    private String userPassword;

    @Schema(description = "确认密码")
    private String checkPassword;

    @Schema(description = "昵称")
    private String userName;
}
