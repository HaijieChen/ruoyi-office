package cn.iocoder.yudao.module.system.service.mfa;

import cn.iocoder.yudao.module.system.service.mfa.enums.MfaAuthFlowState;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaFlowTokenClass;
import cn.iocoder.yudao.module.system.service.mfa.model.MfaAuthFlowRecord;
import cn.iocoder.yudao.module.system.service.mfa.model.MfaIssuedFlow;
import cn.iocoder.yudao.module.system.service.mfa.model.MfaPolicySnapshot;
import cn.iocoder.yudao.module.system.service.mfa.store.InMemoryMfaAuthFlowStore;
import cn.iocoder.yudao.module.system.service.mfa.store.MfaAuthFlowStore;
import cn.iocoder.yudao.module.system.service.mfa.support.MfaLockOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Auth flow 权威（切片 4）：存储可切换；CAS 由 store 执行。
 */
@Service
public class MfaAuthFlowServiceImpl implements MfaAuthFlowService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final MfaAuthFlowStore store;

    public MfaAuthFlowServiceImpl() {
        this(InMemoryMfaAuthFlowStore.shared());
    }

    @Autowired
    public MfaAuthFlowServiceImpl(MfaAuthFlowStore store) {
        this.store = store == null ? InMemoryMfaAuthFlowStore.shared() : store;
    }

    @Override
    public MfaIssuedFlow issue(MfaFlowTokenClass tokenClass, Long userId, Long tenantId, String clientId,
                               MfaPolicySnapshot policy, long assuranceEpoch,
                               List<String> allowedActions, List<String> allowedFactorIds, int ttlSeconds) {
        Objects.requireNonNull(tokenClass, "tokenClass");
        Objects.requireNonNull(userId, "userId");
        if (ttlSeconds <= 0) {
            throw new IllegalArgumentException("ttlSeconds must be positive");
        }
        MfaLockOrder.requireValidOrder(List.of(
                MfaLockOrder.Resource.GLOBAL_POLICY,
                MfaLockOrder.Resource.TENANT_POLICY,
                MfaLockOrder.Resource.USER_ASSURANCE,
                MfaLockOrder.Resource.FLOW_OR_DECISION_OR_CODE));

        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String hash = sha256Hex(raw);
        Instant now = Instant.now();
        long gEpoch = policy == null ? 0L : policy.getGlobalPolicyEpoch();
        long tEpoch = policy == null ? 0L : policy.getTenantPolicyEpoch();

        MfaAuthFlowRecord record = MfaAuthFlowRecord.builder()
                .id(UUID.randomUUID().toString())
                .flowTokenHash(hash)
                .tokenClass(tokenClass)
                .state(MfaAuthFlowState.ACTIVE)
                .tenantId(tenantId)
                .userId(userId)
                .clientId(clientId)
                .globalPolicyEpoch(gEpoch)
                .tenantPolicyEpoch(tEpoch)
                .assuranceEpoch(assuranceEpoch)
                .allowedActions(allowedActions == null ? new ArrayList<>() : new ArrayList<>(allowedActions))
                .allowedFactorIds(allowedFactorIds == null ? new ArrayList<>() : new ArrayList<>(allowedFactorIds))
                .attempts(0)
                .expiresAt(now.plusSeconds(ttlSeconds))
                .createdAt(now)
                .build();
        store.insert(record);
        return MfaIssuedFlow.builder()
                .flowId(record.getId())
                .flowToken(raw)
                .tokenClass(tokenClass)
                .expiresInSeconds(ttlSeconds)
                .build();
    }

    @Override
    public MfaAuthFlowRecord resolveActive(String rawFlowToken) {
        if (rawFlowToken == null || rawFlowToken.isBlank()) {
            return null;
        }
        MfaLockOrder.requireValidOrder(List.of(MfaLockOrder.Resource.FLOW_OR_DECISION_OR_CODE));
        MfaAuthFlowRecord record = store.getByTokenHash(sha256Hex(rawFlowToken));
        if (record == null) {
            return null;
        }
        Instant now = Instant.now();
        if (record.getState() != MfaAuthFlowState.ACTIVE) {
            return null;
        }
        if (record.isExpired(now)) {
            store.casState(record.getFlowTokenHash(), MfaAuthFlowState.ACTIVE, MfaAuthFlowState.EXPIRED);
            record.setState(MfaAuthFlowState.EXPIRED);
            return null;
        }
        return record;
    }

    @Override
    public boolean tryComplete(String rawFlowToken) {
        if (rawFlowToken == null || rawFlowToken.isBlank()) {
            return false;
        }
        MfaLockOrder.requireValidOrder(List.of(MfaLockOrder.Resource.FLOW_OR_DECISION_OR_CODE));
        MfaAuthFlowRecord record = store.getByTokenHash(sha256Hex(rawFlowToken));
        if (record == null) {
            return false;
        }
        if (record.isExpired(Instant.now())) {
            store.casState(record.getFlowTokenHash(), MfaAuthFlowState.ACTIVE, MfaAuthFlowState.EXPIRED);
            return false;
        }
        return store.casState(record.getFlowTokenHash(), MfaAuthFlowState.ACTIVE, MfaAuthFlowState.COMPLETED);
    }

    @Override
    public boolean tryRevertComplete(String rawFlowToken) {
        if (rawFlowToken == null || rawFlowToken.isBlank()) {
            return false;
        }
        MfaAuthFlowRecord record = store.getByTokenHash(sha256Hex(rawFlowToken));
        if (record == null || record.isExpired(Instant.now())) {
            return false;
        }
        return store.casState(record.getFlowTokenHash(), MfaAuthFlowState.COMPLETED, MfaAuthFlowState.ACTIVE);
    }

    @Override
    public void revoke(String rawFlowToken) {
        if (rawFlowToken == null || rawFlowToken.isBlank()) {
            return;
        }
        MfaAuthFlowRecord record = store.getByTokenHash(sha256Hex(rawFlowToken));
        if (record != null) {
            store.casState(record.getFlowTokenHash(), record.getState(), MfaAuthFlowState.REVOKED);
        }
    }

    @Override
    public void clear() {
        store.clear();
    }

    public static String sha256Hex(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
