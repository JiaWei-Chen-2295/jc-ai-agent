package fun.javierchen.jcaiagentbackend.config;

import fun.javierchen.jcaiagentbackend.common.ErrorCode;
import fun.javierchen.jcaiagentbackend.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * API Key 对称加密组件（AES-256-GCM）
 * <p>
 * 主密钥通过环境变量 {@code JC_API_KEY_MASTER_SECRET} 提供，
 * 使用 SHA-256 将其派生为 256-bit AES 密钥。
 * <p>
 * 密文格式（Base64）：{12字节随机 IV} + {GCM 密文 + 16字节认证标签}
 * <p>
 * ⚠️ 生产环境必须通过环境变量设置强随机主密钥，禁止使用默认值。
 */
@Component
public class ApiKeyEncryptor {

    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final String ALGORITHM = "AES/GCM/NoPadding";

    private final SecretKey secretKey;

    public ApiKeyEncryptor(
            @Value("${JC_API_KEY_MASTER_SECRET}") String masterSecret) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = sha256.digest(masterSecret.getBytes(StandardCharsets.UTF_8));
            this.secretKey = new SecretKeySpec(keyBytes, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize ApiKeyEncryptor", e);
        }
    }

    /**
     * 加密明文 API Key，返回 Base64 编码的密文
     */
    public String encrypt(String plainText) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] cipherBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            // 格式：IV(12) + CipherText+Tag
            byte[] combined = new byte[iv.length + cipherBytes.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherBytes, 0, combined, iv.length, cipherBytes.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "API Key encryption failed");
        }
    }

    /**
     * 解密 Base64 编码的密文，返回明文 API Key
     */
    public String decrypt(String encryptedText) {
        try {
            byte[] combined = Base64.getDecoder().decode(encryptedText);
            if (combined.length <= GCM_IV_LENGTH) {
                throw new IllegalArgumentException("Invalid encrypted data length");
            }
            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, iv.length);
            byte[] cipherBytes = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, GCM_IV_LENGTH, cipherBytes, 0, cipherBytes.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return new String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "API Key decryption failed");
        }
    }

    /**
     * 将 API Key 脱敏为 "sk-***xxxx" 格式供前端展示
     */
    public static String mask(String apiKey) {
        if (apiKey == null || apiKey.length() <= 8) {
            return "****";
        }
        return apiKey.substring(0, Math.min(3, apiKey.indexOf('-') + 1)) +
                "***" + apiKey.substring(apiKey.length() - 4);
    }
}
