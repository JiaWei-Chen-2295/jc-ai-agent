package fun.javierchen.jcaiagentbackend.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "创建团队请求")
public class TenantCreateRequest {

    @Schema(description = "团队名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String tenantName;
}
