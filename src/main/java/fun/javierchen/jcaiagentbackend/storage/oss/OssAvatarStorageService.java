package fun.javierchen.jcaiagentbackend.storage.oss;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.MatchMode;
import com.aliyun.oss.model.PolicyConditions;
import fun.javierchen.jcaiagentbackend.common.ErrorCode;
import fun.javierchen.jcaiagentbackend.exception.BusinessException;
import fun.javierchen.jcaiagentbackend.storage.StorageProperties;
import fun.javierchen.jcaiagentbackend.storage.StorageReadUrlService;
import fun.javierchen.jcaiagentbackend.storage.avatar.AvatarObjectKeyFactory;
import fun.javierchen.jcaiagentbackend.storage.avatar.AvatarStorageService;
import fun.javierchen.jcaiagentbackend.storage.avatar.AvatarUploadToken;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "storage", name = "type", havingValue = "oss")
public class OssAvatarStorageService implements AvatarStorageService {

    private final OSS ossClient;
    private final StorageProperties storageProperties;
    private final StorageReadUrlService storageReadUrlService;
    private final AvatarObjectKeyFactory avatarObjectKeyFactory;


    @Override
    public AvatarUploadToken createAvatarUploadToken(long userId, String fileName) {
        StorageProperties.Oss oss = storageProperties.getOss();
        if (oss == null || StringUtils.isBlank(oss.getBucket())) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "OSS 配置不完整：storage.oss.bucket");
        }

        String objectKey = avatarObjectKeyFactory.createAvatarKey(userId, fileName);
        long expireSeconds = Math.max(oss.getExpireSeconds(), 30);
        long expireAt = (System.currentTimeMillis() / 1000) + expireSeconds;

        PolicyConditions policyConditions = new PolicyConditions();
        policyConditions.addConditionItem(MatchMode.Exact, PolicyConditions.COND_KEY, objectKey);
        long maxBytes = Math.max(oss.getMaxSizeMb(), 1) * 1024L * 1024L;
        policyConditions.addConditionItem(PolicyConditions.COND_CONTENT_LENGTH_RANGE, 0, maxBytes);

        java.util.Date expiration = new java.util.Date(System.currentTimeMillis() + expireSeconds * 1000);
        String policy = ossClient.generatePostPolicy(expiration, policyConditions);
        String policyBase64 = Base64.getEncoder().encodeToString(policy.getBytes(StandardCharsets.UTF_8));
        String signature = ossClient.calculatePostSignature(policy);

        String uploadHost = buildUploadHost(oss.getEndpoint(), oss.getBucket());
        Map<String, String> formFields = new LinkedHashMap<>();
        formFields.put("key", objectKey);
        formFields.put("policy", policyBase64);
        formFields.put("OSSAccessKeyId", oss.getAccessKeyId());
        formFields.put("signature", signature);
        formFields.put("success_action_status", "200");

        return new AvatarUploadToken(
                "oss",
                uploadHost,
                objectKey,
                expireAt,
                formFields,
                storageReadUrlService.toReadUrl(objectKey)
        );
    }

    @Override
    public boolean isAvatarKeyOwnedByUser(String avatarKey, long userId) {
        String key = StringUtils.trimToEmpty(avatarKey);
        if (StringUtils.containsAny(key, "..", "\\", "\0")) {
            return false;
        }
        String prefix = "avatar/" + userId + "/";
        return StringUtils.startsWith(key, prefix);
    }

    private String buildUploadHost(String endpoint, String bucket) {
        String ep = StringUtils.trimToEmpty(endpoint);
        ep = StringUtils.removeStartIgnoreCase(ep, "https://");
        ep = StringUtils.removeStartIgnoreCase(ep, "http://");
        return "https://" + bucket + "." + ep;
    }
}
