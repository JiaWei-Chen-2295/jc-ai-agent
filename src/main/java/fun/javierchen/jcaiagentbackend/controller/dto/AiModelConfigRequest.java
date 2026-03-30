package fun.javierchen.jcaiagentbackend.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 管理员创建 / 更新 AI 模型配置的请求体
 */
@Data
@Schema(description = "AI 模型配置请求")
public class AiModelConfigRequest {

    @Schema(description = "提供商标识（dashscope / openai / deepseek / kimi / glm）", required = true, example = "deepseek")
    private String provider;

    @Schema(description = "模型业务 ID（全局唯一）", required = true, example = "deepseek-chat")
    private String modelId;

    @Schema(description = "发送给 API 的实际模型名", required = true, example = "deepseek-chat")
    private String modelName;

    @Schema(description = "前端展示名称", required = true, example = "DeepSeek Chat")
    private String displayName;

    @Schema(description = "OpenAI 兼容端点（非 DashScope 必填）", example = "https://api.deepseek.com")
    private String baseUrl;

    @Schema(description = "自定义 completions 路径（如智谱 /v4/chat/completions）；为空则使用默认 /v1/chat/completions")
    private String completionsPath;

    @Schema(description = "明文 API Key（创建时必填；更新时留空则保留原值）")
    private String apiKeyPlain;

    @Schema(description = "最大输出 token 数")
    private Integer maxTokens;

    @Schema(description = "默认温度（0.0 ~ 1.0）", example = "0.70")
    private BigDecimal temperature;

    @Schema(description = "模型描述")
    private String description;

    @Schema(description = "模型图标 URL")
    private String iconUrl;

    @Schema(description = "排序权重（升序）", example = "1")
    private Integer sortOrder;

    @Schema(description = "是否启用", example = "true")
    private Boolean enabled;
}
