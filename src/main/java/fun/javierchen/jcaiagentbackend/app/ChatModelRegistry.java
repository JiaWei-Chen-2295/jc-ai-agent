package fun.javierchen.jcaiagentbackend.app;

import fun.javierchen.jcaiagentbackend.common.ErrorCode;
import fun.javierchen.jcaiagentbackend.config.ApiKeyEncryptor;
import fun.javierchen.jcaiagentbackend.exception.BusinessException;
import fun.javierchen.jcaiagentbackend.model.entity.AiModelConfig;
import fun.javierchen.jcaiagentbackend.repository.AiModelConfigRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 模型注册中心：根据 modelId 解析并返回对应的 {@link ChatModel} 实例。
 *
 * <ul>
 *   <li>DashScope 原生模型 —— 直接返回 Spring Boot 自动装配的 {@code dashscopeChatModel} Bean</li>
 *   <li>OpenAI 兼容模型（DeepSeek / GPT / Kimi / GLM 等）—— 根据数据库配置动态创建 {@link OpenAiChatModel}</li>
 * </ul>
 *
 * 解析结果缓存在内存中；管理员更新模型配置后需调用 {@link #evict(String)} 清除对应缓存。
 */
@Slf4j
@Component
public class ChatModelRegistry {

    /** 当用户未指定模型时使用的默认模型 ID */
    public static final String DEFAULT_MODEL_ID = "qwen3-max";

    private final ChatModel dashscopeChatModel;
    private final AiModelConfigRepository modelConfigRepo;
    private final ApiKeyEncryptor apiKeyEncryptor;

    /** modelId -> ChatModel 实例缓存（线程安全） */
    private final ConcurrentHashMap<String, ChatModel> modelCache = new ConcurrentHashMap<>();

    /** Spring AI OpenAiApi 默认的 completionsPath */
    private static final String DEFAULT_COMPLETIONS_PATH = "/v1/chat/completions";

    public ChatModelRegistry(
            @Qualifier("dashscopeChatModel") ChatModel dashscopeChatModel,
            AiModelConfigRepository modelConfigRepo,
            ApiKeyEncryptor apiKeyEncryptor) {
        this.dashscopeChatModel = dashscopeChatModel;
        this.modelConfigRepo = modelConfigRepo;
        this.apiKeyEncryptor = apiKeyEncryptor;
    }

    /**
     * 根据 modelId 返回对应的 {@link ChatModel}。
     *
     * @param modelId 模型业务 ID，为空时使用默认模型
     * @throws BusinessException 模型未启用或不存在时
     */
    public ChatModel resolve(String modelId) {
        String id = StringUtils.hasText(modelId) ? modelId : DEFAULT_MODEL_ID;
        return modelCache.computeIfAbsent(id, this::createChatModel);
    }

    /**
     * 清除指定模型的缓存（管理员更新模型配置后调用）
     */
    public void evict(String modelId) {
        modelCache.remove(modelId);
        log.info("ChatModelRegistry: evicted model cache for '{}'", modelId);
    }

    /**
     * 清除所有缓存
     */
    public void evictAll() {
        modelCache.clear();
        log.info("ChatModelRegistry: cleared all model cache");
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private ChatModel createChatModel(String modelId) {
        AiModelConfig config = modelConfigRepo.findByModelIdAndEnabled(modelId, Boolean.TRUE)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR,
                        "模型不可用或未启用: " + modelId));

        if ("dashscope".equalsIgnoreCase(config.getProvider())) {
            // DashScope 原生：直接复用自动装配实例，API Key 由 application.yml 管理
            log.debug("ChatModelRegistry: resolving '{}' → native DashScope model", modelId);
            return dashscopeChatModel;
        }

        // OpenAI 兼容提供商：动态创建 OpenAiChatModel
        return createOpenAiCompatibleModel(config);
    }

    private ChatModel createOpenAiCompatibleModel(AiModelConfig config) {
        if (!StringUtils.hasText(config.getBaseUrl())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,
                    "OpenAI 兼容模型缺少 base_url: " + config.getModelId());
        }
        if (!StringUtils.hasText(config.getApiKeyEnc())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,
                    "OpenAI 兼容模型缺少 API Key: " + config.getModelId());
        }

        String decryptedKey = apiKeyEncryptor.decrypt(config.getApiKeyEnc());

        // 根据官方文档，使用 OpenAiApi.builder() 构建
        // baseUrl 不含版本路径（如 https://api.deepseek.com）
        // completionsPath 默认 /v1/chat/completions，智谱等非标准提供商可自定义（如 /v4/chat/completions）
        // 参考：https://docs.spring.io/spring-ai/reference/api/chat/openai-chat.html
        String completionsPath = StringUtils.hasText(config.getCompletionsPath())
                ? config.getCompletionsPath()
                : DEFAULT_COMPLETIONS_PATH;

        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(config.getBaseUrl())
                .apiKey(decryptedKey)
                .completionsPath(completionsPath)
                .build();

        double temperature = config.getTemperature() != null
                ? config.getTemperature().doubleValue()
                : 0.7;

        OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder()
                .model(config.getModelName())
                .temperature(temperature);

        if (config.getMaxTokens() != null) {
            optionsBuilder.maxTokens(config.getMaxTokens());
        }

        OpenAiChatOptions options = optionsBuilder.build();

        log.info("ChatModelRegistry: creating OpenAI-compatible model '{}' provider='{}' baseUrl='{}' completionsPath='{}'",
                config.getModelId(), config.getProvider(), config.getBaseUrl(), completionsPath);
        return new OpenAiChatModel(openAiApi, options);
    }
}
