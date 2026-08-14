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
import java.util.concurrent.atomic.AtomicReference;

/**
 * Auth flow 权威（切片 2 修复）：
 * <ul>
 *   <li>进程内共享 ConcurrentHashMap（多 Spring bean / 同 JVM 多实例构造共享同一 store）</li>
 *   <li>state 使用 AtomicReference CAS 完成 ACTIVE→COMPLETED</li>
 *   <li>生产多节点应替换为 MySQL 行锁 + CAS（见 system_mfa_v3_slice2_flow.sql）</li>
 * </ul>
 */
@Service
public class MfaAuthFlowServiceImpl implements MfaAuthFlowService {

    private static final SecureRandom RANDOM = new SecureRandom();
    /** 同 JVM 共享；跨 JVM 由 DB 实现承接 */
    private static final ConcurrentHashMap<String, StoredFlow> SHARED = new ConcurrentHashMap<>();

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
        SHARED.put(hash, new StoredFlow(record, new AtomicReference<>(MfaAuthFlowState.ACTIVE)));
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
        StoredFlow stored = SHARED.get(sha256Hex(rawFlowToken));
        if (stored == null) {
            return null;
        }
        Instant now = Instant.now();
        MfaAuthFlowState st = stored.state.get();
        if (st != MfaAuthFlowState.ACTIVE) {
            return null;
        }
        if (stored.record.isExpired(now)) {
            stored.state.compareAndSet(MfaAuthFlowState.ACTIVE, MfaAuthFlowState.EXPIRED);
            stored.record.setState(MfaAuthFlowState.EXPIRED);
            return null;
        }
        // 返回防御拷贝语义：state 以 AtomicReference 为准
        stored.record.setState(st);
        return stored.record;
    }

    @Override
    public boolean tryComplete(String rawFlowToken) {
        if (rawFlowToken == null || rawFlowToken.isBlank()) {
            return false;
        }
        MfaLockOrder.requireValidOrder(List.of(MfaLockOrder.Resource.FLOW_OR_DECISION_OR_CODE));
        StoredFlow stored = SHARED.get(sha256Hex(rawFlowToken));
        if (stored == null) {
            return false;
        }
        if (stored.record.isExpired(Instant.now())) {
            stored.state.compareAndSet(MfaAuthFlowState.ACTIVE, MfaAuthFlowState.EXPIRED);
            stored.record.setState(MfaAuthFlowState.EXPIRED);
            return false;
        }
        // 真实 CAS：仅 ACTIVE→COMPLETED
        if (!stored.state.compareAndSet(MfaAuthFlowState.ACTIVE, MfaAuthFlowState.COMPLETED)) {
            return false;
        }
        stored.record.setState(MfaAuthFlowState.COMPLETED);
        return true;
    }

    @Override
    public void revoke(String rawFlowToken) {
        if (rawFlowToken == null || rawFlowToken.isBlank()) {
            return;
        }
        StoredFlow stored = SHARED.get(sha256Hex(rawFlowToken));
        if (stored != null) {
            stored.state.set(MfaAuthFlowState.REVOKED);
            stored.record.setState(MfaAuthFlowState.REVOKED);
        }
    }

    @Override
    public void clear() {
        SHARED.clear();
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

    private record StoredFlow(MfaAuthFlowRecord record, AtomicReference<MfaAuthFlowState> state) {
    }
}
