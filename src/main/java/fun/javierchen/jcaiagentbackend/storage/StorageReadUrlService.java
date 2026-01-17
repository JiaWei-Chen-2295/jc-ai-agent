package fun.javierchen.jcaiagentbackend.storage;

/**
 * 把对象 Key 转为“可访问 URL”（public domain 拼接或 provider 签名 URL）。
 */
public interface StorageReadUrlService {

    String toReadUrl(String keyOrUrl);
}

