package cn.iocoder.yudao.module.system.service.mfa;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MfaTotpCompatibilityTest {

    @Test
    void generatedSecret_isAuthenticatorBase32() {
        MfaFactorServiceImpl service = new MfaFactorServiceImpl(null);
        var pending = service.startPendingTotp(1L, 1L, "admin");
        assertTrue(pending.getSecretManual().matches("[A-Z2-7]+"), pending.getSecretManual());
        assertTrue(pending.getOtpauthUri().startsWith("otpauth://totp/"));
        assertTrue(pending.getOtpauthUri().contains("secret=" + pending.getSecretManual()));
        assertTrue(!pending.getOtpauthUri().contains("_"), pending.getOtpauthUri());
    }

    @Test
    void hotp_usesDecodedBase32Key() throws Exception {
        Method hotp = MfaFactorServiceImpl.class.getDeclaredMethod("hotp", String.class, long.class);
        hotp.setAccessible(true);
        String secret = "JBSWY3DPEHPK3PXP";
        String code = (String) hotp.invoke(null, secret, 1L);
        assertEquals(6, code.length());
        assertEquals(code, hotp.invoke(null, secret, 1L));
    }
}
