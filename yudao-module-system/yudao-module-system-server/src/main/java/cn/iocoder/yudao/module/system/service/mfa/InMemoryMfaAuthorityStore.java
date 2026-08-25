package cn.iocoder.yudao.module.system.service.mfa;

import cn.iocoder.yudao.module.system.dal.dataobject.mfa.MfaControlStateDO;
import cn.iocoder.yudao.module.system.dal.dataobject.mfa.MfaTenantPolicyDO;
import cn.iocoder.yudao.module.system.dal.dataobject.mfa.MfaUserAssuranceDO;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 切片 1 单测用内存权威存储。
 */
public class InMemoryMfaAuthorityStore implements MfaAuthorityStore {

    private volatile MfaControlStateDO controlState;
    private final Map<Long, MfaTenantPolicyDO> tenants = new ConcurrentHashMap<>();
    private final Map<String, MfaUserAssuranceDO> assurances = new ConcurrentHashMap<>();
    private final AtomicBoolean forceReadFailure = new AtomicBoolean(false);

    public void setForceReadFailure(boolean v) {
        forceReadFailure.set(v);
    }

    @Override
    public boolean isForceReadFailure() {
        return forceReadFailure.get();
    }

    private void checkRead() {
        if (forceReadFailure.get()) {
            throw new IllegalStateException("simulated DB timeout");
        }
    }

    @Override
    public MfaControlStateDO getControlState() {
        checkRead();
        return controlState;
    }

    @Override
    public void saveControlState(MfaControlStateDO state) {
        this.controlState = state;
    }

    @Override
    public MfaTenantPolicyDO getTenantPolicy(Long tenantId) {
        checkRead();
        return tenants.get(tenantId);
    }

    @Override
    public void saveTenantPolicy(MfaTenantPolicyDO policy) {
        tenants.put(policy.getTenantId(), policy);
    }

    @Override
    public List<MfaTenantPolicyDO> listConfirmedTenantPolicies() {
        checkRead();
        List<MfaTenantPolicyDO> list = new ArrayList<>();
        for (MfaTenantPolicyDO t : tenants.values()) {
            if (Boolean.TRUE.equals(t.getConfirmed())) {
                list.add(t);
            }
        }
        return list;
    }

    @Override
    public MfaUserAssuranceDO getUserAssurance(Long tenantId, Long userId) {
        checkRead();
        return assurances.get(key(tenantId, userId));
    }

    @Override
    public void saveUserAssurance(MfaUserAssuranceDO assurance) {
        assurances.put(key(assurance.getTenantId(), assurance.getUserId()), assurance);
    }

    public void clear() {
        controlState = null;
        tenants.clear();
        assurances.clear();
        forceReadFailure.set(false);
    }

    public void corruptGlobalMode(String illegal) {
        if (controlState != null) {
            controlState.setGlobalMode(illegal);
        }
    }

    public void removeControlState() {
        controlState = null;
    }

    private static String key(Long tenantId, Long userId) {
        return tenantId + ":" + userId;
    }
}
