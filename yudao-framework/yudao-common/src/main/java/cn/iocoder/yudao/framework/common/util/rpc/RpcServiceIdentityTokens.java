package cn.iocoder.yudao.framework.common.util.rpc;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.HMac;
import cn.hutool.crypto.digest.HmacAlgorithm;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * RPC 服务身份令牌：HMAC-SHA256，绑定 service + audience（方法/路径），防伪造与跨路由重放。
 */
public final class RpcServiceIdentityTokens {

    private RpcServiceIdentityTokens() {
    }

    /**
     * 签发：payload = serviceName + ":" + audience + ":" + timeBucket
     */
    public static String sign(String serviceName, String audience, String secret) {
        return sign(serviceName, audience, secret, currentBucket());
    }

    public static String sign(String serviceName, String audience, String secret, long bucket) {
        if (StrUtil.isBlank(serviceName) || StrUtil.isBlank(audience) || StrUtil.isBlank(secret)) {
            throw new IllegalArgumentException("serviceName/audience/secret required");
        }
        return hmacHex(secret, serviceName.trim() + ":" + audience.trim() + ":" + bucket);
    }

    /**
     * 校验 token 是否与 serviceName + audience + secret 匹配（当前桶或相邻桶）。
     */
    public static boolean verify(String serviceName, String audience, String token, String secret) {
        if (StrUtil.isBlank(serviceName) || StrUtil.isBlank(audience)
                || StrUtil.isBlank(token) || StrUtil.isBlank(secret)) {
            return false;
        }
        long bucket = currentBucket();
        for (long b = bucket - 1; b <= bucket + 1; b++) {
            if (constantTimeEquals(token.trim(), sign(serviceName, audience, secret, b))) {
                return true;
            }
        }
        return false;
    }

    public static long currentBucket() {
        return System.currentTimeMillis() / 1000L / RpcServiceIdentityConstants.TOKEN_BUCKET_SECONDS;
    }

    private static String hmacHex(String secret, String payload) {
        HMac hmac = new HMac(HmacAlgorithm.HmacSHA256, secret.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hmac.digest(payload.getBytes(StandardCharsets.UTF_8)));
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int r = 0;
        for (int i = 0; i < a.length(); i++) {
            r |= a.charAt(i) ^ b.charAt(i);
        }
        return r == 0;
    }
}
