package fun.javierchen.jcaiagentbackend.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "退出团队请求")
public class TenantLeaveRequest {

    @Schema(description = "团队 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1001")
    private Long tenantId;
}
