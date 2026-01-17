package fun.javierchen.jcaiagentbackend.storage.oss;

import com.aliyun.oss.HttpMethod;
import com.aliyun.oss.OSS;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import fun.javierchen.jcaiagentbackend.storage.StorageProperties;
import fun.javierchen.jcaiagentbackend.storage.StorageReadUrlService;
import fun.javierchen.jcaiagentbackend.storage.StorageUrlResolver;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.net.URL;
import java.util.Date;

@Service
@ConditionalOnProperty(prefix = "storage", name = "type", havingValue = "oss")
public class OssStorageReadUrlService implements StorageReadUrlService {

    private final OSS ossClient;
    private final StorageProperties storageProperties;
    private final StorageUrlResolver storageUrlResolver;

    public OssStorageReadUrlService(OSS ossClient, StorageProperties storageProperties, StorageUrlResolver storageUrlResolver) {
        this.ossClient = ossClient;
        this.storageProperties = storageProperties;
        this.storageUrlResolver = storageUrlResolver;
    }

    @Override
    public String toReadUrl(String keyOrUrl) {
        String value = StringUtils.trimToNull(keyOrUrl);
        if (value == null) {
            return null;
        }
        if (StringUtils.startsWithIgnoreCase(value, "http://") || StringUtils.startsWithIgnoreCase(value, "https://")) {
            return value;
        }

        String key = StringUtils.removeStart(value, "/");
        if (key.length() > 2048 || StringUtils.containsAny(key, "..", "\\", "\0")) {
            return null;
        }

        StorageProperties.Oss oss = storageProperties.getOss();
        if (oss == null || StringUtils.isBlank(oss.getBucket())) {
            return storageUrlResolver.toPublicUrl(key);
        }
        if (!oss.isSignReadUrl()) {
            return storageUrlResolver.toPublicUrl(key);
        }

        long expireSeconds = Math.max(oss.getReadUrlExpireSeconds(), 60);
        Date expiration = new Date(System.currentTimeMillis() + expireSeconds * 1000);
        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(oss.getBucket(), key, HttpMethod.GET);
        request.setExpiration(expiration);
        URL url = ossClient.generatePresignedUrl(request);
        return url == null ? null : url.toString();
    }
}

