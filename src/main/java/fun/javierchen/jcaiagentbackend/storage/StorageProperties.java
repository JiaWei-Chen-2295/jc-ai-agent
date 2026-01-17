package fun.javierchen.jcaiagentbackend.storage;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "storage")
public class StorageProperties {

    private StorageType type = StorageType.none;

    /**
     * 公共访问域名（用于把对象 Key 拼接成可访问 URL）
     * 示例：https://user-assets-prod.oss-cn-xxx.aliyuncs.com
     */
    private String domain;

    private Oss oss = new Oss();

    @Data
    public static class Oss {
        /**
         * OSS endpoint，例如：oss-cn-hangzhou.aliyuncs.com 或 https://oss-cn-hangzhou.aliyuncs.com
         */
        private String endpoint;
        private String bucket;
        private String accessKeyId;
        private String accessKeySecret;

        /**
         * 上传策略有效期（秒）
         */
        private long expireSeconds = 300;

        /**
         * 头像最大大小（MB）
         */
        private long maxSizeMb = 2;

        /**
         * Bucket 若为私有读，可以开启“读取签名 URL”以支持前端展示（UserVO 会返回临时可访问 URL）
         */
        private boolean signReadUrl = false;

        /**
         * 读取签名 URL 有效期（秒）
         */
        private long readUrlExpireSeconds = 3600;
    }
}
