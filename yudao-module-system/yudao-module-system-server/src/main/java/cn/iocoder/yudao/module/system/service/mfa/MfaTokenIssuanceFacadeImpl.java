package cn.iocoder.yudao.module.system.service.mfa;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.module.system.controller.admin.auth.vo.AuthLoginRespVO;
import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.OAuth2AccessTokenDO;
import cn.iocoder.yudao.module.system.service.mfa.contract.MfaAuthLoginResult;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaFlowTokenClass;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaIssuanceOutcome;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaIssuancePath;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaLifecycleState;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaLoginStatus;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaMode;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaTokenClass;
import cn.iocoder.yudao.module.system.service.mfa.model.IssuanceDecision;
import cn.iocoder.yudao.module.system.service.mfa.model.MfaAuthFlowRecord;
import cn.iocoder.yudao.module.system.service.mfa.model.MfaIssuanceResult;
import cn.iocoder.yudao.module.system.service.mfa.model.MfaIssuedFlow;
import cn.iocoder.yudao.module.system.service.mfa.model.MfaPolicySnapshot;
import cn.iocoder.yudao.module.system.service.mfa.model.MfaUserAssuranceView;
import cn.iocoder.yudao.module.system.service.mfa.support.MfaIssuanceGuard;
import cn.iocoder.yudao.module.system.service.mfa.support.MfaLockOrder;
import cn.iocoder.yudao.module.system.service.oauth2.OAuth2TokenService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.MFA_ADMIN_DIRECT_ISSUE_FORBIDDEN;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.MFA_FACTOR_VERIFY_FAILED;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.MFA_FLOW_INVALID;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.MFA_POLICY_UNAVAILABLE;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.MFA_TOKEN_ISSUANCE_REJECTED;

/**
 * MFA Token 签发 Facade（切片 2：对接 AuthFlow / T_issue / SessionGuard 元数据）。
 * <p>
 * 默认 UNINITIALIZED/OFF 下与现状兼容：主凭证通过后经本 Facade 签发。
 * 需 MFA 时返回 canonical flowToken + tokenClass，保证零 access/refresh。
 */
@Service
public class MfaTokenIssuanceFacadeImpl implements MfaTokenIssuanceFacade {

    private static final int PRE_AUTH_TTL_SECONDS = MfaAuthLoginResult.PRE_AUTH_MAX_TTL_SECONDS;
    private static final int ENROLLMENT_TTL_SECONDS = MfaAuthLoginResult.ENROLLMENT_MAX_TTL_SECONDS;

    @Resource
    private MfaPolicyControlService policyControlService;
    @Resource
    private OAuth2TokenService oauth2TokenService;
    @Resource
    private MfaUserFactorProbe userFactorProbe;
    @Resource
    private MfaAuthFlowService authFlowService;
    @Resource
    private MfaFactorService factorService;
    @Resource
    private MfaAssuranceAuthority assuranceAuthority;

    @Override
    public MfaIssuanceResult issueAfterPrimaryAuth(MfaIssuancePath path, Long userId, Long tenantId,
                                                   Integer userType, String clientId, List<String> scopes,
                                                   List<String> amr) {
        if (!isAdminUser(userId, userType)) {
            return allowDirect(userId, userType, clientId, scopes, amr);
        }
        MfaPolicySnapshot policy = policyControlService.resolveEffectivePolicy(tenantId);
        if (!policy.isUsable() || policy.getLifecycleState() == MfaLifecycleState.DEGRADED_CLOSED) {
            return policyUnavailable();
        }
        DecisionNeed need = evaluateNeed(policy, tenantId, userId);
        if (need == DecisionNeed.NONE) {
            return allowWithDecision(userId, tenantId, clientId, scopes, amr, policy);
        }
        if (need == DecisionNeed.ENROLLMENT) {
            return challengeEnrollment(userId, tenantId, clientId, policy);
        }
        return challengeMfa(userId, tenantId, clientId, policy);
    }

    @Override
    public MfaIssuanceResult issueForOAuthGrant(MfaIssuancePath path, Long userId, Long tenantId,
                                                Integer userType, String clientId, List<String> scopes,
                                                List<String> amr) {
        if (path == MfaIssuancePath.OAUTH2_CLIENT_CREDENTIALS) {
            OAuth2AccessTokenDO token = issueClientCredentials(clientId, scopes);
            return MfaIssuanceResult.builder()
                    .outcome(MfaIssuanceOutcome.NOT_APPLICABLE)
                    .loginStatus(MfaLoginStatus.AUTHENTICATED)
                    .accessToken(token)
                    .build();
        }
        if (!isAdminUser(userId, userType)) {
            return allowDirect(userId, userType, clientId, scopes, amr);
        }
        MfaPolicySnapshot policy = policyControlService.resolveEffectivePolicy(tenantId);
        if (!policy.isUsable() || policy.getLifecycleState() == MfaLifecycleState.DEGRADED_CLOSED) {
            throw exception(MFA_POLICY_UNAVAILABLE);
        }
        DecisionNeed need = evaluateNeed(policy, tenantId, userId);
        if (path == MfaIssuancePath.OAUTH2_PASSWORD && need != DecisionNeed.NONE) {
            throw exception(MFA_TOKEN_ISSUANCE_REJECTED);
        }
        if (need != DecisionNeed.NONE) {
            if (path == MfaIssuancePath.OAUTH2_AUTHORIZATION_CODE
                    || path == MfaIssuancePath.OAUTH2_IMPLICIT) {
                throw exception(MFA_TOKEN_ISSUANCE_REJECTED);
            }
            return challengeMfa(userId, tenantId, clientId, policy);
        }
        return allowWithDecision(userId, tenantId, clientId, scopes, amr, policy);
    }

    @Override
    public MfaIssuanceResult refresh(MfaIssuancePath path, String refreshToken, String clientId) {
        MfaPolicySnapshot policy = policyControlService.resolveEffectivePolicy(null);
        if (!policy.isUsable() || policy.getLifecycleState() == MfaLifecycleState.DEGRADED_CLOSED) {
            throw exception(MFA_POLICY_UNAVAILABLE);
        }
        if (userFactorProbe.isEnrollmentPendingOnSession(refreshToken)
                || userFactorProbe.isRecoveryPendingOnSession(refreshToken)) {
            throw exception(MFA_TOKEN_ISSUANCE_REJECTED);
        }
        Long sessionVersion = userFactorProbe.getSessionPolicyVersion(refreshToken);
        if (sessionVersion != null && sessionVersion < policy.getPolicyVersion()
                && policy.getMode() == MfaMode.REQUIRED) {
            throw exception(MFA_TOKEN_ISSUANCE_REJECTED);
        }

        IssuanceDecision decision = IssuanceDecision.builder()
                .subjectId(0L)
                .clientId(clientId)
                .authContextId(UUID.randomUUID().toString())
                .amr(List.of("refresh"))
                .mfaSatisfied(true)
                .enrollmentComplete(true)
                .recoveryRequired(false)
                .policyVersion(policy.getPolicyVersion())
                .factorVersion(0L)
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        try {
            MfaIssuanceGuard.bind(decision);
            OAuth2AccessTokenDO token = oauth2TokenService.refreshAccessToken(refreshToken, clientId);
            if (token != null && isAdminUser(token.getUserId(), token.getUserType())) {
                MfaPolicySnapshot again = policyControlService.resolveEffectivePolicy(token.getTenantId());
                if (!again.isUsable() || again.getLifecycleState() == MfaLifecycleState.DEGRADED_CLOSED) {
                    oauth2TokenService.removeAccessToken(token.getAccessToken());
                    throw exception(MFA_POLICY_UNAVAILABLE);
                }
            }
            return MfaIssuanceResult.builder()
                    .outcome(MfaIssuanceOutcome.ALLOWED)
                    .loginStatus(MfaLoginStatus.AUTHENTICATED)
                    .accessToken(token)
                    .loginResp(toAuthenticatedResp(token))
                    .build();
        } finally {
            MfaIssuanceGuard.clear();
        }
    }

    @Override
    public OAuth2AccessTokenDO issueClientCredentials(String clientId, List<String> scopes) {
        return oauth2TokenService.createAccessToken(0L, UserTypeEnum.ADMIN.getValue(), clientId, scopes);
    }

    @Override
    public MfaIssuanceResult rejectInternalAdminCreate(Long userId, Integer userType) {
        if (isAdminUser(userId, userType)) {
            throw exception(MFA_ADMIN_DIRECT_ISSUE_FORBIDDEN);
        }
        return MfaIssuanceResult.builder()
                .outcome(MfaIssuanceOutcome.NOT_APPLICABLE)
                .loginStatus(MfaLoginStatus.AUTHENTICATED)
                .build();
    }

    @Override
    public MfaIssuanceResult completeChallengeAndIssue(String rawFlowToken, String factorId, String factorType,
                                                       String code, String clientId, List<String> scopes) {
        // T_issue 锁序：GLOBAL → TENANT → USER → FACTOR → FLOW → TOKEN
        MfaLockOrder.requireValidOrder(List.of(
                MfaLockOrder.Resource.GLOBAL_POLICY,
                MfaLockOrder.Resource.TENANT_POLICY,
                MfaLockOrder.Resource.USER_ASSURANCE,
                MfaLockOrder.Resource.FACTOR,
                MfaLockOrder.Resource.FLOW_OR_DECISION_OR_CODE,
                MfaLockOrder.Resource.TOKEN_FAMILY));

        MfaAuthFlowRecord flow = authFlowService.resolveActive(rawFlowToken);
        if (flow == null) {
            throw exception(MFA_FLOW_INVALID);
        }
        if (flow.getTokenClass() != MfaFlowTokenClass.PRE_AUTH
                && flow.getTokenClass() != MfaFlowTokenClass.ENROLLMENT) {
            throw exception(MFA_FLOW_INVALID);
        }

        MfaPolicySnapshot policy = policyControlService.resolveEffectivePolicy(flow.getTenantId());
        if (!policy.isUsable() || policy.getLifecycleState() == MfaLifecycleState.DEGRADED_CLOSED) {
            throw exception(MFA_POLICY_UNAVAILABLE);
        }
        // Decision epoch 精确匹配
        if (flow.getGlobalPolicyEpoch() != policy.getGlobalPolicyEpoch()
                || flow.getTenantPolicyEpoch() != policy.getTenantPolicyEpoch()) {
            throw exception(MFA_TOKEN_ISSUANCE_REJECTED);
        }

        boolean verified;
        try {
            verified = factorService.verifyChallengeCode(rawFlowToken, factorId, factorType, code);
        } catch (IllegalStateException | IllegalArgumentException ex) {
            throw exception(MFA_FACTOR_VERIFY_FAILED);
        }
        if (!verified) {
            throw exception(MFA_FACTOR_VERIFY_FAILED);
        }

        if (!authFlowService.tryComplete(rawFlowToken)) {
            throw exception(MFA_FLOW_INVALID);
        }

        MfaUserAssuranceView assurance = assuranceAuthority.ensureBootstrapRow(
                flow.getTenantId() == null ? 0L : flow.getTenantId(), flow.getUserId());
        List<String> amr = List.of(factorType == null ? "mfa" : factorType.toLowerCase());
        return allowWithDecision(flow.getUserId(), flow.getTenantId(),
                clientId != null ? clientId : flow.getClientId(),
                scopes, amr, policy, assurance.getAssuranceEpoch());
    }

    private enum DecisionNeed {
        NONE, MFA, ENROLLMENT
    }

    private DecisionNeed evaluateNeed(MfaPolicySnapshot policy, Long tenantId, Long userId) {
        MfaMode mode = policy.getMode();
        if (mode == null || mode == MfaMode.OFF) {
            return DecisionNeed.NONE;
        }
        if (mode == MfaMode.REQUIRED) {
            if (!userFactorProbe.hasEligibleActiveFactor(tenantId, userId)
                    || !userFactorProbe.isEnrollmentComplete(tenantId, userId)) {
                return DecisionNeed.ENROLLMENT;
            }
            return DecisionNeed.MFA;
        }
        if (mode == MfaMode.OPTIONAL && userFactorProbe.isUserMfaEnabled(tenantId, userId)) {
            if (!userFactorProbe.hasEligibleActiveFactor(tenantId, userId)) {
                return DecisionNeed.ENROLLMENT;
            }
            return DecisionNeed.MFA;
        }
        return DecisionNeed.NONE;
    }

    private MfaIssuanceResult allowWithDecision(Long userId, Long tenantId, String clientId,
                                                List<String> scopes, List<String> amr,
                                                MfaPolicySnapshot policy) {
        long assuranceEpoch = 0L;
        if (tenantId != null) {
            MfaUserAssuranceView view = assuranceAuthority.ensureBootstrapRow(tenantId, userId);
            assuranceEpoch = view.getAssuranceEpoch();
        }
        return allowWithDecision(userId, tenantId, clientId, scopes, amr, policy, assuranceEpoch);
    }

    private MfaIssuanceResult allowWithDecision(Long userId, Long tenantId, String clientId,
                                                List<String> scopes, List<String> amr,
                                                MfaPolicySnapshot policy, long assuranceEpoch) {
        MfaLockOrder.requireValidOrder(List.of(
                MfaLockOrder.Resource.GLOBAL_POLICY,
                MfaLockOrder.Resource.TENANT_POLICY,
                MfaLockOrder.Resource.USER_ASSURANCE,
                MfaLockOrder.Resource.TOKEN_FAMILY));

        IssuanceDecision decision = IssuanceDecision.builder()
                .subjectId(userId)
                .tenantId(tenantId)
                .clientId(clientId)
                .authContextId(UUID.randomUUID().toString())
                .amr(amr == null ? Collections.emptyList() : amr)
                .mfaSatisfied(true)
                .enrollmentComplete(true)
                .recoveryRequired(false)
                .policyVersion(policy.getPolicyVersion())
                .factorVersion(assuranceEpoch)
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        try {
            MfaIssuanceGuard.bind(decision);
            OAuth2AccessTokenDO token = oauth2TokenService.createAccessToken(
                    userId, UserTypeEnum.ADMIN.getValue(), clientId, scopes);
            stampTokenMetadata(token, policy, assuranceEpoch);
            return MfaIssuanceResult.builder()
                    .outcome(MfaIssuanceOutcome.ALLOWED)
                    .loginStatus(MfaLoginStatus.AUTHENTICATED)
                    .accessToken(token)
                    .loginResp(toAuthenticatedResp(token))
                    .build();
        } finally {
            MfaIssuanceGuard.clear();
        }
    }

    private static void stampTokenMetadata(OAuth2AccessTokenDO token, MfaPolicySnapshot policy,
                                           long assuranceEpoch) {
        if (token == null) {
            return;
        }
        if (token.getUserInfo() == null) {
            token.setUserInfo(new HashMap<>());
        }
        token.getUserInfo().put(MfaSessionGuardImpl.UI_TOKEN_CLASS, MfaTokenClass.ACCESS.name());
        token.getUserInfo().put(MfaSessionGuardImpl.UI_SUBJECT_CLASS, MfaSessionGuardImpl.SUBJECT_ADMIN_USER);
        token.getUserInfo().put(MfaSessionGuardImpl.UI_GLOBAL_EPOCH,
                String.valueOf(policy.getGlobalPolicyEpoch()));
        token.getUserInfo().put(MfaSessionGuardImpl.UI_TENANT_EPOCH,
                String.valueOf(policy.getTenantPolicyEpoch()));
        token.getUserInfo().put(MfaSessionGuardImpl.UI_ASSURANCE_EPOCH, String.valueOf(assuranceEpoch));
    }

    private MfaIssuanceResult allowDirect(Long userId, Integer userType, String clientId,
                                          List<String> scopes, List<String> amr) {
        OAuth2AccessTokenDO token = oauth2TokenService.createAccessToken(userId, userType, clientId, scopes);
        return MfaIssuanceResult.builder()
                .outcome(MfaIssuanceOutcome.NOT_APPLICABLE)
                .loginStatus(MfaLoginStatus.AUTHENTICATED)
                .accessToken(token)
                .loginResp(toAuthenticatedResp(token))
                .build();
    }

    private MfaIssuanceResult challengeMfa(Long userId, Long tenantId, String clientId,
                                           MfaPolicySnapshot policy) {
        MfaIssuedFlow flow = authFlowService.issue(
                MfaFlowTokenClass.PRE_AUTH, userId, tenantId, clientId, policy, 0L,
                List.of("verify", "send", "logout"), List.of(), PRE_AUTH_TTL_SECONDS);
        // 契约校验：status × tokenClass × TTL
        MfaAuthLoginResult.mfaFlow(MfaLoginStatus.MFA_REQUIRED,
                MfaAuthLoginResult.FlowPayload.of(flow.getFlowToken(), MfaFlowTokenClass.PRE_AUTH,
                        flow.getExpiresInSeconds()));
        AuthLoginRespVO resp = toFlowResp(userId, MfaLoginStatus.MFA_REQUIRED, flow);
        return MfaIssuanceResult.builder()
                .outcome(MfaIssuanceOutcome.CHALLENGE)
                .loginStatus(MfaLoginStatus.MFA_REQUIRED)
                .accessToken(null)
                .loginResp(resp)
                .build();
    }

    private MfaIssuanceResult challengeEnrollment(Long userId, Long tenantId, String clientId,
                                                  MfaPolicySnapshot policy) {
        MfaIssuedFlow flow = authFlowService.issue(
                MfaFlowTokenClass.ENROLLMENT, userId, tenantId, clientId, policy, 0L,
                List.of("enroll", "logout"), List.of(), ENROLLMENT_TTL_SECONDS);
        MfaAuthLoginResult.mfaFlow(MfaLoginStatus.MFA_ENROLLMENT_REQUIRED,
                MfaAuthLoginResult.FlowPayload.of(flow.getFlowToken(), MfaFlowTokenClass.ENROLLMENT,
                        flow.getExpiresInSeconds()));
        AuthLoginRespVO resp = toFlowResp(userId, MfaLoginStatus.MFA_ENROLLMENT_REQUIRED, flow);
        return MfaIssuanceResult.builder()
                .outcome(MfaIssuanceOutcome.CHALLENGE)
                .loginStatus(MfaLoginStatus.MFA_ENROLLMENT_REQUIRED)
                .accessToken(null)
                .loginResp(resp)
                .build();
    }

    private MfaIssuanceResult policyUnavailable() {
        throw exception(MFA_POLICY_UNAVAILABLE);
    }

    private static AuthLoginRespVO toAuthenticatedResp(OAuth2AccessTokenDO token) {
        AuthLoginRespVO.AuthLoginRespVOBuilder builder = AuthLoginRespVO.builder()
                .loginStatus(MfaLoginStatus.AUTHENTICATED.name());
        if (token != null) {
            builder.userId(token.getUserId())
                    .accessToken(token.getAccessToken())
                    .refreshToken(token.getRefreshToken())
                    .expiresTime(token.getExpiresTime());
        }
        return builder.build();
    }

    private static AuthLoginRespVO toFlowResp(Long userId, MfaLoginStatus status, MfaIssuedFlow flow) {
        return AuthLoginRespVO.builder()
                .userId(userId)
                .loginStatus(status.name())
                .flowToken(flow.getFlowToken())
                .tokenClass(flow.getTokenClass().name())
                .expiresIn(flow.getExpiresInSeconds())
                // 明确不写 challengeToken 别名
                .challengeToken(null)
                .accessToken(null)
                .refreshToken(null)
                .build();
    }

    private static boolean isAdminUser(Long userId, Integer userType) {
        return userId != null && userId != 0L
                && UserTypeEnum.ADMIN.getValue().equals(userType);
    }

}
