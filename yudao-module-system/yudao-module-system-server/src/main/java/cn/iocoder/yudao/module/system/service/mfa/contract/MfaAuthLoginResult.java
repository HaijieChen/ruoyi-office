package cn.iocoder.yudao.module.system.service.mfa.contract;

import cn.iocoder.yudao.module.system.service.mfa.enums.MfaFlowTokenClass;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaLoginStatus;
import lombok.Builder;
import lombok.Value;

import java.util.Collections;
import java.util.List;

/**
 * ADR-MFA-v3 §7 canonical 判别式登录结果骨架。
 * <p>
 * 禁止旧别名字段：challengeToken / preAuthToken / enrollmentToken / recoveryToken。
 * 中间态仅允许 {@link FlowPayload#flowToken} + {@link FlowPayload#tokenClass}。
 */
@Value
@Builder
public class MfaAuthLoginResult {

    MfaLoginStatus loginStatus;
    /** 仅 AUTHENTICATED */
    String accessToken;
    /** 仅 AUTHENTICATED */
    String refreshToken;
    Integer expiresIn;
    /** 仅 MFA_* 中间态 */
    FlowPayload flow;

    @Value
    @Builder
    public static class FlowPayload {
        String flowToken;
        MfaFlowTokenClass tokenClass;
        int expiresIn;
        @Builder.Default
        List<String> allowedActions = Collections.emptyList();
        @Builder.Default
        List<FactorRef> factors = Collections.emptyList();
    }

    @Value
    @Builder
    public static class FactorRef {
        String id;
        String type;
        String label;
        String maskedTarget;
    }

    public static MfaAuthLoginResult authenticated(String accessToken, String refreshToken, int expiresIn) {
        return MfaAuthLoginResult.builder()
                .loginStatus(MfaLoginStatus.AUTHENTICATED)
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(expiresIn)
                .flow(null)
                .build();
    }

    public static MfaAuthLoginResult mfaFlow(MfaLoginStatus status, FlowPayload flow) {
        if (status == null || status == MfaLoginStatus.AUTHENTICATED) {
            throw new IllegalArgumentException("mfa flow requires non-AUTHENTICATED status");
        }
        if (flow == null || flow.getFlowToken() == null || flow.getTokenClass() == null) {
            throw new IllegalArgumentException("flowToken and tokenClass required");
        }
        return MfaAuthLoginResult.builder()
                .loginStatus(status)
                .accessToken(null)
                .refreshToken(null)
                .expiresIn(null)
                .flow(flow)
                .build();
    }

    public boolean hasAccessOrRefreshToken() {
        return accessToken != null || refreshToken != null;
    }
}
