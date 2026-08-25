package cn.iocoder.yudao.module.system.service.mfa;

import cn.iocoder.yudao.module.system.service.mfa.enums.MfaEnrollmentState;
import cn.iocoder.yudao.module.system.service.mfa.model.MfaUserAssuranceView;

/**
 * Assurance Authority 只读路径（ADR-MFA-v3 §5）。
 * <p>
 * ADMIN 缺 assurance 行时禁止签发/校验，不得按 epoch=0 猜测。
 */
public interface MfaAssuranceAuthority {

    /**
     * @return 视图；缺行返回 null（调用方 fail-closed）
     */
    MfaUserAssuranceView getAssurance(Long tenantId, Long userId);

    /**
     * ADMIN 必须存在 assurance 行，否则抛错。
     */
    MfaUserAssuranceView requireAssuranceForAdmin(Long tenantId, Long userId);

    /**
     * 切片 1：为用户确保存在 epoch=0 的 assurance 行（迁移回填语义 / 用户创建同事务预留）。
     */
    MfaUserAssuranceView ensureBootstrapRow(Long tenantId, Long userId);

    /**
     * 切片 1 写路径骨架：递增 assurance_epoch（完整事件表在切片 3/4）。
     */
    long bumpAssuranceEpoch(Long tenantId, Long userId, MfaEnrollmentState newEnrollmentState, Boolean enabled);
}
