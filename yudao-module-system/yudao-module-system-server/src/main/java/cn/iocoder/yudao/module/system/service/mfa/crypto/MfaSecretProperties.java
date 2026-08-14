package cn.iocoder.yudao.module.system.service.mfa.crypto;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "yudao.mfa.secret")
public class MfaSecretProperties {

    /**
     * 当前写入使用的 keyId。
     */
    private String activeKeyId;

    /**
     * keyId → Base64(32-byte AES key)
     */
    private Map<String, String> keys = new LinkedHashMap<>();

    public String getActiveKeyId() {
        return activeKeyId;
    }

    public void setActiveKeyId(String activeKeyId) {
        this.activeKeyId = activeKeyId;
    }

    public Map<String, String> getKeys() {
        return keys;
    }

    public void setKeys(Map<String, String> keys) {
        this.keys = keys == null ? new LinkedHashMap<>() : keys;
    }
}
