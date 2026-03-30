"""
API Key 加密工具
与后端 ApiKeyEncryptor.java 使用完全相同的算法：AES-256-GCM
格式：Base64( IV(12B) || Ciphertext || GCM-Tag(16B) )
主密钥通过环境变量 JC_API_KEY_MASTER_SECRET 读取，与服务端保持一致。

依赖：pip install cryptography

用法：
  # 交互输入
  python scripts/encrypt-api-key.py

  # 命令行参数
  python scripts/encrypt-api-key.py sk-your-plain-api-key

  # 指定主密钥（生产环境）
  JC_API_KEY_MASTER_SECRET=your-prod-secret python scripts/encrypt-api-key.py sk-your-key
"""

import os
import sys
import hashlib
import base64
import secrets

try:
    from cryptography.hazmat.primitives.ciphers.aead import AESGCM
except ImportError:
    print("缺少依赖，请运行: pip install cryptography")
    sys.exit(1)


DEFAULT_MASTER_SECRET = "jc-dev-placeholder-change-in-prod-32c"


def derive_key(master_secret: str) -> bytes:
    """SHA-256 派生 32 字节 AES 密钥，与 Java 端 MessageDigest.getInstance("SHA-256") 一致"""
    return hashlib.sha256(master_secret.encode("utf-8")).digest()


def encrypt(plain_text: str, master_secret: str) -> str:
    """
    加密明文，返回 Base64(IV + Ciphertext+Tag)
    与 ApiKeyEncryptor#encrypt 完全对应
    """
    key = derive_key(master_secret)
    iv = secrets.token_bytes(12)            # GCM_IV_LENGTH = 12
    aesgcm = AESGCM(key)
    # AESGCM.encrypt 自动附加 16 字节 Tag（GCM_TAG_LENGTH=128bit）
    ciphertext_with_tag = aesgcm.encrypt(iv, plain_text.encode("utf-8"), None)
    combined = iv + ciphertext_with_tag
    return base64.b64encode(combined).decode("utf-8")


def decrypt(encrypted_text: str, master_secret: str) -> str:
    """解密，用于验证结果（与 ApiKeyEncryptor#decrypt 对应）"""
    key = derive_key(master_secret)
    combined = base64.b64decode(encrypted_text)
    iv = combined[:12]
    ciphertext_with_tag = combined[12:]
    aesgcm = AESGCM(key)
    plain = aesgcm.decrypt(iv, ciphertext_with_tag, None)
    return plain.decode("utf-8")


def main():
    master_secret = os.environ.get("JC_API_KEY_MASTER_SECRET", DEFAULT_MASTER_SECRET)

    if master_secret == DEFAULT_MASTER_SECRET:
        print("[WARN] 使用默认开发主密钥，生产环境请设置环境变量 JC_API_KEY_MASTER_SECRET")

    # 从命令行参数或交互输入读取明文 Key
    if len(sys.argv) > 1:
        plain_key = sys.argv[1]
    else:
        plain_key = input("请输入明文 API Key: ").strip()

    if not plain_key:
        print("错误：API Key 不能为空")
        sys.exit(1)

    encrypted = encrypt(plain_key, master_secret)
    print(f"\n密文（写入数据库 api_key_enc 字段）:\n{encrypted}")

    # 自动验证解密一致性
    decrypted = decrypt(encrypted, master_secret)
    if decrypted == plain_key:
        print("\n✓ 验证通过：解密结果与原文一致")
    else:
        print("\n✗ 验证失败：解密结果与原文不一致，请检查主密钥")
        sys.exit(1)


if __name__ == "__main__":
    main()
