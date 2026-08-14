package cn.iocoder.yudao.module.system.service.mfa;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.OAuth2AccessTokenDO;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaLifecycleState;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaTokenClass;
import cn.iocoder.yudao.module.system.service.mfa.model.MfaPolicySnapshot;
import cn.iocoder.yudao.module.system.service.mfa.model.MfaUserAssuranceView;
import cn.iocoder.yudao.module.system.service.mfa.support.MfaLockOrder;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.MFA_POLICY_UNAVAILABLE;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.MFA_SESSION_REJECTED;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.MFA_TOKEN_CLASS_FORBIDDEN;

/**
 * SessionGuard：ADMIN 业务 Token 使用点 fail-closed 校验。
 */
@Service
public class MfaSessionGuardImpl implements MfaSessionGuard {

    public static final String UI_GLOBAL_EPOCH = "mfaGlobalPolicyEpoch";
    public static final String UI_TENANT_EPOCH = "mfaTenantPolicyEpoch";
    public static final String UI_ASSURANCE_EPOCH = "mfaAssuranceEpoch";
    public static final String UI_TOKEN_CLASS = "tokenClass";
    public static final String UI_SUBJECT_CLASS = "subjectClass";
    public static final String SUBJECT_ADMIN_USER = "ADMIN_USER";

    @Resource
    private MfaPolicyControlService policyControlService;
    @Resource
    private MfaAssuranceAuthority assuranceAuthority;

    @Override
    public void assertAccessAllowed(OAuth2AccessTokenDO accessToken) {
        if (accessToken == null) {
            throw exception(MFA_SESSION_REJECTED);
        }
        Long userId = accessToken.getUserId();
        Integer userType = accessToken.getUserType();
        if (userId == null || userId == 0L || !UserTypeEnum.ADMIN.getValue().equals(userType)) {
            return; // 非 ADMIN 用户态
        }

        MfaLockOrder.requireValidOrder(List.of(
                MfaLockOrder.Resource.GLOBAL_POLICY,
                MfaLockOrder.Resource.TENANT_POLICY,
                MfaLockOrder.Resource.USER_ASSURANCE));

        Map<String, String> info = accessToken.getUserInfo();
        if (info != null) {
            String tc = info.get(UI_TOKEN_CLASS);
            if (tc != null && !MfaTokenClass.ACCESS.name().equals(tc)) {
                throw exception(MFA_TOKEN_CLASS_FORBIDDEN);
            }
        }

        MfaPolicySnapshot policy = policyControlService.resolveEffectivePolicy(accessToken.getTenantId());
        if (!policy.isUsable() || policy.getLifecycleState() == MfaLifecycleState.DEGRADED_CLOSED) {
            throw exception(MFA_POLICY_UNAVAILABLE);
        }

        long tokenGlobal = readLong(info, UI_GLOBAL_EPOCH, 0L);
        long tokenTenant = readLong(info, UI_TENANT_EPOCH, 0L);
        long tokenAssurance = readLong(info, UI_ASSURANCE_EPOCH, 0L);

        if (tokenGlobal < policy.getGlobalMinAcceptedEpoch()) {
            throw exception(MFA_SESSION_REJECTED);
        }
        if (tokenTenant < policy.getTenantMinAcceptedEpoch()) {
            throw exception(MFA_SESSION_REJECTED);
        }

        MfaUserAssuranceView assurance = assuranceAuthority.getAssurance(
                accessToken.getTenantId(), userId);
        if (assurance == null) {
            // 缺 assurance 行：slice1 权威 fail-closed；guard 同样拒绝
            throw exception(MFA_SESSION_REJECTED);
        }
        if (tokenAssurance != assurance.getAssuranceEpoch()) {
            throw exception(MFA_SESSION_REJECTED);
        }
    }

    private static long readLong(Map<String, String> info, String key, long defaultVal) {
        if (info == null || !info.containsKey(key)) {
            return defaultVal;
        }
        try {
            return Long.parseLong(info.get(key));
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }
}
