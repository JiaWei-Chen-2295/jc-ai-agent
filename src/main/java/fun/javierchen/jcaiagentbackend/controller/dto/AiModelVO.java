package fun.javierchen.jcaiagentbackend.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * AI 模型信息（对前端暴露，不含 API Key）
 */
@Data
@Schema(description = "AI 模型信息")
public class AiModelVO {

    @Schema(description = "主键 ID")
    private Long id;

    @Schema(description = "提供商标识", example = "dashscope")
    private String provider;

    @Schema(description = "模型业务 ID", example = "qwen3-max")
    private String modelId;

    @Schema(description = "模型展示名称", example = "通义千问3 Max")
    private String displayName;

    @Schema(description = "模型描述")
    private String description;

    @Schema(description = "模型图标 URL")
    private String iconUrl;

    @Schema(description = "排序权重")
    private Integer sortOrder;

    @Schema(description = "是否启用")
    private Boolean enabled;

    // 管理员视图额外字段
    @Schema(description = "API 实际模型名（管理员可见）")
    private String modelName;

    @Schema(description = "OpenAI 兼容端点（管理员可见）")
    private String baseUrl;

    @Schema(description = "自定义 completions 路径（管理员可见）")
    private String completionsPath;

    @Schema(description = "API Key 脱敏展示（管理员可见）", example = "sk-***xxxx")
    private String apiKeyMasked;
}
