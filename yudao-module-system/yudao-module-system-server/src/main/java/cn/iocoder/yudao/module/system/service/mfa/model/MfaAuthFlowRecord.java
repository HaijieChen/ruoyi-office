package cn.iocoder.yudao.module.system.service.mfa.model;

import cn.iocoder.yudao.module.system.service.mfa.enums.MfaAuthFlowState;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaFlowTokenClass;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 服务端权威 auth flow 行（不暴露原始 flowToken，只存 hash）。
 */
@Data
@Builder
public class MfaAuthFlowRecord {

    private String id;
    /** SHA-256 hex of raw flowToken */
    private String flowTokenHash;
    private MfaFlowTokenClass tokenClass;
    private MfaAuthFlowState state;
    private Long tenantId;
    private Long userId;
    private String clientId;
    private long globalPolicyEpoch;
    private long tenantPolicyEpoch;
    private long assuranceEpoch;
    @Builder.Default
    private List<String> allowedActions = new ArrayList<>();
    @Builder.Default
    private List<String> allowedFactorIds = new ArrayList<>();
    private int attempts;
    private Instant expiresAt;
    private Instant createdAt;

    public boolean isExpired(Instant now) {
        return expiresAt != null && now.isAfter(expiresAt);
    }
}
