package cn.iocoder.yudao.module.system.service.mfa;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.module.system.controller.admin.auth.vo.AuthLoginRespVO;
import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.OAuth2AccessTokenDO;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaIssuanceOutcome;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaIssuancePath;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaLifecycleState;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaLoginStatus;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaMode;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaTokenClass;
import cn.iocoder.yudao.module.system.service.mfa.model.IssuanceDecision;
import cn.iocoder.yudao.module.system.service.mfa.model.MfaIssuanceResult;
import cn.iocoder.yudao.module.system.service.mfa.model.MfaPolicySnapshot;
import cn.iocoder.yudao.module.system.service.mfa.support.MfaChallengeHandleStore;
import cn.iocoder.yudao.module.system.service.mfa.support.MfaIssuanceGuard;
import cn.iocoder.yudao.module.system.service.oauth2.OAuth2TokenService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.MFA_ADMIN_DIRECT_ISSUE_FORBIDDEN;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.MFA_POLICY_UNAVAILABLE;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.MFA_TOKEN_ISSUANCE_REJECTED;

/**
 * MFA Token 签发 Facade。
 * <p>
 * 默认 UNINITIALIZED/OFF 下与现状兼容：主凭证通过后经本 Facade 签发。
 * 需 MFA 时返回 challenge/enrollment，保证零 access/refresh。
 */
@Service
public class MfaTokenIssuanceFacadeImpl implements MfaTokenIssuanceFacade {

    private static final int CHALLENGE_TTL_SECONDS = 300;

    @Resource
    private MfaPolicyControlService policyControlService;
    @Resource
    private OAuth2TokenService oauth2TokenService;
    @Resource
    private MfaUserFactorProbe userFactorProbe;
    @Resource
    private MfaChallengeHandleStore challengeHandleStore;

    @Override
    public MfaIssuanceResult issueAfterPrimaryAuth(MfaIssuancePath path, Long userId, Long tenantId,
                                                   Integer userType, String clientId, List<String> scopes,
                                                   List<String> amr) {
        if (!isAdminUser(userId, userType)) {
            // MEMBER 不在本期门禁
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
            return challengeEnrollment(userId, tenantId, policy);
        }
        return challengeMfa(userId, tenantId, policy);
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
        // password grant：需 MFA 一律 REJECT（不在 token endpoint 做交互）
        if (path == MfaIssuancePath.OAUTH2_PASSWORD && need != DecisionNeed.NONE) {
            throw exception(MFA_TOKEN_ISSUANCE_REJECTED);
        }
        // authorization_code / implicit 的 CHALLENGE 在 authorize 阶段处理；到达 grant 层仍需 MFA 则 REJECT
        if (need != DecisionNeed.NONE) {
            if (path == MfaIssuancePath.OAUTH2_AUTHORIZATION_CODE
                    || path == MfaIssuancePath.OAUTH2_IMPLICIT) {
                throw exception(MFA_TOKEN_ISSUANCE_REJECTED);
            }
            return challengeMfa(userId, tenantId, policy);
        }
        return allowWithDecision(userId, tenantId, clientId, scopes, amr, policy);
    }

    @Override
    public MfaIssuanceResult refresh(MfaIssuancePath path, String refreshToken, String clientId) {
        // 先解析策略：DEGRADED_CLOSED 下即使 refresh 合法也失败
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
            // 策略变严：拒绝旧 refresh
            throw exception(MFA_TOKEN_ISSUANCE_REJECTED);
        }

        // refresh 路径：构造决策后调用底层（底层 refresh 不走 create 门闩）
        // 对 ADMIN refresh 仍要求策略可用；通过 guard 标记
        IssuanceDecision decision = IssuanceDecision.builder()
                .subjectId(0L) // refresh 时主体在 token 内
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
        // refresh 使用专用许可：底层对 refresh 校验策略由 Facade 保证
        try {
            MfaIssuanceGuard.bind(decision);
            OAuth2AccessTokenDO token = oauth2TokenService.refreshAccessToken(refreshToken, clientId);
            if (token != null && isAdminUser(token.getUserId(), token.getUserType())) {
                // 再次确认策略（防并发升级）
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
                    .loginResp(toLoginResp(token, MfaLoginStatus.AUTHENTICATED, null, null))
                    .build();
        } finally {
            MfaIssuanceGuard.clear();
        }
    }

    @Override
    public OAuth2AccessTokenDO issueClientCredentials(String clientId, List<String> scopes) {
        // userId=0 非用户态，底层放行
        return oauth2TokenService.createAccessToken(0L, UserTypeEnum.ADMIN.getValue(), clientId, scopes);
    }

    @Override
    public MfaIssuanceResult rejectInternalAdminCreate(Long userId, Integer userType) {
        if (isAdminUser(userId, userType)) {
            throw exception(MFA_ADMIN_DIRECT_ISSUE_FORBIDDEN);
        }
        // MEMBER 保持调用方自行处理
        return MfaIssuanceResult.builder()
                .outcome(MfaIssuanceOutcome.NOT_APPLICABLE)
                .loginStatus(MfaLoginStatus.AUTHENTICATED)
                .build();
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
        // OPTIONAL：用户启用才需 MFA
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
                .factorVersion(0L)
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        try {
            MfaIssuanceGuard.bind(decision);
            OAuth2AccessTokenDO token = oauth2TokenService.createAccessToken(
                    userId, UserTypeEnum.ADMIN.getValue(), clientId, scopes);
            // R-01 骨架：业务 token 标记 ACCESS（存入 userInfo 若可用）
            if (token != null && token.getUserInfo() != null) {
                token.getUserInfo().put("tokenClass", MfaTokenClass.ACCESS.name());
            }
            return MfaIssuanceResult.builder()
                    .outcome(MfaIssuanceOutcome.ALLOWED)
                    .loginStatus(MfaLoginStatus.AUTHENTICATED)
                    .accessToken(token)
                    .loginResp(toLoginResp(token, MfaLoginStatus.AUTHENTICATED, null, null))
                    .build();
        } finally {
            MfaIssuanceGuard.clear();
        }
    }

    private MfaIssuanceResult allowDirect(Long userId, Integer userType, String clientId,
                                          List<String> scopes, List<String> amr) {
        OAuth2AccessTokenDO token = oauth2TokenService.createAccessToken(userId, userType, clientId, scopes);
        return MfaIssuanceResult.builder()
                .outcome(MfaIssuanceOutcome.NOT_APPLICABLE)
                .loginStatus(MfaLoginStatus.AUTHENTICATED)
                .accessToken(token)
                .loginResp(toLoginResp(token, MfaLoginStatus.AUTHENTICATED, null, null))
                .build();
    }

    private MfaIssuanceResult challengeMfa(Long userId, Long tenantId, MfaPolicySnapshot policy) {
        MfaChallengeHandleStore.ChallengeHandle handle = challengeHandleStore.create(
                MfaTokenClass.PRE_AUTH, userId, tenantId, policy.getPolicyVersion(), CHALLENGE_TTL_SECONDS);
        AuthLoginRespVO resp = toLoginResp(null, MfaLoginStatus.MFA_REQUIRED, handle.token(), CHALLENGE_TTL_SECONDS);
        return MfaIssuanceResult.builder()
                .outcome(MfaIssuanceOutcome.CHALLENGE)
                .loginStatus(MfaLoginStatus.MFA_REQUIRED)
                .accessToken(null)
                .loginResp(resp)
                .build();
    }

    private MfaIssuanceResult challengeEnrollment(Long userId, Long tenantId, MfaPolicySnapshot policy) {
        MfaChallengeHandleStore.ChallengeHandle handle = challengeHandleStore.create(
                MfaTokenClass.ENROLLMENT, userId, tenantId, policy.getPolicyVersion(), CHALLENGE_TTL_SECONDS);
        AuthLoginRespVO resp = toLoginResp(null, MfaLoginStatus.MFA_ENROLLMENT_REQUIRED, handle.token(), CHALLENGE_TTL_SECONDS);
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

    private static AuthLoginRespVO toLoginResp(OAuth2AccessTokenDO token, MfaLoginStatus status,
                                               String challengeToken, Integer expiresIn) {
        AuthLoginRespVO.AuthLoginRespVOBuilder builder = AuthLoginRespVO.builder()
                .loginStatus(status.name());
        if (token != null) {
            builder.userId(token.getUserId())
                    .accessToken(token.getAccessToken())
                    .refreshToken(token.getRefreshToken())
                    .expiresTime(token.getExpiresTime());
        }
        if (challengeToken != null) {
            builder.challengeToken(challengeToken)
                    .expiresIn(expiresIn);
        }
        return builder.build();
    }

    private static boolean isAdminUser(Long userId, Integer userType) {
        return userId != null && userId != 0L
                && UserTypeEnum.ADMIN.getValue().equals(userType);
    }

}
