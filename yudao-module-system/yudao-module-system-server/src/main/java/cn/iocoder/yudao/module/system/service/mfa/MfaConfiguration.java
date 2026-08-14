package cn.iocoder.yudao.module.system.service.mfa;

import cn.iocoder.yudao.module.system.service.mfa.crypto.FailClosedMfaSecretCipher;
import cn.iocoder.yudao.module.system.service.mfa.crypto.MfaSecretCipher;
import cn.iocoder.yudao.module.system.service.mfa.crypto.MfaSecretCipherImpl;
import cn.iocoder.yudao.module.system.service.mfa.crypto.MfaSecretProperties;
import cn.iocoder.yudao.module.system.service.mfa.delivery.MfaChallengeDelivery;
import cn.iocoder.yudao.module.system.service.mfa.delivery.StubMfaChallengeDelivery;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MFA 装配：默认桩投递 + 受控密钥 cipher（缺密钥 fail-closed）。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MfaSecretProperties.class)
public class MfaConfiguration {

    @Bean
    @ConditionalOnMissingBean(MfaChallengeDelivery.class)
    public MfaChallengeDelivery mfaChallengeDelivery() {
        return new StubMfaChallengeDelivery();
    }

    @Bean
    @ConditionalOnMissingBean(MfaSecretCipher.class)
    public MfaSecretCipher mfaSecretCipher(MfaSecretProperties properties) {
        if (properties.getActiveKeyId() == null || properties.getKeys() == null
                || properties.getKeys().isEmpty()) {
            return FailClosedMfaSecretCipher.INSTANCE;
        }
        return MfaSecretCipherImpl.fromProperties(properties);
    }
}
