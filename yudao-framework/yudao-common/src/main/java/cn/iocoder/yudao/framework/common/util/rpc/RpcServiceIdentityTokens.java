package cn.iocoder.yudao.framework.common.util.rpc;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.HMac;
import cn.hutool.crypto.digest.HmacAlgorithm;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * RPC 服务身份令牌：HMAC-SHA256，防伪造 header。
 */
public final class RpcServiceIdentityTokens {

    private RpcServiceIdentityTokens() {
    }

    public static String sign(String serviceName, String secret) {
        return sign(serviceName, secret, currentBucket());
    }

    public static String sign(String serviceName, String secret, long bucket) {
        if (StrUtil.isBlank(serviceName) || StrUtil.isBlank(secret)) {
            throw new IllegalArgumentException("serviceName/secret required");
        }
        return hmacHex(secret, serviceName.trim() + ":" + bucket);
    }

    /**
     * 校验 token 是否与 serviceName + secret 匹配（当前桶或相邻桶）。
     */
    public static boolean verify(String serviceName, String token, String secret) {
        if (StrUtil.isBlank(serviceName) || StrUtil.isBlank(token) || StrUtil.isBlank(secret)) {
            return false;
        }
        long bucket = currentBucket();
        for (long b = bucket - 1; b <= bucket + 1; b++) {
            if (constantTimeEquals(token.trim(), sign(serviceName, secret, b))) {
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
