package fun.javierchen.jcaiagentbackend.config;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * API Key 加密工具（手动运行，不参与 CI）
 *
 * <p>使用方式（IntelliJ）：右键 encryptApiKey() → Run
 *
 * <p>使用方式（Maven）：
 * <pre>
 *   # Windows PowerShell
 *   $env:JC_API_KEY_MASTER_SECRET="your-secret"; mvn test -Dtest=ApiKeyEncryptorTool#encryptApiKey -pl .
 *
 *   # Linux / macOS
 *   JC_API_KEY_MASTER_SECRET=your-secret mvn test -Dtest=ApiKeyEncryptorTool#encryptApiKey -pl .
 * </pre>
 *
 * <p>修改下方 {@code PLAIN_API_KEY} 为你的明文 Key 后运行，密文直接输出到控制台。
 */
@Disabled("手动运行工具类，不参与自动测试")
class ApiKeyEncryptorTool {

    /** ← 在这里填写要加密的明文 API Key */
    private static final String PLAIN_API_KEY = "sk-xxxxxxxxxxxxx";

    @Test
    void encryptApiKey() {
        String masterSecret = System.getenv("JC_API_KEY_MASTER_SECRET");
        if (masterSecret == null || masterSecret.isBlank()) {
            masterSecret = "fdsfdsfdsfds-dsfsdfsdfds-fsdfsdfdsf";
            System.out.println("[WARN] 使用默认开发主密钥，生产环境请设置环境变量 JC_API_KEY_MASTER_SECRET");
        }

        ApiKeyEncryptor encryptor = new ApiKeyEncryptor(masterSecret);
        String encrypted = encryptor.encrypt(PLAIN_API_KEY);

        System.out.println("\n===== API Key 加密结果 =====");
        System.out.println("明文 : " + PLAIN_API_KEY);
        System.out.println("密文 : " + encrypted);
        System.out.println("（将密文写入数据库 ai_model_config.api_key_enc 字段）");

        // 验证可逆
        String decrypted = encryptor.decrypt(encrypted);
        if (PLAIN_API_KEY.equals(decrypted)) {
            System.out.println("✓ 验证通过：解密结果与原文一致");
        } else {
            throw new AssertionError("解密结果与原文不一致，请检查主密钥");
        }
    }
}
