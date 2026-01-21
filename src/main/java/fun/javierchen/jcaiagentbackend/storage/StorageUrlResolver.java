package fun.javierchen.jcaiagentbackend.storage;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import javax.annotation.Nullable;

@Component
@RequiredArgsConstructor
public class StorageUrlResolver {

    private final StorageProperties storageProperties;

    /**
     * 把 Key 或 URL 转为可访问 URL：
     * - 入参已经是 http(s) URL：原样返回
     * - 入参是 Key：使用 storage.domain 拼接
     */
    public String toPublicUrl(@Nullable String keyOrUrl) {
        String value = StringUtils.trimToNull(keyOrUrl);
        if (value == null) {
            return null;
        }
        if (StringUtils.startsWithIgnoreCase(value, "http://") || StringUtils.startsWithIgnoreCase(value, "https://")) {
            return value;
        }
        String domain = StringUtils.trimToNull(storageProperties.getDomain());
        if (domain == null) {
            return value;
        }
        domain = StringUtils.removeEnd(domain, "/");
        value = StringUtils.removeStart(value, "/");
        return domain + "/" + value;
    }

    /**
     * 规范化：如果传入的是本系统 domain 下的 URL，则抽取出 Key；否则原样返回。
     */
    public String toKeyIfMatchesDomain(String keyOrUrl) {
        String value = StringUtils.trimToNull(keyOrUrl);
        if (value == null) {
            return null;
        }
        String domain = StringUtils.trimToNull(storageProperties.getDomain());
        if (domain == null) {
            return value;
        }
        domain = StringUtils.removeEnd(domain, "/");
        if (!StringUtils.startsWithIgnoreCase(value, domain + "/")) {
            return value;
        }
        String key = value.substring((domain + "/").length());
        return StringUtils.removeStart(key, "/");
    }
}

