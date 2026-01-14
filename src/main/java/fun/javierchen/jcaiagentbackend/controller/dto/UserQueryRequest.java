package fun.javierchen.jcaiagentbackend.controller.dto;

import fun.javierchen.jcaiagentbackend.common.PageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "用户查询请求")
public class UserQueryRequest extends PageRequest {

    @Schema(description = "用户 id")
    private Long id;

    @Schema(description = "账号")
    private String userAccount;

    @Schema(description = "昵称")
    private String userName;

    @Schema(description = "角色")
    private String userRole;
}
