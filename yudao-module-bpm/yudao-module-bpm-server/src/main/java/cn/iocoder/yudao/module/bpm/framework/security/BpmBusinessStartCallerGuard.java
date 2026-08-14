package cn.iocoder.yudao.module.bpm.framework.security;

import cn.iocoder.yudao.framework.security.config.RpcServiceIdentityProperties;
import org.springframework.stereotype.Component;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.PROCESS_INSTANCE_BUSINESS_START_CALLER_FORBIDDEN;

/**
 * EXP-87 G1：callTrusted 前强制校验 Finance 服务身份（服务层纵深防御）。
 */
@Component
public class BpmBusinessStartCallerGuard {

    private final RpcServiceIdentityProperties properties;

    public BpmBusinessStartCallerGuard(RpcServiceIdentityProperties properties) {
        this.properties = properties;
    }

    public void requireVerifiedFinanceCaller() {
        if (!BpmBusinessStartCallerIdentity.isVerifiedFinanceCaller(properties)) {
            throw exception(PROCESS_INSTANCE_BUSINESS_START_CALLER_FORBIDDEN);
        }
    }
}
