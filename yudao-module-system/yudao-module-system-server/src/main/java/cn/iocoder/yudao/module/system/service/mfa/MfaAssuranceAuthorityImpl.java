package cn.iocoder.yudao.module.system.service.mfa;

import cn.iocoder.yudao.module.system.dal.dataobject.mfa.MfaUserAssuranceDO;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaEnrollmentState;
import cn.iocoder.yudao.module.system.service.mfa.model.MfaUserAssuranceView;
import cn.iocoder.yudao.module.system.service.mfa.support.MfaLockOrder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.MFA_POLICY_UNAVAILABLE;

/**
 * Assurance Authority（ADR-MFA-v3 §5 切片 1）。
 * <p>
 * F-S1-04：损坏行 fail-closed，不得猜 epoch=0 / NONE。
 */
@Service
public class MfaAssuranceAuthorityImpl implements MfaAssuranceAuthority {

    private final MfaAuthorityStore store;

    public MfaAssuranceAuthorityImpl(MfaAuthorityStore store) {
        this.store = store;
    }

    @Override
    public MfaUserAssuranceView getAssurance(Long tenantId, Long userId) {
        MfaLockOrder.requireValidOrder(List.of(
                MfaLockOrder.Resource.GLOBAL_POLICY,
                MfaLockOrder.Resource.TENANT_POLICY,
                MfaLockOrder.Resource.USER_ASSURANCE));
        MfaUserAssuranceDO row = store.getUserAssurance(tenantId, userId);
        if (row == null) {
            return null;
        }
        return toViewStrict(row);
    }

    @Override
    public MfaUserAssuranceView requireAssuranceForAdmin(Long tenantId, Long userId) {
        MfaUserAssuranceView view = getAssurance(tenantId, userId);
        if (view == null) {
            throw exception(MFA_POLICY_UNAVAILABLE);
        }
        return view;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MfaUserAssuranceView ensureBootstrapRow(Long tenantId, Long userId) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(userId, "userId");
        MfaUserAssuranceDO existing = store.getUserAssurance(tenantId, userId);
        if (existing != null) {
            // 已存在则严格解析；损坏不得 bootstrap 掩盖
            return toViewStrict(existing);
        }
        MfaUserAssuranceDO row = MfaUserAssuranceDO.builder()
                .tenantId(tenantId)
                .userId(userId)
                .enabled(false)
                .enrollmentState(MfaEnrollmentState.NONE.name())
                .assuranceEpoch(0L)
                .build();
        store.saveUserAssurance(row);
        return toViewStrict(row);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public long bumpAssuranceEpoch(Long tenantId, Long userId,
                                   MfaEnrollmentState newEnrollmentState, Boolean enabled) {
        MfaLockOrder.requireValidOrder(List.of(
                MfaLockOrder.Resource.GLOBAL_POLICY,
                MfaLockOrder.Resource.TENANT_POLICY,
                MfaLockOrder.Resource.USER_ASSURANCE));
        MfaUserAssuranceDO row = store.getUserAssurance(tenantId, userId);
        if (row == null) {
            throw exception(MFA_POLICY_UNAVAILABLE);
        }
        // 先严格校验当前行
        toViewStrict(row);
        long current = row.getAssuranceEpoch();
        if (current == Long.MAX_VALUE) {
            throw exception(MFA_POLICY_UNAVAILABLE);
        }
        long next = current + 1;
        row.setAssuranceEpoch(next);
        if (newEnrollmentState != null) {
            row.setEnrollmentState(newEnrollmentState.name());
        }
        if (enabled != null) {
            row.setEnabled(enabled);
        }
        store.saveUserAssurance(row);
        return next;
    }

    /**
     * 严格视图：未知 enrollment / null 或负 epoch / 缺主键 → fail-closed。
     */
    private static MfaUserAssuranceView toViewStrict(MfaUserAssuranceDO row) {
        if (row.getTenantId() == null || row.getUserId() == null) {
            throw exception(MFA_POLICY_UNAVAILABLE);
        }
        MfaEnrollmentState es = MfaEnrollmentState.parseStrict(row.getEnrollmentState());
        if (es == null) {
            throw exception(MFA_POLICY_UNAVAILABLE);
        }
        if (row.getAssuranceEpoch() == null || row.getAssuranceEpoch() < 0) {
            throw exception(MFA_POLICY_UNAVAILABLE);
        }
        return MfaUserAssuranceView.builder()
                .tenantId(row.getTenantId())
                .userId(row.getUserId())
                .enabled(Boolean.TRUE.equals(row.getEnabled()))
                .enrollmentState(es)
                .preferredFactorId(row.getPreferredFactorId())
                .assuranceEpoch(row.getAssuranceEpoch())
                .build();
    }
}
