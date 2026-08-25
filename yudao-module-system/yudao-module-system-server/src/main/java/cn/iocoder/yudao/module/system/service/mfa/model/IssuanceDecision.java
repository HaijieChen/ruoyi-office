package cn.iocoder.yudao.module.system.service.mfa.model;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 服务端一次性签发决策（ADR-MFA-v2 §1）。
 * <p>
 * 不是客户端 DTO，不可序列化/透传；消费后立即失效。
 */
@Getter
@Builder
public class IssuanceDecision {

    @Builder.Default
    private final String decisionId = UUID.randomUUID().toString();
    private final Long subjectId;
    private final Long tenantId;
    private final String clientId;
    private final String authContextId;
    private final List<String> amr;
    private final boolean mfaSatisfied;
    private final boolean enrollmentComplete;
    private final boolean recoveryRequired;
    private final long policyVersion;
    private final long factorVersion;
    private final Instant expiresAt;
    @Builder.Default
    private final AtomicBoolean consumed = new AtomicBoolean(false);

    /**
     * 一次性消费；重复消费返回 false。
     */
    public boolean tryConsume() {
        if (expiresAt != null && Instant.now().isAfter(expiresAt)) {
            return false;
        }
        return consumed.compareAndSet(false, true);
    }

    public boolean isConsumed() {
        return consumed.get();
    }

}
