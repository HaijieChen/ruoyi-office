package cn.iocoder.yudao.module.system.service.mfa;

import cn.iocoder.yudao.module.system.service.mfa.delivery.MfaChallengeDelivery;
import cn.iocoder.yudao.module.system.service.mfa.delivery.StubMfaChallengeDelivery;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MFA 装配：默认可测桩投递；探测实现见 {@link MfaUserFactorProbeImpl}。
 */
@Configuration(proxyBeanMethods = false)
public class MfaConfiguration {

    @Bean
    @ConditionalOnMissingBean(MfaChallengeDelivery.class)
    public MfaChallengeDelivery mfaChallengeDelivery() {
        return new StubMfaChallengeDelivery();
    }
}
