package cn.iocoder.yudao.module.system.service.mfa;

import cn.iocoder.yudao.module.system.service.mfa.enums.MfaEnrollmentState;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.MFA_FACTOR_VERIFY_FAILED;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.MFA_FLOW_INVALID;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.MFA_TOKEN_ISSUANCE_REJECTED;

/**
 * enroll 唯一提交点：factor ACTIVE + flow COMPLETED + assurance bump。
 * 有事务则同滚；无事务则显式补偿（单测 / 非代理）。
 */
@Service
public class MfaEnrollmentCommitter {

    private final MfaFactorService factorService;
    private final MfaAuthFlowService authFlowService;
    private final MfaAssuranceAuthority assuranceAuthority;

    public MfaEnrollmentCommitter(MfaFactorService factorService,
                                  MfaAuthFlowService authFlowService,
                                  MfaAssuranceAuthority assuranceAuthority) {
        this.factorService = factorService;
        this.authFlowService = authFlowService;
        this.assuranceAuthority = assuranceAuthority;
    }

    @Transactional(rollbackFor = Exception.class)
    public void commit(Long tenantId, Long userId, String factorId, Long totpStep,
                       String rawFlowToken, long expectedEpoch) {
        if (!factorService.tryActivatePendingFactor(tenantId, userId, factorId, totpStep)) {
            throw exception(MFA_FACTOR_VERIFY_FAILED);
        }
        boolean completed = false;
        try {
            if (!authFlowService.tryComplete(rawFlowToken)) {
                throw exception(MFA_FLOW_INVALID);
            }
            completed = true;
            long newEpoch = assuranceAuthority.bumpAssuranceEpoch(
                    tenantId, userId, MfaEnrollmentState.COMPLETED, true);
            if (newEpoch != expectedEpoch) {
                throw exception(MFA_TOKEN_ISSUANCE_REJECTED);
            }
        } catch (RuntimeException ex) {
            if (!TransactionSynchronizationManager.isActualTransactionActive()) {
                if (completed) {
                    authFlowService.tryRevertComplete(rawFlowToken);
                }
                factorService.revertFactorToPending(tenantId, userId, factorId);
            }
            throw ex;
        }
    }
}
