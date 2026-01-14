package fun.javierchen.jcaiagentbackend.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "用户登录请求")
public class UserLoginRequest {

    @Schema(description = "账号")
    private String userAccount;

    @Schema(description = "密码")
    private String userPassword;
}
