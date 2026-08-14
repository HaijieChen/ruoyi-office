package cn.iocoder.yudao.module.system.service.mfa.crypto;

/**
 * TOTP/通道 secret 加密（ADR-MFA-v3 §8.1）：AES-256-GCM，按 keyId 读旧写新。
 */
public interface MfaSecretCipher {

    String activeKeyId();

    /**
     * @return 密文（不含 keyId）；缺密钥 fail-closed
     */
    String encrypt(String plaintext);

    /**
     * 按写入时的 keyId 解密；未知 keyId / 损坏密文 fail-closed。
     */
    String decrypt(String keyId, String ciphertext);

    default boolean isActiveKey(String keyId) {
        return keyId != null && keyId.equals(activeKeyId());
    }
}
