package cn.iocoder.yudao.module.system.service.mfa.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-256-GCM。禁止 plain-dev / 明文回退。
 */
public class MfaSecretCipherImpl implements MfaSecretCipher {

    public static final String FORBIDDEN_KEY_ID = "plain-dev";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_BYTES = 12;
    private static final int KEY_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final String activeKeyId;
    private final Map<String, byte[]> keys;

    public MfaSecretCipherImpl(String activeKeyId, Map<String, byte[]> keys) {
        if (activeKeyId == null || activeKeyId.isBlank()) {
            throw new IllegalStateException("MFA secret activeKeyId is required");
        }
        if (FORBIDDEN_KEY_ID.equalsIgnoreCase(activeKeyId.trim())) {
            throw new IllegalStateException("MFA secret keyId plain-dev is forbidden");
        }
        if (keys == null || keys.isEmpty() || !keys.containsKey(activeKeyId)) {
            throw new IllegalStateException("MFA secret active key is missing");
        }
        this.activeKeyId = activeKeyId;
        this.keys = Map.copyOf(keys);
        for (Map.Entry<String, byte[]> e : this.keys.entrySet()) {
            if (FORBIDDEN_KEY_ID.equalsIgnoreCase(e.getKey())) {
                throw new IllegalStateException("MFA secret keyId plain-dev is forbidden");
            }
            if (e.getValue() == null || e.getValue().length != KEY_BYTES) {
                throw new IllegalStateException("MFA secret key must be 32 bytes: " + e.getKey());
            }
        }
    }

    public static MfaSecretCipherImpl fromProperties(MfaSecretProperties properties) {
        Objects.requireNonNull(properties, "properties");
        if (properties.getActiveKeyId() == null || properties.getKeys() == null
                || properties.getKeys().isEmpty()) {
            throw new IllegalStateException("MFA secret keys are not configured");
        }
        Map<String, byte[]> decoded = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : properties.getKeys().entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            decoded.put(e.getKey(), Base64.getDecoder().decode(e.getValue().trim()));
        }
        return new MfaSecretCipherImpl(properties.getActiveKeyId(), decoded);
    }

    public static MfaSecretCipherImpl forTests(String keyId) {
        byte[] key = new byte[KEY_BYTES];
        RANDOM.nextBytes(key);
        return new MfaSecretCipherImpl(keyId, Map.of(keyId, key));
    }

    @Override
    public String activeKeyId() {
        return activeKeyId;
    }

    @Override
    public String encrypt(String plaintext) {
        if (plaintext == null) {
            throw new IllegalStateException("MFA secret plaintext is required");
        }
        byte[] key = requireKey(activeKeyId);
        byte[] iv = new byte[IV_BYTES];
        RANDOM.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buf = ByteBuffer.allocate(iv.length + ct.length);
            buf.put(iv);
            buf.put(ct);
            return Base64.getEncoder().encodeToString(buf.array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("MFA secret encrypt failed", e);
        }
    }

    @Override
    public String decrypt(String keyId, String ciphertext) {
        if (keyId == null || keyId.isBlank()
                || FORBIDDEN_KEY_ID.equalsIgnoreCase(keyId.trim())) {
            throw new IllegalStateException("MFA secret keyId is not usable");
        }
        if (ciphertext == null || ciphertext.isBlank()) {
            throw new IllegalStateException("MFA secret ciphertext is required");
        }
        byte[] key = requireKey(keyId);
        byte[] packed;
        try {
            packed = Base64.getDecoder().decode(ciphertext);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("MFA secret ciphertext is not valid", e);
        }
        if (packed.length <= IV_BYTES) {
            throw new IllegalStateException("MFA secret ciphertext is truncated");
        }
        byte[] iv = new byte[IV_BYTES];
        byte[] ct = new byte[packed.length - IV_BYTES];
        System.arraycopy(packed, 0, iv, 0, IV_BYTES);
        System.arraycopy(packed, IV_BYTES, ct, 0, ct.length);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("MFA secret decrypt failed", e);
        }
    }

    private byte[] requireKey(String keyId) {
        byte[] key = keys.get(keyId);
        if (key == null) {
            // also try case-sensitive only; never fall back
            for (Map.Entry<String, byte[]> e : keys.entrySet()) {
                if (e.getKey().equalsIgnoreCase(keyId)
                        && !e.getKey().toLowerCase(Locale.ROOT).equals(FORBIDDEN_KEY_ID)) {
                    // still require exact id
                    break;
                }
            }
            throw new IllegalStateException("MFA secret key not found: " + keyId);
        }
        return key;
    }
}
