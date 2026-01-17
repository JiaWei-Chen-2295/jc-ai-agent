package fun.javierchen.jcaiagentbackend.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnMissingBean(StorageReadUrlService.class)
public class DefaultStorageReadUrlService implements StorageReadUrlService {

    private final StorageUrlResolver storageUrlResolver;

    public DefaultStorageReadUrlService(StorageUrlResolver storageUrlResolver) {
        this.storageUrlResolver = storageUrlResolver;
    }

    @Override
    public String toReadUrl(String keyOrUrl) {
        return storageUrlResolver.toPublicUrl(keyOrUrl);
    }
}

