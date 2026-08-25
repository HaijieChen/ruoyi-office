package cn.iocoder.yudao.module.system.service.mfa;

import cn.iocoder.yudao.module.system.dal.dataobject.mfa.MfaControlStateDO;
import cn.iocoder.yudao.module.system.dal.dataobject.mfa.MfaTenantPolicyDO;
import cn.iocoder.yudao.module.system.dal.dataobject.mfa.MfaUserAssuranceDO;

import java.util.List;

/**
 * ADR-MFA-v3 权威存储抽象（切片 1：control / tenant / assurance）。
 */
public interface MfaAuthorityStore {

    MfaControlStateDO getControlState();

    void saveControlState(MfaControlStateDO state);

    MfaTenantPolicyDO getTenantPolicy(Long tenantId);

    void saveTenantPolicy(MfaTenantPolicyDO policy);

    List<MfaTenantPolicyDO> listConfirmedTenantPolicies();

    MfaUserAssuranceDO getUserAssurance(Long tenantId, Long userId);

    void saveUserAssurance(MfaUserAssuranceDO assurance);

    default boolean isForceReadFailure() {
        return false;
    }
}
