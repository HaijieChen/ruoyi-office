package cn.iocoder.yudao.module.system.service.mfa.store;

import cn.iocoder.yudao.module.system.service.mfa.model.MfaFactorBinding;

import java.util.List;

/**
 * 因子可切换存储（切片 4）：PENDING→ACTIVE CAS、last_used_step CAS。
 */
public interface MfaFactorStore {

    void save(Long tenantId, Long userId, MfaFactorBinding binding);

    MfaFactorBinding get(Long tenantId, Long userId, String factorId);

    List<MfaFactorBinding> listByUser(Long tenantId, Long userId);

    /**
     * 状态 CAS；可选写入 lastUsedStep（仅成功时）。
     */
    boolean casStatus(Long tenantId, Long userId, String factorId,
                      String fromStatus, String toStatus, Long lastUsedStep);

    /**
     * TOTP 步数占用：仅当 last_used_step 为空或严格小于 step 时成功。
     */
    boolean claimTotpStep(Long tenantId, Long userId, String factorId, long step);

    void clear();
}
