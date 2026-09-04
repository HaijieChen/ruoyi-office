package cn.iocoder.yudao.module.bpm.framework.im;

import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * HMAC-SHA256(timestamp + eventId + socialType + openid + action)。空密钥一律失败。
 */
public final class ImCardSignature {

    static final long MAX_SKEW_MS = Duration.ofMinutes(5).toMillis();

    private ImCardSignature() {
    }

    public static String sign(String secret, long timestamp, String eventId, Integer socialType,
                              String openid, String action) {
        String payload = timestamp + "\n" + n(eventId) + "\n" + socialType + "\n" + n(openid) + "\n" + n(action);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(raw.length * 2);
            for (byte b : raw) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("HMAC failed", ex);
        }
    }

    public static boolean verify(String secret, long timestamp, String eventId, Integer socialType,
                                 String openid, String action, String signature, long nowMs) {
        if (!StringUtils.hasText(secret) || !StringUtils.hasText(signature)) {
            return false;
        }
        if (Math.abs(nowMs - timestamp) > MAX_SKEW_MS) {
            return false;
        }
        String expected = sign(secret, timestamp, eventId, socialType, openid, action);
        return expected.equalsIgnoreCase(signature);
    }

    private static String n(String value) {
        return value == null ? "" : value;
    }
}
