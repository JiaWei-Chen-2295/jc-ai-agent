package fun.javierchen.jcaiagentbackend.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "切换当前团队请求")
public class TenantSetActiveRequest {

    @Schema(description = "团队 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1001")
    private Long tenantId;
}
