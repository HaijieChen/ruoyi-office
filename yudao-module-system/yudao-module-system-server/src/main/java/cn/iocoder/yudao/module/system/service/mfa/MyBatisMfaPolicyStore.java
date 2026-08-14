package cn.iocoder.yudao.module.system.service.mfa;

import cn.iocoder.yudao.module.system.dal.dataobject.mfa.MfaControlStateDO;
import cn.iocoder.yudao.module.system.dal.dataobject.mfa.MfaGlobalPolicyDO;
import cn.iocoder.yudao.module.system.dal.dataobject.mfa.MfaTenantPolicyDO;
import cn.iocoder.yudao.module.system.dal.mysql.mfa.MfaControlStateMapper;
import cn.iocoder.yudao.module.system.dal.mysql.mfa.MfaGlobalPolicyMapper;
import cn.iocoder.yudao.module.system.dal.mysql.mfa.MfaTenantPolicyMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

/**
 * 主库强一致策略存储。
 */
@Repository
public class MyBatisMfaPolicyStore implements MfaPolicyStore {

    @Resource
    private MfaControlStateMapper controlStateMapper;
    @Resource
    private MfaGlobalPolicyMapper globalPolicyMapper;
    @Resource
    private MfaTenantPolicyMapper tenantPolicyMapper;

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
    public MfaGlobalPolicyDO getGlobalPolicy() {
        return globalPolicyMapper.selectSingleton();
    }

    @Override
    public void saveGlobalPolicy(MfaGlobalPolicyDO policy) {
        if (globalPolicyMapper.selectById(policy.getId()) == null) {
            globalPolicyMapper.insert(policy);
        } else {
            globalPolicyMapper.updateById(policy);
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
    public List<ConfirmedPolicyProbe> scanConfirmedPolicies() {
        List<ConfirmedPolicyProbe> probes = new ArrayList<>();
        MfaGlobalPolicyDO global = globalPolicyMapper.selectSingleton();
        if (global != null && Boolean.TRUE.equals(global.getConfirmed())) {
            probes.add(new ConfirmedPolicyProbe("global", global.getMode(),
                    global.getPolicyVersion(), global.getChecksum(), true));
        }
        List<MfaTenantPolicyDO> tenants = tenantPolicyMapper.selectAllConfirmed();
        if (tenants != null) {
            for (MfaTenantPolicyDO t : tenants) {
                probes.add(new ConfirmedPolicyProbe("tenant:" + t.getTenantId(), t.getMode(),
                        t.getPolicyVersion(), t.getChecksum(), true));
            }
        }
        return probes;
    }

}
