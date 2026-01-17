package fun.javierchen.jcaiagentbackend.storage.oss;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import fun.javierchen.jcaiagentbackend.common.ErrorCode;
import fun.javierchen.jcaiagentbackend.exception.BusinessException;
import fun.javierchen.jcaiagentbackend.storage.StorageProperties;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "storage", name = "type", havingValue = "oss")
public class OssClientConfig {

    @Bean(destroyMethod = "shutdown")
    public OSS ossClient(StorageProperties storageProperties) {
        StorageProperties.Oss oss = storageProperties.getOss();
        if (oss == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "storage.oss 未配置");
        }
        if (StringUtils.isAnyBlank(oss.getEndpoint(), oss.getAccessKeyId(), oss.getAccessKeySecret())) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "OSS 配置不完整：storage.oss.endpoint/access-key-id/access-key-secret");
        }
        String endpoint = normalizeEndpoint(oss.getEndpoint());
        return new OSSClientBuilder().build(endpoint, oss.getAccessKeyId(), oss.getAccessKeySecret());
    }

    private String normalizeEndpoint(String endpoint) {
        String value = StringUtils.trimToEmpty(endpoint);
        if (StringUtils.startsWithIgnoreCase(value, "http://") || StringUtils.startsWithIgnoreCase(value, "https://")) {
            return value;
        }
        return "https://" + value;
    }
}

