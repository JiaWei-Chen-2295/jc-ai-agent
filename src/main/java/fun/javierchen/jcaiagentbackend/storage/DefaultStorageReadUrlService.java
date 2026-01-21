package fun.javierchen.jcaiagentbackend.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnMissingBean(StorageReadUrlService.class)
public class DefaultStorageReadUrlService implements StorageReadUrlService {

    private final StorageUrlResolver storageUrlResolver;

    @Override
    public String toReadUrl(String keyOrUrl) {
        return storageUrlResolver.toPublicUrl(keyOrUrl);
    }
}

