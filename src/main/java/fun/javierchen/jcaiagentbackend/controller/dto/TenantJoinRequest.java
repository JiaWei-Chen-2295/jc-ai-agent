package fun.javierchen.jcaiagentbackend.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "加入团队请求")
public class TenantJoinRequest {

    @Schema(description = "团队 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1001")
    private Long tenantId;
}
