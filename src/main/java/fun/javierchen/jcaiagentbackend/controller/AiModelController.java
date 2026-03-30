package fun.javierchen.jcaiagentbackend.controller;

import fun.javierchen.jcaiagentbackend.app.ChatModelRegistry;
import fun.javierchen.jcaiagentbackend.common.BaseResponse;
import fun.javierchen.jcaiagentbackend.common.ErrorCode;
import fun.javierchen.jcaiagentbackend.common.ResultUtils;
import fun.javierchen.jcaiagentbackend.config.ApiKeyEncryptor;
import fun.javierchen.jcaiagentbackend.controller.dto.AiModelConfigRequest;
import fun.javierchen.jcaiagentbackend.controller.dto.AiModelVO;
import fun.javierchen.jcaiagentbackend.exception.BusinessException;
import fun.javierchen.jcaiagentbackend.exception.ThrowUtils;
import fun.javierchen.jcaiagentbackend.model.entity.AiModelConfig;
import fun.javierchen.jcaiagentbackend.model.entity.User;
import fun.javierchen.jcaiagentbackend.repository.AiModelConfigRepository;
import fun.javierchen.jcaiagentbackend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * AI 模型管理接口
 *
 * <ul>
 *   <li>{@code GET  /ai/models}             — 公开：获取已启用的模型列表（供前端下拉框）</li>
 *   <li>{@code GET  /ai/admin/models}        — 管理员：获取全部模型（含禁用）</li>
 *   <li>{@code POST /ai/admin/models}        — 管理员：新增模型配置</li>
 *   <li>{@code PUT  /ai/admin/models/{id}}   — 管理员：更新模型配置</li>
 *   <li>{@code PATCH /ai/admin/models/{id}/toggle} — 管理员：启用/禁用模型</li>
 *   <li>{@code DELETE /ai/admin/models/{id}} — 管理员：删除模型配置</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
@Tag(name = "AI 模型管理", description = "用户模型列表 + 管理员 CRUD")
public class AiModelController {

    private final AiModelConfigRepository modelConfigRepository;
    private final ApiKeyEncryptor apiKeyEncryptor;
    private final ChatModelRegistry chatModelRegistry;

    @Resource
    private UserService userService;

    // -------------------------------------------------------------------------
    // 公开接口
    // -------------------------------------------------------------------------

    @GetMapping(value = "/models", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "获取可用模型列表", description = "返回所有已启用的 AI 模型，供前端下拉框展示。")
    public BaseResponse<List<AiModelVO>> listModels() {
        List<AiModelConfig> configs = modelConfigRepository.findByEnabledTrueOrderBySortOrderAsc();
        List<AiModelVO> vos = configs.stream()
                .map(this::toPublicVO)
                .collect(Collectors.toList());
        return ResultUtils.success(vos);
    }

    // -------------------------------------------------------------------------
    // 管理员接口
    // -------------------------------------------------------------------------

    @GetMapping(value = "/admin/models", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "【管理员】获取全部模型（含禁用）")
    public BaseResponse<List<AiModelVO>> listAllModels(jakarta.servlet.http.HttpServletRequest request) {
        requireAdmin(request);
        List<AiModelConfig> configs = modelConfigRepository.findAllByOrderBySortOrderAsc();
        List<AiModelVO> vos = configs.stream()
                .map(this::toAdminVO)
                .collect(Collectors.toList());
        return ResultUtils.success(vos);
    }

    @PostMapping(value = "/admin/models", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "【管理员】新增模型配置")
    public BaseResponse<AiModelVO> createModel(
            @RequestBody AiModelConfigRequest req,
            jakarta.servlet.http.HttpServletRequest request) {
        requireAdmin(request);
        validateCreateRequest(req);

        AiModelConfig config = new AiModelConfig();
        applyRequest(config, req);
        // 创建时 API Key 必须提供（DashScope 可传空字符串）
        if (StringUtils.hasText(req.getApiKeyPlain())) {
            config.setApiKeyEnc(apiKeyEncryptor.encrypt(req.getApiKeyPlain()));
        }
        AiModelConfig saved = modelConfigRepository.save(config);
        log.info("AiModelController: created model config '{}'", saved.getModelId());
        return ResultUtils.success(toAdminVO(saved));
    }

    @PutMapping(value = "/admin/models/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "【管理员】更新模型配置")
    public BaseResponse<AiModelVO> updateModel(
            @PathVariable("id") Long id,
            @RequestBody AiModelConfigRequest req,
            jakarta.servlet.http.HttpServletRequest request) {
        requireAdmin(request);
        AiModelConfig config = modelConfigRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "模型不存在"));
        String oldModelId = config.getModelId();
        applyRequest(config, req);
        // 只有传入 apiKeyPlain 才更新 Key（留空则保留原值）
        if (StringUtils.hasText(req.getApiKeyPlain())) {
            config.setApiKeyEnc(apiKeyEncryptor.encrypt(req.getApiKeyPlain()));
        }
        AiModelConfig saved = modelConfigRepository.save(config);
        // 清除注册中心缓存，下次请求时重建
        chatModelRegistry.evict(oldModelId);
        if (!oldModelId.equals(saved.getModelId())) {
            chatModelRegistry.evict(saved.getModelId());
        }
        log.info("AiModelController: updated model config '{}'", saved.getModelId());
        return ResultUtils.success(toAdminVO(saved));
    }

    @PatchMapping(value = "/admin/models/{id}/toggle", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "【管理员】启用 / 禁用模型")
    public BaseResponse<Boolean> toggleModel(
            @PathVariable("id") Long id,
            jakarta.servlet.http.HttpServletRequest request) {
        requireAdmin(request);
        AiModelConfig config = modelConfigRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "模型不存在"));
        config.setEnabled(!config.getEnabled());
        modelConfigRepository.save(config);
        chatModelRegistry.evict(config.getModelId());
        log.info("AiModelController: toggled model '{}' enabled={}", config.getModelId(), config.getEnabled());
        return ResultUtils.success(config.getEnabled());
    }

    @DeleteMapping(value = "/admin/models/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "【管理员】删除模型配置")
    public BaseResponse<Boolean> deleteModel(
            @PathVariable("id") Long id,
            jakarta.servlet.http.HttpServletRequest request) {
        requireAdmin(request);
        AiModelConfig config = modelConfigRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "模型不存在"));
        modelConfigRepository.deleteById(id);
        chatModelRegistry.evict(config.getModelId());
        log.info("AiModelController: deleted model '{}'", config.getModelId());
        return ResultUtils.success(true);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void requireAdmin(jakarta.servlet.http.HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        ThrowUtils.throwIf(!userService.isAdmin(loginUser), ErrorCode.NO_AUTH_ERROR, "Admin required");
    }

    private void validateCreateRequest(AiModelConfigRequest req) {
        ThrowUtils.throwIf(!StringUtils.hasText(req.getProvider()), ErrorCode.PARAMS_ERROR, "provider 不能为空");
        ThrowUtils.throwIf(!StringUtils.hasText(req.getModelId()), ErrorCode.PARAMS_ERROR, "modelId 不能为空");
        ThrowUtils.throwIf(!StringUtils.hasText(req.getModelName()), ErrorCode.PARAMS_ERROR, "modelName 不能为空");
        ThrowUtils.throwIf(!StringUtils.hasText(req.getDisplayName()), ErrorCode.PARAMS_ERROR, "displayName 不能为空");
        // 非 DashScope 需要 baseUrl 和 apiKey
        if (!"dashscope".equalsIgnoreCase(req.getProvider())) {
            ThrowUtils.throwIf(!StringUtils.hasText(req.getBaseUrl()), ErrorCode.PARAMS_ERROR,
                    "非 DashScope 模型必须提供 baseUrl");
            ThrowUtils.throwIf(!StringUtils.hasText(req.getApiKeyPlain()), ErrorCode.PARAMS_ERROR,
                    "非 DashScope 模型必须提供 API Key");
        }
    }

    private void applyRequest(AiModelConfig config, AiModelConfigRequest req) {
        if (StringUtils.hasText(req.getProvider())) config.setProvider(req.getProvider());
        if (StringUtils.hasText(req.getModelId())) config.setModelId(req.getModelId());
        if (StringUtils.hasText(req.getModelName())) config.setModelName(req.getModelName());
        if (StringUtils.hasText(req.getDisplayName())) config.setDisplayName(req.getDisplayName());
        if (req.getBaseUrl() != null) config.setBaseUrl(req.getBaseUrl());
        if (req.getCompletionsPath() != null) config.setCompletionsPath(req.getCompletionsPath());
        if (req.getMaxTokens() != null) config.setMaxTokens(req.getMaxTokens());
        if (req.getTemperature() != null) config.setTemperature(req.getTemperature());
        if (req.getDescription() != null) config.setDescription(req.getDescription());
        if (req.getIconUrl() != null) config.setIconUrl(req.getIconUrl());
        if (req.getSortOrder() != null) config.setSortOrder(req.getSortOrder());
        if (req.getEnabled() != null) config.setEnabled(req.getEnabled());
    }

    /** 面向普通用户的 VO（不暴露 API Key、baseUrl、modelName） */
    private AiModelVO toPublicVO(AiModelConfig config) {
        AiModelVO vo = new AiModelVO();
        vo.setId(config.getId());
        vo.setProvider(config.getProvider());
        vo.setModelId(config.getModelId());
        vo.setDisplayName(config.getDisplayName());
        vo.setDescription(config.getDescription());
        vo.setIconUrl(config.getIconUrl());
        vo.setSortOrder(config.getSortOrder());
        vo.setEnabled(config.getEnabled());
        return vo;
    }

    /** 面向管理员的 VO（脱敏 API Key） */
    private AiModelVO toAdminVO(AiModelConfig config) {
        AiModelVO vo = toPublicVO(config);
        vo.setModelName(config.getModelName());
        vo.setBaseUrl(config.getBaseUrl());
        vo.setCompletionsPath(config.getCompletionsPath());
        if (StringUtils.hasText(config.getApiKeyEnc())) {
            try {
                String plain = apiKeyEncryptor.decrypt(config.getApiKeyEnc());
                vo.setApiKeyMasked(ApiKeyEncryptor.mask(plain));
            } catch (Exception e) {
                vo.setApiKeyMasked("****");
            }
        }
        return vo;
    }
}
