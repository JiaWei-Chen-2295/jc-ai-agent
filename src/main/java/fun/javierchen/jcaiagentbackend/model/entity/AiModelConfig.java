package fun.javierchen.jcaiagentbackend.model.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * AI 模型配置实体
 * <p>
 * 存储所有可用的 Chat 模型信息。DashScope 原生模型的 API Key 由环境变量管理，
 * api_key_enc 可为 null；OpenAI 兼容模型的 API Key 使用 AES-256-GCM 加密存储。
 */
@Entity
@Table(name = "ai_model_config")
@Data
@Schema(description = "AI 模型配置")
public class AiModelConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "主键")
    private Long id;

    /**
     * 提供商标识：dashscope | openai | deepseek | kimi | glm
     */
    @Column(name = "provider", nullable = false, length = 32)
    @Schema(description = "提供商标识")
    private String provider;

    /**
     * 业务唯一标识，如 qwen3-max、deepseek-chat、gpt-4o
     */
    @Column(name = "model_id", nullable = false, unique = true, length = 64)
    @Schema(description = "模型业务 ID（唯一）")
    private String modelId;

    /**
     * 调用 API 时实际传入的 model 名称
     */
    @Column(name = "model_name", nullable = false, length = 128)
    @Schema(description = "API 实际模型名")
    private String modelName;

    /**
     * 前端展示名称
     */
    @Column(name = "display_name", nullable = false, length = 128)
    @Schema(description = "前端展示名称")
    private String displayName;

    /**
     * OpenAI 兼容端点；DashScope 原生模型此字段为 null
     */
    @Column(name = "base_url", length = 512)
    @Schema(description = "OpenAI 兼容端点 URL（DashScope 可为空）")
    private String baseUrl;

    /**
     * 自定义 chat completions 路径。用于非标准 OpenAI 兼容协议（如智谱 /v4/chat/completions）。
     * 为 null 时使用 Spring AI 默认值 /v1/chat/completions。
     */
    @Column(name = "completions_path", length = 256)
    @Schema(description = "自定义 completions 路径（为空则使用默认 /v1/chat/completions）")
    private String completionsPath;

    /**
     * AES-256-GCM 加密的 API Key；DashScope 原生模型由环境变量管理，可为 null
     */
    @Column(name = "api_key_enc", length = 1024)
    @Schema(description = "加密存储的 API Key（不对外暴露）")
    private String apiKeyEnc;

    @Column(name = "max_tokens")
    @Schema(description = "模型最大输出 token 数")
    private Integer maxTokens;

    @Column(name = "temperature", precision = 3, scale = 2)
    @Schema(description = "默认温度（0.0 ~ 1.0）")
    private BigDecimal temperature;

    @Column(name = "description", length = 512)
    @Schema(description = "模型描述")
    private String description;

    @Column(name = "icon_url", length = 512)
    @Schema(description = "模型图标 URL")
    private String iconUrl;

    @Column(name = "enabled", nullable = false)
    @Schema(description = "是否启用")
    private Boolean enabled = Boolean.TRUE;

    @Column(name = "sort_order", nullable = false)
    @Schema(description = "排序权重（升序）")
    private Integer sortOrder = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Schema(description = "创建时间")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @Schema(description = "更新时间")
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}
