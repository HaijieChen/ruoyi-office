package cn.iocoder.yudao.module.system.service.mfa.model;

import cn.iocoder.yudao.module.system.service.mfa.enums.MfaEnrollmentState;
import lombok.Builder;
import lombok.Value;

/**
 * 用户 assurance 只读视图（权威行存在时）。
 */
@Value
@Builder
public class MfaUserAssuranceView {

    Long tenantId;
    Long userId;
    boolean enabled;
    MfaEnrollmentState enrollmentState;
    Long preferredFactorId;
    long assuranceEpoch;

}
