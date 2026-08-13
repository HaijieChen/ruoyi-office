package cn.iocoder.yudao.framework.security.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * EXP-87 F1：应用启动时校验 RPC 服务身份密钥（fail-closed）。
 * <p>
 * 当 {@code yudao.rpc.service-identity.enabled=true}（默认）且 secret 不合格时阻止启动。
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "yudao.rpc.service-identity", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class RpcServiceIdentityBootstrapValidator {

    private final RpcServiceIdentityProperties properties;

    @PostConstruct
    public void validateOnStartup() {
        RpcServiceIdentitySecretValidator.requireStrongSecretWhenEnabled(properties);
        log.info("[RpcServiceIdentity] privileged service-identity channel enabled; secret strength OK");
    }
}
