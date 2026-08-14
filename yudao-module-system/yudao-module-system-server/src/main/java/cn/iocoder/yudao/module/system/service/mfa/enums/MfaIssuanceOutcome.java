package cn.iocoder.yudao.module.system.service.mfa.enums;

/**
 * 用户态 Token 签发决策结果（ADR-MFA-v2 §1–§2）。
 */
public enum MfaIssuanceOutcome {

    /** 条件满足，可由 Facade 签发 access/refresh */
    ALLOWED,
    /** 仅返回 MFA/绑定挑战，零 access/refresh */
    CHALLENGE,
    /** 安全条件不满足或协议不可交互，零 Token */
    REJECT,
    /** 非后台用户态 MFA 范围（如 client_credentials / MEMBER） */
    NOT_APPLICABLE

}
