package cn.iocoder.yudao.module.system.service.mfa;

import cn.iocoder.yudao.module.system.dal.dataobject.mfa.MfaControlStateDO;
import cn.iocoder.yudao.module.system.dal.dataobject.mfa.MfaGlobalPolicyDO;
import cn.iocoder.yudao.module.system.dal.dataobject.mfa.MfaTenantPolicyDO;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaLifecycleState;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 单测与本地无 DB 场景的内存实现。
 */
public class InMemoryMfaPolicyStore implements MfaPolicyStore {

    private volatile MfaControlStateDO controlState;
    private volatile MfaGlobalPolicyDO globalPolicy;
    private final Map<Long, MfaTenantPolicyDO> tenantPolicies = new ConcurrentHashMap<>();
    private final AtomicBoolean forceReadFailure = new AtomicBoolean(false);

    public InMemoryMfaPolicyStore() {
        // 空库：无 control state 行
    }

    public void setForceReadFailure(boolean value) {
        forceReadFailure.set(value);
    }

    @Override
    public boolean isForceReadFailure() {
        return forceReadFailure.get();
    }

    @Override
    public MfaControlStateDO getControlState() {
        if (forceReadFailure.get()) {
            throw new IllegalStateException("simulated DB timeout");
        }
        return controlState;
    }

    @Override
    public void saveControlState(MfaControlStateDO state) {
        this.controlState = state;
    }

    @Override
    public MfaGlobalPolicyDO getGlobalPolicy() {
        if (forceReadFailure.get()) {
            throw new IllegalStateException("simulated DB timeout");
        }
        return globalPolicy;
    }

    @Override
    public void saveGlobalPolicy(MfaGlobalPolicyDO policy) {
        this.globalPolicy = policy;
    }

    @Override
    public MfaTenantPolicyDO getTenantPolicy(Long tenantId) {
        if (forceReadFailure.get()) {
            throw new IllegalStateException("simulated DB timeout");
        }
        return tenantPolicies.get(tenantId);
    }

    @Override
    public void saveTenantPolicy(MfaTenantPolicyDO policy) {
        tenantPolicies.put(policy.getTenantId(), policy);
    }

    @Override
    public List<ConfirmedPolicyProbe> scanConfirmedPolicies() {
        if (forceReadFailure.get()) {
            throw new IllegalStateException("simulated DB timeout");
        }
        List<ConfirmedPolicyProbe> probes = new ArrayList<>();
        if (globalPolicy != null && Boolean.TRUE.equals(globalPolicy.getConfirmed())) {
            probes.add(new ConfirmedPolicyProbe("global", globalPolicy.getMode(),
                    globalPolicy.getPolicyVersion(), globalPolicy.getChecksum(), true));
        }
        for (MfaTenantPolicyDO t : tenantPolicies.values()) {
            if (Boolean.TRUE.equals(t.getConfirmed())) {
                probes.add(new ConfirmedPolicyProbe("tenant:" + t.getTenantId(), t.getMode(),
                        t.getPolicyVersion(), t.getChecksum(), true));
            }
        }
        return probes;
    }

    public void clear() {
        controlState = null;
        globalPolicy = null;
        tenantPolicies.clear();
        forceReadFailure.set(false);
    }

    public void corruptGlobalMode(String illegalMode) {
        if (globalPolicy != null) {
            globalPolicy.setMode(illegalMode);
        }
    }

    public void removeGlobalPolicyRow() {
        globalPolicy = null;
    }

    public void ensureUninitializedControlState() {
        controlState = MfaControlStateDO.builder()
                .id(MfaControlStateDO.SINGLETON_ID)
                .lifecycleState(MfaLifecycleState.UNINITIALIZED.name())
                .policyVersion(0L)
                .checksum("init")
                .build();
    }

    public static boolean isNonOffMode(String mode) {
        MfaMode parsed = MfaMode.parseStrict(mode);
        return parsed != null && parsed.isNonOff();
    }

}
