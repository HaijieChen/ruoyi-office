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
import cn.iocoder.yudao.module.system.service.mfa.store.InMemoryMfaEnrollSagaStore;
import cn.iocoder.yudao.module.system.service.mfa.store.MfaEnrollSagaStore;
import cn.iocoder.yudao.module.system.service.mfa.support.MfaIssuanceGuard;
import cn.iocoder.yudao.module.system.service.mfa.support.MfaLockOrder;
import cn.iocoder.yudao.module.system.service.oauth2.OAuth2TokenService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
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
    @Resource
    private MfaEnrollmentCommitter enrollmentCommitter;
    @Resource
    private MfaEnrollSagaStore enrollSagaStore;

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
        // F-S2-03：先看全局策略可用性；非 OFF 下旧会话缺 epoch/assurance 必须拒绝
        MfaPolicySnapshot globalPolicy = policyControlService.resolveEffectivePolicy(null);
        if (!globalPolicy.isUsable() || globalPolicy.getLifecycleState() == MfaLifecycleState.DEGRADED_CLOSED) {
            throw exception(MFA_POLICY_UNAVAILABLE);
        }
        if (userFactorProbe.isEnrollmentPendingOnSession(refreshToken)
                || userFactorProbe.isRecoveryPendingOnSession(refreshToken)) {
            throw exception(MFA_TOKEN_ISSUANCE_REJECTED);
        }
        Long sessionVersion = userFactorProbe.getSessionPolicyVersion(refreshToken);
        // REQUIRED 下 sessionVersion 缺失或过旧一律拒绝（不得 null 放行）
        if (globalPolicy.getMode() == MfaMode.REQUIRED) {
            if (sessionVersion == null || sessionVersion < globalPolicy.getGlobalMinAcceptedEpoch()) {
                throw exception(MFA_TOKEN_ISSUANCE_REJECTED);
            }
        } else if (sessionVersion != null && sessionVersion < globalPolicy.getPolicyVersion()
                && globalPolicy.getMode() == MfaMode.OPTIONAL) {
            // OPTIONAL 变严路径：有快照则比较
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
                .policyVersion(globalPolicy.getPolicyVersion())
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
                // 切片 3：ADMIN refresh 一律盖安全元数据，供 SessionGuard 持续校验
                long aEpoch = 0L;
                if (token.getTenantId() != null) {
                    MfaUserAssuranceView view = assuranceAuthority.ensureBootstrapRow(
                            token.getTenantId(), token.getUserId());
                    aEpoch = view.getAssuranceEpoch();
                }
                stampTokenMetadata(token, again, aEpoch);
                oauth2TokenService.saveAccessToken(token);
                if (again.getMode() != null && again.getMode() != MfaMode.OFF) {
                    long tg = Long.parseLong(token.getUserInfo().get(MfaSessionGuardImpl.UI_GLOBAL_EPOCH));
                    long tt = Long.parseLong(token.getUserInfo().get(MfaSessionGuardImpl.UI_TENANT_EPOCH));
                    long ta = Long.parseLong(token.getUserInfo().get(MfaSessionGuardImpl.UI_ASSURANCE_EPOCH));
                    if (tg < again.getGlobalMinAcceptedEpoch() || tt < again.getTenantMinAcceptedEpoch()) {
                        oauth2TokenService.removeAccessToken(token.getAccessToken());
                        throw exception(MFA_TOKEN_ISSUANCE_REJECTED);
                    }
                    MfaUserAssuranceView current = assuranceAuthority.getAssurance(
                            token.getTenantId(), token.getUserId());
                    if (current == null || ta != current.getAssuranceEpoch()) {
                        oauth2TokenService.removeAccessToken(token.getAccessToken());
                        throw exception(MFA_TOKEN_ISSUANCE_REJECTED);
                    }
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
        // 普通 complete 仅 PRE_AUTH；ENROLLMENT 需独立原子路径（切片 3）
        if (flow.getTokenClass() != MfaFlowTokenClass.PRE_AUTH) {
            throw exception(MFA_FLOW_INVALID);
        }

        MfaPolicySnapshot policy = policyControlService.resolveEffectivePolicy(flow.getTenantId());
        if (!policy.isUsable() || policy.getLifecycleState() == MfaLifecycleState.DEGRADED_CLOSED) {
            throw exception(MFA_POLICY_UNAVAILABLE);
        }
        // Decision epoch 精确匹配（含 assurance）
        if (flow.getGlobalPolicyEpoch() != policy.getGlobalPolicyEpoch()
                || flow.getTenantPolicyEpoch() != policy.getTenantPolicyEpoch()) {
            throw exception(MFA_TOKEN_ISSUANCE_REJECTED);
        }
        Long tenantId = flow.getTenantId() == null ? 0L : flow.getTenantId();
        MfaUserAssuranceView assurance = assuranceAuthority.getAssurance(tenantId, flow.getUserId());
        if (assurance == null) {
            // 缺行：bootstrap 仅当 flow 绑 0 且当前仍为 0 才允许
            assurance = assuranceAuthority.ensureBootstrapRow(tenantId, flow.getUserId());
        }
        if (flow.getAssuranceEpoch() != assurance.getAssuranceEpoch()) {
            // F-S2-04：assurance bump 后旧 flow 失效
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

        // F-S2-01：先签发 Token，成功后再 CAS 消费 flow；Token 失败则 flow 保持 ACTIVE
        List<String> amr = List.of(factorType == null ? "mfa" : factorType.toLowerCase());
        MfaIssuanceResult issued;
        try {
            issued = allowWithDecision(flow.getUserId(), flow.getTenantId(),
                    clientId != null ? clientId : flow.getClientId(),
                    scopes, amr, policy, assurance.getAssuranceEpoch());
        } catch (RuntimeException ex) {
            // flow 未 complete，可重试
            throw ex;
        }
        if (!authFlowService.tryComplete(rawFlowToken)) {
            // 极少：并发 double-complete；撤销刚发的 token 防双签发
            if (issued.getAccessToken() != null) {
                oauth2TokenService.removeAccessToken(issued.getAccessToken().getAccessToken());
            }
            throw exception(MFA_FLOW_INVALID);
        }
        return issued;
    }

    @Override
    public cn.iocoder.yudao.module.system.service.mfa.model.MfaPendingTotp startTotpEnrollment(
            String rawEnrollmentFlowToken, String accountName) {
        MfaAuthFlowRecord flow = authFlowService.resolveActive(rawEnrollmentFlowToken);
        if (flow == null || flow.getTokenClass() != MfaFlowTokenClass.ENROLLMENT) {
            throw exception(MFA_FLOW_INVALID);
        }
        if (flow.getAllowedActions() != null && !flow.getAllowedActions().isEmpty()
                && !flow.getAllowedActions().contains("enroll")) {
            throw exception(MFA_FLOW_INVALID);
        }
        return factorService.startPendingTotp(flow.getTenantId(), flow.getUserId(), accountName);
    }

    @Override
    public MfaIssuanceResult completeTotpEnrollmentAndIssue(String rawEnrollmentFlowToken, String factorId,
                                                            String code, String clientId, List<String> scopes) {
        MfaLockOrder.requireValidOrder(List.of(
                MfaLockOrder.Resource.GLOBAL_POLICY,
                MfaLockOrder.Resource.TENANT_POLICY,
                MfaLockOrder.Resource.USER_ASSURANCE,
                MfaLockOrder.Resource.FACTOR,
                MfaLockOrder.Resource.FLOW_OR_DECISION_OR_CODE,
                MfaLockOrder.Resource.TOKEN_FAMILY));

        MfaAuthFlowRecord flow = authFlowService.resolveActive(rawEnrollmentFlowToken);
        if (flow == null || flow.getTokenClass() != MfaFlowTokenClass.ENROLLMENT) {
            throw exception(MFA_FLOW_INVALID);
        }
        MfaPolicySnapshot policy = policyControlService.resolveEffectivePolicy(flow.getTenantId());
        if (!policy.isUsable() || policy.getLifecycleState() == MfaLifecycleState.DEGRADED_CLOSED) {
            throw exception(MFA_POLICY_UNAVAILABLE);
        }
        if (flow.getGlobalPolicyEpoch() != policy.getGlobalPolicyEpoch()
                || flow.getTenantPolicyEpoch() != policy.getTenantPolicyEpoch()) {
            throw exception(MFA_TOKEN_ISSUANCE_REJECTED);
        }
        Long tenantId = flow.getTenantId() == null ? 0L : flow.getTenantId();
        MfaUserAssuranceView assurance = assuranceAuthority.ensureBootstrapRow(tenantId, flow.getUserId());
        if (flow.getAssuranceEpoch() != assurance.getAssuranceEpoch()) {
            throw exception(MFA_TOKEN_ISSUANCE_REJECTED);
        }
        Long totpStep = factorService.matchPendingTotpStep(tenantId, flow.getUserId(), factorId, code);
        if (totpStep == null) {
            throw exception(MFA_FACTOR_VERIFY_FAILED);
        }
        long expectedEpoch = assurance.getAssuranceEpoch() + 1;
        String flowHash = MfaAuthFlowServiceImpl.sha256Hex(rawEnrollmentFlowToken);
        var sagaStore = enrollSaga();
        var existing = sagaStore.get(flowHash);
        if (existing != null && MfaEnrollSagaStore.COMPENSATE_TOKEN.equals(existing.state())) {
            if (!tryRevokeToken(existing.accessToken())) {
                throw exception(MFA_TOKEN_ISSUANCE_REJECTED);
            }
            existing = existing.cleared(MfaEnrollSagaStore.VERIFIED);
            sagaStore.upsert(existing);
        }

        MfaIssuanceResult issued;
        if (existing != null && MfaEnrollSagaStore.TOKEN_ISSUED.equals(existing.state())
                && existing.accessToken() != null) {
            issued = reuseIssuedToken(existing);
            if (issued == null) {
                existing = existing.cleared(MfaEnrollSagaStore.VERIFIED);
                sagaStore.upsert(existing);
            }
        } else {
            issued = null;
        }
        if (issued == null) {
            issued = allowWithDecision(flow.getUserId(), flow.getTenantId(),
                    clientId != null ? clientId : flow.getClientId(),
                    scopes, List.of("totp", "enroll"), policy, expectedEpoch);
            String access = issued.getAccessToken() == null ? null : issued.getAccessToken().getAccessToken();
            String refresh = issued.getAccessToken() == null ? null : issued.getAccessToken().getRefreshToken();
            sagaStore.upsert(new MfaEnrollSagaStore.Record(flowHash, tenantId, flow.getUserId(), factorId,
                    totpStep, expectedEpoch, access, refresh, MfaEnrollSagaStore.TOKEN_ISSUED));
        }
        String access = issued.getAccessToken() == null ? null : issued.getAccessToken().getAccessToken();
        String refresh = issued.getAccessToken() == null ? null : issued.getAccessToken().getRefreshToken();
        try {
            enrollmentCommitter().commit(tenantId, flow.getUserId(), factorId, totpStep,
                    rawEnrollmentFlowToken, expectedEpoch);
            sagaStore.upsert(new MfaEnrollSagaStore.Record(flowHash, tenantId, flow.getUserId(), factorId,
                    totpStep, expectedEpoch, access, refresh, MfaEnrollSagaStore.COMMITTED));
        } catch (RuntimeException ex) {
            boolean revoked = tryRevokeToken(access);
            sagaStore.upsert(new MfaEnrollSagaStore.Record(flowHash, tenantId, flow.getUserId(), factorId,
                    totpStep, expectedEpoch,
                    revoked ? null : access, revoked ? null : refresh,
                    revoked ? MfaEnrollSagaStore.VERIFIED : MfaEnrollSagaStore.COMPENSATE_TOKEN));
            throw ex;
        }
        return issued;
    }

    private MfaIssuanceResult reuseIssuedToken(MfaEnrollSagaStore.Record saga) {
        OAuth2AccessTokenDO token = oauth2TokenService.getAccessToken(saga.accessToken());
        if (token == null) {
            return null;
        }
        return MfaIssuanceResult.builder()
                .outcome(MfaIssuanceOutcome.ALLOWED)
                .loginStatus(MfaLoginStatus.AUTHENTICATED)
                .accessToken(token)
                .loginResp(toAuthenticatedResp(token))
                .build();
    }

    private boolean tryRevokeToken(String accessToken) {
        if (accessToken == null) {
            return true;
        }
        try {
            oauth2TokenService.removeAccessToken(accessToken);
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private MfaEnrollmentCommitter enrollmentCommitter() {
        if (enrollmentCommitter == null) {
            enrollmentCommitter = new MfaEnrollmentCommitter(factorService, authFlowService, assuranceAuthority);
        }
        return enrollmentCommitter;
    }

    private MfaEnrollSagaStore enrollSaga() {
        if (enrollSagaStore == null) {
            enrollSagaStore = InMemoryMfaEnrollSagaStore.shared();
        }
        return enrollSagaStore;
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
            oauth2TokenService.saveAccessToken(token);
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
        long aEpoch = currentAssuranceEpoch(tenantId, userId);
        List<String> factorIds = new ArrayList<>();
        List<AuthLoginRespVO.FactorRef> factorRefs = new ArrayList<>();
        if (tenantId != null) {
            for (var f : factorService.listActiveFactors(tenantId, userId)) {
                factorIds.add(f.getId());
                factorRefs.add(AuthLoginRespVO.FactorRef.builder()
                        .id(f.getId()).type(f.getType()).label(f.getLabel())
                        .maskedTarget(f.getMaskedTarget()).build());
            }
        }
        MfaIssuedFlow flow = authFlowService.issue(
                MfaFlowTokenClass.PRE_AUTH, userId, tenantId, clientId, policy, aEpoch,
                List.of("verify", "send", "logout"), factorIds, PRE_AUTH_TTL_SECONDS);
        MfaAuthLoginResult.mfaFlow(MfaLoginStatus.MFA_REQUIRED,
                MfaAuthLoginResult.FlowPayload.of(flow.getFlowToken(), MfaFlowTokenClass.PRE_AUTH,
                        flow.getExpiresInSeconds()));
        AuthLoginRespVO resp = toFlowResp(userId, MfaLoginStatus.MFA_REQUIRED, flow,
                List.of("verify", "send", "logout"), factorRefs);
        return MfaIssuanceResult.builder()
                .outcome(MfaIssuanceOutcome.CHALLENGE)
                .loginStatus(MfaLoginStatus.MFA_REQUIRED)
                .accessToken(null)
                .loginResp(resp)
                .build();
    }

    private MfaIssuanceResult challengeEnrollment(Long userId, Long tenantId, String clientId,
                                                  MfaPolicySnapshot policy) {
        long aEpoch = currentAssuranceEpoch(tenantId, userId);
        MfaIssuedFlow flow = authFlowService.issue(
                MfaFlowTokenClass.ENROLLMENT, userId, tenantId, clientId, policy, aEpoch,
                List.of("enroll", "logout"), List.of(), ENROLLMENT_TTL_SECONDS);
        MfaAuthLoginResult.mfaFlow(MfaLoginStatus.MFA_ENROLLMENT_REQUIRED,
                MfaAuthLoginResult.FlowPayload.of(flow.getFlowToken(), MfaFlowTokenClass.ENROLLMENT,
                        flow.getExpiresInSeconds()));
        AuthLoginRespVO resp = toFlowResp(userId, MfaLoginStatus.MFA_ENROLLMENT_REQUIRED, flow,
                List.of("enroll", "logout"), List.of());
        return MfaIssuanceResult.builder()
                .outcome(MfaIssuanceOutcome.CHALLENGE)
                .loginStatus(MfaLoginStatus.MFA_ENROLLMENT_REQUIRED)
                .accessToken(null)
                .loginResp(resp)
                .build();
    }

    private long currentAssuranceEpoch(Long tenantId, Long userId) {
        if (tenantId == null || userId == null) {
            return 0L;
        }
        MfaUserAssuranceView view = assuranceAuthority.ensureBootstrapRow(tenantId, userId);
        return view.getAssuranceEpoch();
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

    private static AuthLoginRespVO toFlowResp(Long userId, MfaLoginStatus status, MfaIssuedFlow flow,
                                              List<String> allowedActions,
                                              List<AuthLoginRespVO.FactorRef> factors) {
        // F-S2-05：canonical nested flow union
        return AuthLoginRespVO.builder()
                .userId(userId)
                .loginStatus(status.name())
                .accessToken(null)
                .refreshToken(null)
                .flow(AuthLoginRespVO.FlowPayload.builder()
                        .flowToken(flow.getFlowToken())
                        .tokenClass(flow.getTokenClass().name())
                        .expiresIn(flow.getExpiresInSeconds())
                        .allowedActions(allowedActions)
                        .factors(factors == null ? List.of() : factors)
                        .build())
                .build();
    }

    private static boolean isAdminUser(Long userId, Integer userType) {
        return userId != null && userId != 0L
                && UserTypeEnum.ADMIN.getValue().equals(userType);
    }

}
