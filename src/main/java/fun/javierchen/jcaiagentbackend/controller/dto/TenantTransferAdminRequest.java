package fun.javierchen.jcaiagentbackend.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "管理员转让请求")
public class TenantTransferAdminRequest {

    @Schema(description = "团队 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1001")
    private Long tenantId;

    @Schema(description = "新的管理员用户 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "2002")
    private Long newAdminUserId;
}
