package cn.iocoder.yudao.framework.security.config;

import cn.hutool.core.util.StrUtil;

import java.util.Locale;
import java.util.Set;

/**
 * EXP-87 F1：RPC 服务身份密钥强度校验（fail-closed）。
 * <p>
 * 拒绝：null / 空 / 空白 / 长度不足 / 已知开发默认与弱密钥黑名单。
 */
public final class RpcServiceIdentitySecretValidator {

    /** 最小密钥长度（字节字符数） */
    public static final int MIN_SECRET_LENGTH = 24;

    /**
     * 已知不安全/曾出现在源码中的默认值黑名单（大小写不敏感匹配 trim 后原文）。
     */
    private static final Set<String> FORBIDDEN_SECRETS = Set.of(
            "yudao-rpc-service-identity-dev-only",
            "change-me",
            "change-me-in-prod",
            "secret",
            "password",
            "123456",
            "test",
            "test-secret",
            "dev",
            "default"
    );

    private RpcServiceIdentitySecretValidator() {
    }

    /**
     * @return true 当 secret 可用于 privileged 通道
     */
    public static boolean isStrongSecret(String secret) {
        if (StrUtil.isBlank(secret)) {
            return false;
        }
        String trimmed = secret.trim();
        if (trimmed.length() < MIN_SECRET_LENGTH) {
            return false;
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (FORBIDDEN_SECRETS.contains(lower)) {
            return false;
        }
        // 黑名单子串：明确禁止历史默认串
        if (lower.contains("yudao-rpc-service-identity-dev-only")
                || lower.contains("dev-only")
                || lower.contains("change-me")) {
            return false;
        }
        return true;
    }

    /**
     * privileged 通道启用时：密钥必须强；否则抛出启动失败异常。
     *
     * @throws IllegalStateException 启用但密钥不合格
     */
    public static void requireStrongSecretWhenEnabled(RpcServiceIdentityProperties properties) {
        if (properties == null) {
            throw new IllegalStateException(
                    "yudao.rpc.service-identity 配置缺失：privileged 通道无法安全启用");
        }
        if (Boolean.FALSE.equals(properties.getEnabled())) {
            return; // 通道关闭，不要求 secret
        }
        if (!isStrongSecret(properties.getSecret())) {
            throw new IllegalStateException(
                    "yudao.rpc.service-identity.secret 未配置或不符合安全要求（缺失/空白/过短/已知弱密钥）。"
                            + " privileged 通道（create-by-business）已启用，拒绝启动。"
                            + " 请通过环境变量 YUDAO_RPC_SERVICE_IDENTITY_SECRET 或配置中心注入 ≥"
                            + MIN_SECRET_LENGTH + " 字符的强密钥；或设置 yudao.rpc.service-identity.enabled=false 关闭通道。");
        }
    }

    public static String requireStrongSecretOrThrow(String secret) {
        if (!isStrongSecret(secret)) {
            throw new IllegalStateException(
                    "RPC service identity secret is missing or weak (fail-closed)");
        }
        return secret.trim();
    }
}
