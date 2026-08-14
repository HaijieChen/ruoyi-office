package cn.iocoder.yudao.module.system.service.mfa.contract;

import cn.iocoder.yudao.module.system.service.mfa.enums.MfaFlowTokenClass;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaLoginStatus;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * ADR-MFA-v3 §7 canonical 判别式登录结果。
 * <p>
 * 仅允许工厂方法构造；禁止公开 builder 构造非法联合。
 * 禁止旧别名字段 challengeToken / preAuthToken / enrollmentToken / recoveryToken。
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class MfaAuthLoginResult {

    private final MfaLoginStatus loginStatus;
    private final String accessToken;
    private final String refreshToken;
    private final Integer expiresIn;
    private final FlowPayload flow;

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class FlowPayload {
        private final String flowToken;
        private final MfaFlowTokenClass tokenClass;
        private final int expiresIn;
        private final List<String> allowedActions;
        private final List<FactorRef> factors;

        /**
         * @param expiresIn 必须 &gt; 0
         */
        public static FlowPayload of(String flowToken, MfaFlowTokenClass tokenClass, int expiresIn,
                                     List<String> allowedActions, List<FactorRef> factors) {
            if (flowToken == null || flowToken.isBlank()) {
                throw new IllegalArgumentException("flowToken required");
            }
            if (tokenClass == null) {
                throw new IllegalArgumentException("tokenClass required");
            }
            if (expiresIn <= 0) {
                throw new IllegalArgumentException("expiresIn must be positive");
            }
            return new FlowPayload(
                    flowToken,
                    tokenClass,
                    expiresIn,
                    allowedActions == null ? Collections.emptyList() : List.copyOf(allowedActions),
                    factors == null ? Collections.emptyList() : List.copyOf(factors));
        }

        public static FlowPayload of(String flowToken, MfaFlowTokenClass tokenClass, int expiresIn) {
            return of(flowToken, tokenClass, expiresIn, null, null);
        }
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class FactorRef {
        private final String id;
        private final String type;
        private final String label;
        private final String maskedTarget;

        public static FactorRef of(String id, String type, String label, String maskedTarget) {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("factor id required");
            }
            if (type == null || type.isBlank()) {
                throw new IllegalArgumentException("factor type required");
            }
            return new FactorRef(id, type, label, maskedTarget);
        }
    }

    /**
     * AUTHENTICATED：必须有 access+refresh，且无 flow。
     */
    public static MfaAuthLoginResult authenticated(String accessToken, String refreshToken, int expiresIn) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("accessToken required for AUTHENTICATED");
        }
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalArgumentException("refreshToken required for AUTHENTICATED");
        }
        if (expiresIn <= 0) {
            throw new IllegalArgumentException("expiresIn must be positive for AUTHENTICATED");
        }
        return new MfaAuthLoginResult(MfaLoginStatus.AUTHENTICATED, accessToken, refreshToken, expiresIn, null);
    }

    /**
     * MFA 中间态：status 与 tokenClass 必须精确映射，零 access/refresh。
     * <ul>
     *   <li>MFA_REQUIRED → PRE_AUTH</li>
     *   <li>MFA_ENROLLMENT_REQUIRED → ENROLLMENT</li>
     *   <li>MFA_RECOVERY_REQUIRED → RECOVERY</li>
     * </ul>
     * {@link MfaLoginStatus#MFA_POLICY_UNAVAILABLE} 不得走本工厂。
     */
    public static MfaAuthLoginResult mfaFlow(MfaLoginStatus status, FlowPayload flow) {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(flow, "flow");
        MfaFlowTokenClass expected = expectedTokenClass(status);
        if (flow.getTokenClass() != expected) {
            throw new IllegalArgumentException(
                    "tokenClass " + flow.getTokenClass() + " incompatible with status " + status
                            + "; expected " + expected);
        }
        return new MfaAuthLoginResult(status, null, null, null, flow);
    }

    private static MfaFlowTokenClass expectedTokenClass(MfaLoginStatus status) {
        return switch (status) {
            case MFA_REQUIRED -> MfaFlowTokenClass.PRE_AUTH;
            case MFA_ENROLLMENT_REQUIRED -> MfaFlowTokenClass.ENROLLMENT;
            case MFA_RECOVERY_REQUIRED -> MfaFlowTokenClass.RECOVERY;
            case AUTHENTICATED, MFA_POLICY_UNAVAILABLE ->
                    throw new IllegalArgumentException("status " + status + " cannot carry flow payload");
        };
    }

    public boolean hasAccessOrRefreshToken() {
        return accessToken != null || refreshToken != null;
    }
}
