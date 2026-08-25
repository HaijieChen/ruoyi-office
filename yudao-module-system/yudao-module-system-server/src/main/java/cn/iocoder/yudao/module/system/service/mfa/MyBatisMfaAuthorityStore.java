package cn.iocoder.yudao.module.system.service.mfa;

import cn.iocoder.yudao.module.system.dal.dataobject.mfa.MfaControlStateDO;
import cn.iocoder.yudao.module.system.dal.dataobject.mfa.MfaTenantPolicyDO;
import cn.iocoder.yudao.module.system.dal.dataobject.mfa.MfaUserAssuranceDO;
import cn.iocoder.yudao.module.system.dal.mysql.mfa.MfaControlStateMapper;
import cn.iocoder.yudao.module.system.dal.mysql.mfa.MfaTenantPolicyMapper;
import cn.iocoder.yudao.module.system.dal.mysql.mfa.MfaUserAssuranceMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 主库权威存储（ADR-MFA-v3 切片 1）。
 */
@Repository
public class MyBatisMfaAuthorityStore implements MfaAuthorityStore {

    @Resource
    private MfaControlStateMapper controlStateMapper;
    @Resource
    private MfaTenantPolicyMapper tenantPolicyMapper;
    @Resource
    private MfaUserAssuranceMapper userAssuranceMapper;

    @Override
    public MfaControlStateDO getControlState() {
        return controlStateMapper.selectSingleton();
    }

    @Override
    public void saveControlState(MfaControlStateDO state) {
        if (controlStateMapper.selectById(state.getId()) == null) {
            controlStateMapper.insert(state);
        } else {
            controlStateMapper.updateById(state);
        }
    }

    @Override
    public MfaTenantPolicyDO getTenantPolicy(Long tenantId) {
        return tenantPolicyMapper.selectByTenantId(tenantId);
    }

    @Override
    public void saveTenantPolicy(MfaTenantPolicyDO policy) {
        MfaTenantPolicyDO existing = tenantPolicyMapper.selectByTenantId(policy.getTenantId());
        if (existing == null) {
            tenantPolicyMapper.insert(policy);
        } else {
            policy.setId(existing.getId());
            tenantPolicyMapper.updateById(policy);
        }
    }

    @Override
    public List<MfaTenantPolicyDO> listConfirmedTenantPolicies() {
        return tenantPolicyMapper.selectAllConfirmed();
    }

    @Override
    public MfaUserAssuranceDO getUserAssurance(Long tenantId, Long userId) {
        return userAssuranceMapper.selectByTenantAndUser(tenantId, userId);
    }

    @Override
    public void saveUserAssurance(MfaUserAssuranceDO assurance) {
        MfaUserAssuranceDO existing = userAssuranceMapper.selectByTenantAndUser(
                assurance.getTenantId(), assurance.getUserId());
        if (existing == null) {
            userAssuranceMapper.insert(assurance);
        } else {
            assurance.setId(existing.getId());
            userAssuranceMapper.updateById(assurance);
        }
    }
}
