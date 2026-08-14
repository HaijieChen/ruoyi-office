package cn.iocoder.yudao.framework.security.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EXP-87 F1：密钥 fail-closed 校验（无源码默认可运行密钥）。
 */
class RpcServiceIdentitySecretValidatorTest {

    @Test
    void rejectsNullEmptyBlank() {
        assertFalse(RpcServiceIdentitySecretValidator.isStrongSecret(null));
        assertFalse(RpcServiceIdentitySecretValidator.isStrongSecret(""));
        assertFalse(RpcServiceIdentitySecretValidator.isStrongSecret("   "));
    }

    @Test
    void rejectsKnownDevDefaultAndBlacklist() {
        assertFalse(RpcServiceIdentitySecretValidator.isStrongSecret(
                "yudao-rpc-service-identity-dev-only"));
        assertFalse(RpcServiceIdentitySecretValidator.isStrongSecret(
                "YUDAO-RPC-SERVICE-IDENTITY-DEV-ONLY"));
        assertFalse(RpcServiceIdentitySecretValidator.isStrongSecret("change-me"));
        assertFalse(RpcServiceIdentitySecretValidator.isStrongSecret("change-me-in-prod"));
        assertFalse(RpcServiceIdentitySecretValidator.isStrongSecret("test-secret"));
        assertFalse(RpcServiceIdentitySecretValidator.isStrongSecret("secret"));
    }

    @Test
    void rejectsTooShort() {
        assertFalse(RpcServiceIdentitySecretValidator.isStrongSecret("short-but-not-24b"));
        assertEquals(17, "short-but-not-24b".length());
    }

    @Test
    void acceptsStrongInjectedSecret() {
        String strong = "prod-injected-rpc-service-identity-key-9f3a";
        assertTrue(strong.length() >= RpcServiceIdentitySecretValidator.MIN_SECRET_LENGTH);
        assertTrue(RpcServiceIdentitySecretValidator.isStrongSecret(strong));
    }

    @Test
    void requireWhenEnabled_missingSecret_throws() {
        RpcServiceIdentityProperties p = new RpcServiceIdentityProperties();
        p.setEnabled(true);
        p.setSecret(null);
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> RpcServiceIdentitySecretValidator.requireStrongSecretWhenEnabled(p));
        assertTrue(ex.getMessage().contains("secret") || ex.getMessage().contains("拒绝启动"));
    }

    @Test
    void requireWhenEnabled_blacklistedDefault_throws() {
        RpcServiceIdentityProperties p = new RpcServiceIdentityProperties();
        p.setEnabled(true);
        p.setSecret("yudao-rpc-service-identity-dev-only");
        assertThrows(IllegalStateException.class,
                () -> RpcServiceIdentitySecretValidator.requireStrongSecretWhenEnabled(p));
    }

    @Test
    void requireWhenEnabled_blank_throws() {
        RpcServiceIdentityProperties p = new RpcServiceIdentityProperties();
        p.setEnabled(true);
        p.setSecret("   ");
        assertThrows(IllegalStateException.class,
                () -> RpcServiceIdentitySecretValidator.requireStrongSecretWhenEnabled(p));
    }

    @Test
    void requireWhenDisabled_allowsMissingSecret() {
        RpcServiceIdentityProperties p = new RpcServiceIdentityProperties();
        p.setEnabled(false);
        p.setSecret(null);
        assertDoesNotThrow(() -> RpcServiceIdentitySecretValidator.requireStrongSecretWhenEnabled(p));
    }

    @Test
    void requireWhenEnabled_strongSecret_ok() {
        RpcServiceIdentityProperties p = new RpcServiceIdentityProperties();
        p.setEnabled(true);
        p.setSecret("prod-injected-rpc-service-identity-key-9f3a");
        assertDoesNotThrow(() -> RpcServiceIdentitySecretValidator.requireStrongSecretWhenEnabled(p));
    }

    @Test
    void propertiesHasNoRunnableDefaultSecret() {
        RpcServiceIdentityProperties p = new RpcServiceIdentityProperties();
        assertNull(p.getSecret(), "源码不得内置可运行默认 secret");
        assertFalse(RpcServiceIdentitySecretValidator.isStrongSecret(p.getSecret()));
    }

    @Test
    void bootstrapValidator_failsOnWeakSecret() {
        RpcServiceIdentityProperties p = new RpcServiceIdentityProperties();
        p.setEnabled(true);
        p.setSecret("yudao-rpc-service-identity-dev-only");
        RpcServiceIdentityBootstrapValidator bootstrap = new RpcServiceIdentityBootstrapValidator(p);
        assertThrows(IllegalStateException.class, bootstrap::validateOnStartup);
    }

    @Test
    void bootstrapValidator_okOnStrongSecret() {
        RpcServiceIdentityProperties p = new RpcServiceIdentityProperties();
        p.setEnabled(true);
        p.setSecret("prod-injected-rpc-service-identity-key-9f3a");
        RpcServiceIdentityBootstrapValidator bootstrap = new RpcServiceIdentityBootstrapValidator(p);
        assertDoesNotThrow(bootstrap::validateOnStartup);
    }
}
