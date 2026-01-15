package fun.javierchen.jcaiagentbackend.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Schema(description = "团队信息")
public class TenantVO {

    @Schema(description = "团队 ID", example = "1001")
    private Long id;

    @Schema(description = "团队名称")
    private String tenantName;

    @Schema(description = "团队类型：personal/team")
    private String tenantType;

    @Schema(description = "团队管理员用户 ID")
    private Long ownerUserId;

    @Schema(description = "当前用户在团队中的角色：admin/member")
    private String role;

    @Schema(description = "创建时间")
    private OffsetDateTime createTime;

    @Schema(description = "更新时间")
    private OffsetDateTime updateTime;
}
