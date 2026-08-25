package cn.iocoder.yudao.module.system.service.mfa.enums;

/**
 * MFA 控制面生命周期状态（ADR-MFA-v2 §4）。
 * <p>
 * 禁止 {@link #ARMED} → {@link #UNINITIALIZED}；解析失败不得回落为业务 OFF。
 */
public enum MfaLifecycleState {

    /**
     * 尚未出现任何已确认非 OFF 策略：仅可读为 OFF。
     */
    UNINITIALIZED,

    /**
     * 已确认过非 OFF；即使之后显式 OFF 仍保持 ARMED。
     */
    ARMED,

    /**
     * ARMED 后策略不可安全解析：拒绝用户态 Token 与 refresh。
     */
    DEGRADED_CLOSED;

}
