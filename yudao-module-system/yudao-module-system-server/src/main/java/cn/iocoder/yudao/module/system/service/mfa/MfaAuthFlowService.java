package cn.iocoder.yudao.module.system.service.mfa;

import cn.iocoder.yudao.module.system.service.mfa.enums.MfaFlowTokenClass;
import cn.iocoder.yudao.module.system.service.mfa.model.MfaAuthFlowRecord;
import cn.iocoder.yudao.module.system.service.mfa.model.MfaIssuedFlow;
import cn.iocoder.yudao.module.system.service.mfa.model.MfaPolicySnapshot;

import java.util.List;

/**
 * PRE_AUTH / ENROLLMENT / RECOVERY 流程权威（ADR-MFA-v3 §6–§7，切片 2）。
 * <p>
 * 原始 flowToken 仅签发时返回；存储与解析一律用 SHA-256 摘要。
 * 一次性消费：ACTIVE → COMPLETED（CAS）。
 */
public interface MfaAuthFlowService {

    MfaIssuedFlow issue(MfaFlowTokenClass tokenClass, Long userId, Long tenantId, String clientId,
                        MfaPolicySnapshot policy, long assuranceEpoch,
                        List<String> allowedActions, List<String> allowedFactorIds, int ttlSeconds);

    /**
     * 解析 ACTIVE 且未过期的 flow；过期自动 EXPIRED；找不到/已完成返回 null。
     */
    MfaAuthFlowRecord resolveActive(String rawFlowToken);

    /**
     * 一次性消费 ACTIVE → COMPLETED；失败返回 false（已消费/过期/不存在）。
     */
    boolean tryComplete(String rawFlowToken);

    /**
     * 补偿：COMPLETED → ACTIVE（仅未过期）。
     */
    boolean tryRevertComplete(String rawFlowToken);

    void revoke(String rawFlowToken);

    void clear();
}
