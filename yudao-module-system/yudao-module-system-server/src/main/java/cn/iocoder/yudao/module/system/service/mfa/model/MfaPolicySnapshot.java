package cn.iocoder.yudao.module.system.service.mfa.model;

import cn.iocoder.yudao.module.system.service.mfa.enums.MfaLifecycleState;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaMode;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.Collections;
import java.util.Set;

/**
 * 策略缓存快照：必须含 lifecycleState + policyVersion + mode + allowedFactors + checksum + loadedAt。
 * <p>
 * 禁止缓存裸 mode。
 */
@Value
@Builder
public class MfaPolicySnapshot {

    MfaLifecycleState lifecycleState;
    long policyVersion;
    MfaMode mode;
    @Builder.Default
    Set<String> allowedFactors = Collections.emptySet();
    String checksum;
    Instant loadedAt;
    /** 是否为安全可读的业务策略（false 时不得签发） */
    boolean usable;
    /** 不可用原因（仅审计） */
    String unusableReason;

    public static MfaPolicySnapshot degraded(long policyVersion, String reason) {
        return MfaPolicySnapshot.builder()
                .lifecycleState(MfaLifecycleState.DEGRADED_CLOSED)
                .policyVersion(policyVersion)
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
                .policyVersion(0L)
                .mode(MfaMode.OFF)
                .allowedFactors(Collections.emptySet())
                .checksum("uninitialized-off")
                .loadedAt(Instant.now())
                .usable(true)
                .build();
    }

}
