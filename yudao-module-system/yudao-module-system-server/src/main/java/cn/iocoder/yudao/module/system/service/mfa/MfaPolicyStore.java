package cn.iocoder.yudao.module.system.service.mfa;

import cn.iocoder.yudao.module.system.dal.dataobject.mfa.MfaControlStateDO;
import cn.iocoder.yudao.module.system.dal.dataobject.mfa.MfaGlobalPolicyDO;
import cn.iocoder.yudao.module.system.dal.dataobject.mfa.MfaTenantPolicyDO;

import java.util.List;

/**
 * MFA 策略与控制面持久化抽象（便于单测与主库强一致实现切换）。
 */
public interface MfaPolicyStore {

    MfaControlStateDO getControlState();

    void saveControlState(MfaControlStateDO state);

    MfaGlobalPolicyDO getGlobalPolicy();

    void saveGlobalPolicy(MfaGlobalPolicyDO policy);

    MfaTenantPolicyDO getTenantPolicy(Long tenantId);

    void saveTenantPolicy(MfaTenantPolicyDO policy);

    /**
     * 扫描所有已确认且 mode 解析为非 OFF 的策略（全局 + 租户）。
     * 用于 UNINITIALIZED 判定；返回 null mode 表示非法，调用方应 ARMED/closed。
     */
    List<ConfirmedPolicyProbe> scanConfirmedPolicies();

    /**
     * 模拟读取失败（仅测试桩使用）；生产实现始终 false。
     */
    default boolean isForceReadFailure() {
        return false;
    }

    record ConfirmedPolicyProbe(String scope, String mode, Long policyVersion, String checksum, boolean confirmed) {
    }

}
