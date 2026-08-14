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
        return toView(row);
    }

    @Override
    public MfaUserAssuranceView requireAssuranceForAdmin(Long tenantId, Long userId) {
        MfaUserAssuranceView view = getAssurance(tenantId, userId);
        if (view == null) {
            // 缺行不得按 epoch=0 猜测
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
            return toView(existing);
        }
        MfaUserAssuranceDO row = MfaUserAssuranceDO.builder()
                .tenantId(tenantId)
                .userId(userId)
                .enabled(false)
                .enrollmentState(MfaEnrollmentState.NONE.name())
                .assuranceEpoch(0L)
                .build();
        store.saveUserAssurance(row);
        return toView(row);
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
        long next = (row.getAssuranceEpoch() == null ? 0L : row.getAssuranceEpoch()) + 1;
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

    private static MfaUserAssuranceView toView(MfaUserAssuranceDO row) {
        MfaEnrollmentState es = MfaEnrollmentState.parseStrict(row.getEnrollmentState());
        if (es == null) {
            es = MfaEnrollmentState.NONE;
        }
        return MfaUserAssuranceView.builder()
                .tenantId(row.getTenantId())
                .userId(row.getUserId())
                .enabled(Boolean.TRUE.equals(row.getEnabled()))
                .enrollmentState(es)
                .preferredFactorId(row.getPreferredFactorId())
                .assuranceEpoch(row.getAssuranceEpoch() == null ? 0L : row.getAssuranceEpoch())
                .build();
    }
}
