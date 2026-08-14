package cn.iocoder.yudao.module.system.service.mfa.model;

import cn.iocoder.yudao.module.system.service.mfa.enums.MfaLifecycleState;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaMode;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.Collections;
import java.util.Set;

/**
 * 策略缓存快照（ADR-MFA-v3 §4.3）：禁止裸 mode。
 * 含 lifecycleState / globalPolicyEpoch / tenantPolicyEpoch / mode / allowedFactors / checksum / loadedAt。
 */
@Value
@Builder
public class MfaPolicySnapshot {

    MfaLifecycleState lifecycleState;
    long globalPolicyEpoch;
    long tenantPolicyEpoch;
    long globalMinAcceptedEpoch;
    long tenantMinAcceptedEpoch;
    MfaMode mode;
    @Builder.Default
    Set<String> allowedFactors = Collections.emptySet();
    String checksum;
    Instant loadedAt;
    boolean usable;
    String unusableReason;

    /** 兼容旧测试字段名 */
    public long getPolicyVersion() {
        return globalPolicyEpoch;
    }

    public static MfaPolicySnapshot degraded(long epoch, String reason) {
        return MfaPolicySnapshot.builder()
                .lifecycleState(MfaLifecycleState.DEGRADED_CLOSED)
                .globalPolicyEpoch(epoch)
                .tenantPolicyEpoch(epoch)
                .globalMinAcceptedEpoch(epoch)
                .tenantMinAcceptedEpoch(epoch)
                .mode(null)
                .allowedFactors(Collections.emptySet())
                .checksum(null)
                .loadedAt(Instant.now())
                .usable(false)
                .unusableReason(reason)
                .build();
    }

    public static MfaPolicySnapshot uninitializedOff() {
        return MfaPolicySnapshot.builder()
                .lifecycleState(MfaLifecycleState.UNINITIALIZED)
                .globalPolicyEpoch(0L)
                .tenantPolicyEpoch(0L)
                .globalMinAcceptedEpoch(0L)
                .tenantMinAcceptedEpoch(0L)
                .mode(MfaMode.OFF)
                .allowedFactors(Collections.emptySet())
                .checksum("uninitialized-off")
                .loadedAt(Instant.now())
                .usable(true)
                .build();
    }
}
