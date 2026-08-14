package cn.iocoder.yudao.module.system.service.mfa.model;

import cn.iocoder.yudao.module.system.service.mfa.enums.MfaLifecycleState;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaMode;
import lombok.Builder;
import lombok.Value;

/**
 * 主库权威 control tuple（ADR-MFA-v3 §4.3）：每次签发/refresh/校验必须强一致读取。
 */
@Value
@Builder
public class MfaControlTuple {

    MfaLifecycleState lifecycleState;
    MfaMode globalMode;
    long globalPolicyEpoch;
    long globalMinAcceptedEpoch;
    String checksum;
    boolean usable;
    String unusableReason;

    public static MfaControlTuple degraded(long epoch, String reason) {
        return MfaControlTuple.builder()
                .lifecycleState(MfaLifecycleState.DEGRADED_CLOSED)
                .globalMode(null)
                .globalPolicyEpoch(epoch)
                .globalMinAcceptedEpoch(epoch)
                .checksum(null)
                .usable(false)
                .unusableReason(reason)
                .build();
    }

    public static MfaControlTuple uninitializedOff() {
        return MfaControlTuple.builder()
                .lifecycleState(MfaLifecycleState.UNINITIALIZED)
                .globalMode(MfaMode.OFF)
                .globalPolicyEpoch(0L)
                .globalMinAcceptedEpoch(0L)
                .checksum("uninitialized-off")
                .usable(true)
                .build();
    }
}
