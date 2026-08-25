package cn.iocoder.yudao.module.system.service.mfa;

import cn.iocoder.yudao.module.system.service.mfa.enums.MfaMode;
import cn.iocoder.yudao.module.system.service.mfa.model.MfaControlTuple;
import cn.iocoder.yudao.module.system.service.mfa.model.MfaPolicySnapshot;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * 兼容适配器 → {@link MfaPolicyAuthority}。
 */
@Service
public class MfaPolicyControlServiceImpl implements MfaPolicyControlService {

    private final MfaPolicyAuthority policyAuthority;

    public MfaPolicyControlServiceImpl(MfaPolicyAuthority policyAuthority) {
        this.policyAuthority = policyAuthority;
    }

    @Override
    public MfaPolicySnapshot resolveEffectivePolicy(Long tenantId) {
        return policyAuthority.resolveEffectivePolicy(tenantId);
    }

    @Override
    public long readPrimaryPolicyVersionGate() {
        MfaControlTuple t = policyAuthority.readControlTuple();
        return t.getGlobalPolicyEpoch();
    }

    @Override
    public long confirmGlobalPolicy(MfaMode mode, Set<String> allowedFactors) {
        return policyAuthority.confirmGlobalPolicy(mode, allowedFactors);
    }

    @Override
    public long confirmTenantPolicy(Long tenantId, MfaMode mode, Set<String> allowedFactors) {
        return policyAuthority.confirmTenantPolicy(tenantId, mode, allowedFactors);
    }

    @Override
    public boolean isReady() {
        return policyAuthority.isReady();
    }

    @Override
    public void invalidateCache() {
        policyAuthority.invalidateCache();
    }
}
