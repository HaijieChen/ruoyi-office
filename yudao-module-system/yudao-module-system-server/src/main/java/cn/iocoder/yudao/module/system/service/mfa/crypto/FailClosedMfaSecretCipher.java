package cn.iocoder.yudao.module.system.service.mfa.crypto;

/**
 * 未配置受控密钥时 fail-closed，禁止明文回退。
 */
public final class FailClosedMfaSecretCipher implements MfaSecretCipher {

    public static final FailClosedMfaSecretCipher INSTANCE = new FailClosedMfaSecretCipher();

    private FailClosedMfaSecretCipher() {
    }

    @Override
    public String activeKeyId() {
        throw new IllegalStateException("MFA secret keys are not configured");
    }

    @Override
    public String encrypt(String plaintext) {
        throw new IllegalStateException("MFA secret keys are not configured");
    }

    @Override
    public String decrypt(String keyId, String ciphertext) {
        throw new IllegalStateException("MFA secret keys are not configured");
    }
}
