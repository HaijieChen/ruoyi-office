package cn.iocoder.yudao.module.system.service.mfa;

import cn.iocoder.yudao.module.system.service.mfa.enums.MfaAuthFlowState;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaFlowTokenClass;
import cn.iocoder.yudao.module.system.service.mfa.model.MfaAuthFlowRecord;
import cn.iocoder.yudao.module.system.service.mfa.model.MfaIssuedFlow;
import cn.iocoder.yudao.module.system.service.mfa.model.MfaPolicySnapshot;
import cn.iocoder.yudao.module.system.service.mfa.support.MfaLockOrder;
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
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存权威实现（切片 2）：生产可替换为 DB 行锁实现，锁序与 CAS 语义保持不变。
 */
@Service
public class MfaAuthFlowServiceImpl implements MfaAuthFlowService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final Map<String, MfaAuthFlowRecord> byHash = new ConcurrentHashMap<>();

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
        byHash.put(hash, record);
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
        String hash = sha256Hex(rawFlowToken);
        MfaAuthFlowRecord record = byHash.get(hash);
        if (record == null) {
            return null;
        }
        Instant now = Instant.now();
        if (record.getState() != MfaAuthFlowState.ACTIVE) {
            return null;
        }
        if (record.isExpired(now)) {
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
        String hash = sha256Hex(rawFlowToken);
        MfaAuthFlowRecord record = byHash.get(hash);
        if (record == null) {
            return false;
        }
        synchronized (record) {
            if (record.getState() != MfaAuthFlowState.ACTIVE) {
                return false;
            }
            if (record.isExpired(Instant.now())) {
                record.setState(MfaAuthFlowState.EXPIRED);
                return false;
            }
            record.setState(MfaAuthFlowState.COMPLETED);
            return true;
        }
    }

    @Override
    public void revoke(String rawFlowToken) {
        if (rawFlowToken == null || rawFlowToken.isBlank()) {
            return;
        }
        MfaAuthFlowRecord record = byHash.get(sha256Hex(rawFlowToken));
        if (record != null && record.getState() == MfaAuthFlowState.ACTIVE) {
            record.setState(MfaAuthFlowState.REVOKED);
        }
    }

    @Override
    public void clear() {
        byHash.clear();
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
