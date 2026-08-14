package cn.iocoder.yudao.module.system.service.mfa;

import cn.iocoder.yudao.module.system.service.mfa.support.MfaChallengeHandleStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class MfaConfiguration {

    @Bean
    @ConditionalOnMissingBean(MfaUserFactorProbe.class)
    public MfaUserFactorProbe mfaUserFactorProbe() {
        return new MfaUserFactorProbe.Noop();
    }

    @Bean
    @ConditionalOnMissingBean(MfaChallengeHandleStore.class)
    public MfaChallengeHandleStore mfaChallengeHandleStore() {
        return new MfaChallengeHandleStore();
    }

}
