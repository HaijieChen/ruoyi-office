package cn.iocoder.yudao.module.system.service.mfa;

import cn.iocoder.yudao.module.system.dal.dataobject.mfa.MfaControlStateDO;
import cn.iocoder.yudao.module.system.dal.dataobject.mfa.MfaTenantPolicyDO;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaLifecycleState;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaMode;
import cn.iocoder.yudao.module.system.service.mfa.model.MfaControlTuple;
import cn.iocoder.yudao.module.system.service.mfa.model.MfaPolicySnapshot;
import cn.iocoder.yudao.module.system.service.mfa.support.MfaChecksumUtil;
import cn.iocoder.yudao.module.system.service.mfa.support.MfaLockOrder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Policy Authority 实现（ADR-MFA-v3 §4 / §5 切片 1）。
 */
@Service
@Slf4j
public class MfaPolicyAuthorityImpl implements MfaPolicyAuthority {

    private final MfaAuthorityStore store;
    private final ConcurrentHashMap<String, MfaPolicySnapshot> cache = new ConcurrentHashMap<>();

    public MfaPolicyAuthorityImpl(MfaAuthorityStore store) {
        this.store = store;
    }

    /** 策略面允许的因子类型（与 contract FactorRef 一致）。 */
    private static final Set<String> POLICY_FACTOR_TYPES =
            Set.of("TOTP", "SMS", "EMAIL", "BACKUP_CODE");

    @Override
    public MfaControlTuple readControlTuple() {
        MfaLockOrder.requireValidOrder(List.of(MfaLockOrder.Resource.GLOBAL_POLICY));
        try {
            MfaControlStateDO control = store.getControlState();
            // F-S1-01：control 缺失时仍须扫描已确认策略；存在非 OFF 则 closed，不得 usable OFF
            // 仅「行真正缺失」可合成 clean UNINITIALIZED；持久化行必须完整校验
            if (control == null) {
                if (hasConfirmedNonOffTenants()) {
                    return MfaControlTuple.degraded(0L, "missing-control-with-confirmed-non-off-policy");
                }
                return MfaControlTuple.uninitializedOff();
            }
            // F-R4-01：null/blank lifecycle 不得猜 UNINITIALIZED
            MfaLifecycleState lifecycle = resolveLifecycleStrict(control);
            if (lifecycle == null || lifecycle == MfaLifecycleState.DEGRADED_CLOSED) {
                return MfaControlTuple.degraded(rawEpochOrZero(control),
                        lifecycle == null ? "lifecycle-null-or-blank" : "lifecycle-illegal");
            }

            // F-R3-01 / F-R4-01：持久化 control 一律严格解析，禁止猜 OFF / 折叠 epoch
            MfaMode mode = parsePersistedModeStrict(control);
            if (mode == null || mode == MfaMode.INHERIT) {
                return MfaControlTuple.degraded(rawEpochOrZero(control),
                        "global-mode-illegal:" + control.getGlobalMode());
            }
            if (lifecycle == MfaLifecycleState.UNINITIALIZED) {
                if (mode.isNonOff() || hasConfirmedNonOffTenants()) {
                    return MfaControlTuple.degraded(rawEpochOrZero(control), "uninitialized-but-non-off-present");
                }
                if (mode != MfaMode.OFF) {
                    return MfaControlTuple.degraded(rawEpochOrZero(control), "uninitialized-mode-not-off");
                }
                // F-R4-01：UNINITIALIZED 必须无 ARMED 标记
                if (control.getArmedAt() != null) {
                    return MfaControlTuple.degraded(rawEpochOrZero(control), "uninitialized-armed-marker-present");
                }
            }

            // null epoch/min 对持久化行视为非法（不猜 0），仅允许显式数值
            if (control.getGlobalPolicyEpoch() == null || control.getGlobalMinAcceptedEpoch() == null) {
                return MfaControlTuple.degraded(rawEpochOrZero(control), "epoch-or-min-null");
            }
            long epoch = control.getGlobalPolicyEpoch();
            long min = control.getGlobalMinAcceptedEpoch();
            // F-R2-04 / F-R3-01：负 epoch / min 不一致 → closed
            if (epoch < 0 || min < 0 || min > epoch) {
                return MfaControlTuple.degraded(epoch < 0 ? 0L : epoch, "epoch-invalid");
            }
            // F-R4-01：UNINITIALIZED 的 min 必须为 0（非零 min 与未初始化生命周期不兼容）
            if (lifecycle == MfaLifecycleState.UNINITIALIZED && min != 0L) {
                return MfaControlTuple.degraded(epoch, "uninitialized-nonzero-min");
            }

            Set<String> factors = parseFactors(control.getGlobalAllowedFactors());
            if (!factorsTyped(factors)) {
                return MfaControlTuple.degraded(epoch, "factor-type-illegal");
            }
            String expected = MfaChecksumUtil.computeControl(
                    lifecycle.name(), mode.name(), factors, epoch, min);
            if (control.getChecksum() == null || !control.getChecksum().equals(expected)) {
                return MfaControlTuple.degraded(epoch, "control-checksum-mismatch");
            }
            return MfaControlTuple.builder()
                    .lifecycleState(lifecycle)
                    .globalMode(mode)
                    .globalPolicyEpoch(epoch)
                    .globalMinAcceptedEpoch(min)
                    .checksum(expected)
                    .usable(true)
                    .build();
        } catch (Exception ex) {
            log.error("[mfa][authority] readControlTuple failed", ex);
            return MfaControlTuple.degraded(0L, "read-failure:" + ex.getMessage());
        }
    }

    @Override
    public MfaPolicySnapshot resolveEffectivePolicy(Long tenantId) {
        try {
            MfaLockOrder.requireValidOrder(tenantId == null
                    ? List.of(MfaLockOrder.Resource.GLOBAL_POLICY)
                    : List.of(MfaLockOrder.Resource.GLOBAL_POLICY, MfaLockOrder.Resource.TENANT_POLICY));

            MfaControlTuple tuple = readControlTuple();
            if (!tuple.isUsable()) {
                return MfaPolicySnapshot.degraded(tuple.getGlobalPolicyEpoch(), tuple.getUnusableReason());
            }
            if (tuple.getLifecycleState() == MfaLifecycleState.UNINITIALIZED) {
                MfaPolicySnapshot off = MfaPolicySnapshot.uninitializedOff();
                cache.put(cacheKey(0L, tenantId), off);
                return off;
            }

            String cacheKey = cacheKey(tuple.getGlobalPolicyEpoch(), tenantId);
            MfaPolicySnapshot cached = cache.get(cacheKey);
            if (cached != null && cached.isUsable()
                    && cached.getGlobalPolicyEpoch() == tuple.getGlobalPolicyEpoch()) {
                return cached;
            }

            MfaMode effective = tuple.getGlobalMode();
            Set<String> effectiveFactors = parseFactors(
                    store.getControlState() != null ? store.getControlState().getGlobalAllowedFactors() : null);
            // re-read control for factors (already validated in tuple)
            MfaControlStateDO control = store.getControlState();
            effectiveFactors = parseFactors(control.getGlobalAllowedFactors());

            long tenantEpoch = tuple.getGlobalPolicyEpoch();
            long tenantMin = tuple.getGlobalMinAcceptedEpoch();

            // §4.1 层级：global OFF 最高；REQUIRED 不可降级；OPTIONAL 可下放租户
            if (tenantId != null && effective == MfaMode.OPTIONAL) {
                MfaTenantPolicyDO tenant = store.getTenantPolicy(tenantId);
                if (tenant == null || !Boolean.TRUE.equals(tenant.getConfirmed())) {
                    // ARMED 后缺租户行 = closed（§4.2 硬规则 6）
                    return MfaPolicySnapshot.degraded(tuple.getGlobalPolicyEpoch(), "tenant-policy-missing");
                }
                MfaMode tenantMode = MfaMode.parseStrict(tenant.getMode());
                if (tenantMode == null) {
                    return MfaPolicySnapshot.degraded(tuple.getGlobalPolicyEpoch(), "tenant-mode-illegal");
                }
                long tEpoch = nz(tenant.getPolicyEpoch());
                Set<String> tFactors = parseFactors(tenant.getAllowedFactors());
                String tExpected = MfaChecksumUtil.compute(tenantMode.name(), tFactors, tEpoch);
                if (tenant.getChecksum() == null || !tenant.getChecksum().equals(tExpected)) {
                    return MfaPolicySnapshot.degraded(tuple.getGlobalPolicyEpoch(), "tenant-checksum-mismatch");
                }
                tenantEpoch = tEpoch;
                tenantMin = nz(tenant.getMinAcceptedEpoch());
                if (tenantMode != MfaMode.INHERIT) {
                    effective = tenantMode;
                }
                // 因子交集；空保持空
                if (tenant.getAllowedFactors() != null) {
                    Set<String> intersection = new LinkedHashSet<>(effectiveFactors);
                    intersection.retainAll(tFactors);
                    effectiveFactors = intersection;
                }
            } else if (tenantId != null && effective == MfaMode.REQUIRED) {
                MfaTenantPolicyDO tenant = store.getTenantPolicy(tenantId);
                if (tenant != null && Boolean.TRUE.equals(tenant.getConfirmed())) {
                    MfaMode tenantMode = MfaMode.parseStrict(tenant.getMode());
                    if (tenantMode == null) {
                        return MfaPolicySnapshot.degraded(tuple.getGlobalPolicyEpoch(), "tenant-mode-illegal");
                    }
                    long tEpoch = nz(tenant.getPolicyEpoch());
                    Set<String> tFactors = parseFactors(tenant.getAllowedFactors());
                    String tExpected = MfaChecksumUtil.compute(tenantMode.name(), tFactors, tEpoch);
                    if (tenant.getChecksum() == null || !tenant.getChecksum().equals(tExpected)) {
                        return MfaPolicySnapshot.degraded(tuple.getGlobalPolicyEpoch(), "tenant-checksum-mismatch");
                    }
                    tenantEpoch = tEpoch;
                    tenantMin = nz(tenant.getMinAcceptedEpoch());
                    if (tenant.getAllowedFactors() != null) {
                        Set<String> intersection = new LinkedHashSet<>(effectiveFactors);
                        intersection.retainAll(tFactors);
                        effectiveFactors = intersection;
                    }
                } else if (tenant == null) {
                    return MfaPolicySnapshot.degraded(tuple.getGlobalPolicyEpoch(), "tenant-policy-missing");
                }
            }
            // global OFF：忽略租户

            MfaPolicySnapshot snap = MfaPolicySnapshot.builder()
                    .lifecycleState(MfaLifecycleState.ARMED)
                    .globalPolicyEpoch(tuple.getGlobalPolicyEpoch())
                    .tenantPolicyEpoch(tenantEpoch)
                    .globalMinAcceptedEpoch(tuple.getGlobalMinAcceptedEpoch())
                    .tenantMinAcceptedEpoch(tenantMin)
                    .mode(effective)
                    .allowedFactors(effectiveFactors)
                    .checksum(tuple.getChecksum())
                    .loadedAt(Instant.now())
                    .usable(true)
                    .build();
            cache.put(cacheKey, snap);
            return snap;
        } catch (Exception ex) {
            log.error("[mfa][authority] resolveEffectivePolicy failed", ex);
            return MfaPolicySnapshot.degraded(0L, "read-failure:" + ex.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public long confirmGlobalPolicy(MfaMode mode, Set<String> allowedFactors) {
        Objects.requireNonNull(mode, "mode");
        if (mode == MfaMode.INHERIT) {
            throw new IllegalArgumentException("global mode cannot be INHERIT");
        }
        MfaLockOrder.requireValidOrder(List.of(MfaLockOrder.Resource.GLOBAL_POLICY));
        // F-R5-03：写前 typed factor 白名单；非法不落库
        Set<String> factors = requireTypedFactors(allowedFactors);

        MfaControlStateDO control = store.getControlState();
        long base = control == null || control.getGlobalPolicyEpoch() == null ? 0L : control.getGlobalPolicyEpoch();
        if (base < 0) {
            throw new IllegalStateException("control epoch corrupt; refuse global confirm");
        }
        long next = nextEpoch(base);

        // F-R5-01：ARMED 永久单调——一旦 armed_at / lifecycle=ARMED / 非 OFF 历史存在，
        // 显式 OFF 修复只能写 ARMED+OFF，不得清 armed_at 或回落 UNINITIALIZED
        boolean irreversibleArmed = hasIrreversibleArmedEvidence(control);
        MfaLifecycleState lifecycle;
        LocalDateTime armedAt;
        if (irreversibleArmed || mode.isNonOff()) {
            lifecycle = MfaLifecycleState.ARMED;
            armedAt = control != null && control.getArmedAt() != null
                    ? control.getArmedAt() : LocalDateTime.now();
        } else {
            lifecycle = MfaLifecycleState.UNINITIALIZED;
            armedAt = null;
        }

        long minAccepted = control == null || control.getGlobalMinAcceptedEpoch() == null
                ? 0L : control.getGlobalMinAcceptedEpoch();
        if (minAccepted < 0) {
            minAccepted = 0L;
        }
        // 变严：提高 min_accepted
        if (mode == MfaMode.REQUIRED || mode == MfaMode.OPTIONAL) {
            MfaMode prev = control == null ? null : MfaMode.parseStrict(control.getGlobalMode());
            if (prev == MfaMode.OFF || prev == null || (prev == MfaMode.OPTIONAL && mode == MfaMode.REQUIRED)) {
                minAccepted = next;
            }
        }
        // ARMED+OFF 时 min 不得被清零到小于既有值
        if (lifecycle == MfaLifecycleState.ARMED && minAccepted < 0) {
            minAccepted = 0L;
        }

        String checksum = MfaChecksumUtil.computeControl(
                lifecycle.name(), mode.name(), factors, next, minAccepted);

        MfaControlStateDO nextState = MfaControlStateDO.builder()
                .id(MfaControlStateDO.SINGLETON_ID)
                .lifecycleState(lifecycle.name())
                .globalMode(mode.name())
                .globalAllowedFactors(String.join(",", factors))
                .globalPolicyEpoch(next)
                .globalMinAcceptedEpoch(minAccepted)
                .armedAt(armedAt)
                // F-R4-02/03：应用写即表示 v3 权威已接管，禁止后续 legacy 回写
                .checksum(checksum)
                .legacyGlobalMerged(true)
                .build();
        store.saveControlState(nextState);
        invalidateCache();
        return next;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public long confirmTenantPolicy(Long tenantId, MfaMode mode, Set<String> allowedFactors) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(mode, "mode");
        MfaLockOrder.requireValidOrder(List.of(
                MfaLockOrder.Resource.GLOBAL_POLICY, MfaLockOrder.Resource.TENANT_POLICY));
        // F-R5-03：写前 typed factor；非法不推进 epoch、不落 tenant/control
        Set<String> factors = requireTypedFactors(allowedFactors);

        // F-R2-01：tenant 写不得修复/降级损坏的 global control
        // 仅允许：真正空库（无 control 且无已确认非 OFF）或 control 已完整可用 / clean UNINITIALIZED
        MfaControlStateDO control = store.getControlState();
        MfaControlTuple tuple = readControlTuple();
        boolean cleanEmpty = control == null && !hasConfirmedNonOffTenants();
        boolean cleanUninitialized = tuple.isUsable()
                && tuple.getLifecycleState() == MfaLifecycleState.UNINITIALIZED;
        boolean controlUsableArmedOrOff = tuple.isUsable()
                && tuple.getLifecycleState() == MfaLifecycleState.ARMED;
        if (!cleanEmpty && !cleanUninitialized && !controlUsableArmedOrOff) {
            throw new IllegalStateException(
                    "cannot confirm tenant policy while control plane unusable: "
                            + tuple.getUnusableReason());
        }

        MfaMode globalMode = cleanEmpty || cleanUninitialized
                ? MfaMode.OFF
                : tuple.getGlobalMode();
        if (globalMode == null) {
            throw new IllegalStateException("global mode missing on usable control");
        }
        if (globalMode == MfaMode.REQUIRED && mode != MfaMode.REQUIRED && mode != MfaMode.INHERIT) {
            throw new IllegalArgumentException("global REQUIRED forbids tenant downgrade");
        }

        Set<String> globalFactors = cleanEmpty || cleanUninitialized
                ? Collections.emptySet()
                : parseFactors(control != null ? control.getGlobalAllowedFactors() : null);

        long base = cleanEmpty ? 0L : tuple.getGlobalPolicyEpoch();
        if (base < 0) {
            throw new IllegalStateException("control epoch corrupt; refuse tenant confirm");
        }
        long nextGlobal = nextEpoch(base);

        // 任一已确认非 OFF / 已 ARMED 证据 → 保持/进入 ARMED（F-R5-01 单调）
        boolean arm = mode.isNonOff()
                || globalMode.isNonOff()
                || controlUsableArmedOrOff
                || hasIrreversibleArmedEvidence(control)
                || hasConfirmedNonOffTenants();
        MfaLifecycleState nextLifecycle = arm ? MfaLifecycleState.ARMED : MfaLifecycleState.UNINITIALIZED;
        LocalDateTime armedAt = nextLifecycle == MfaLifecycleState.ARMED
                ? (control != null && control.getArmedAt() != null
                ? control.getArmedAt() : LocalDateTime.now())
                : null;

        long minAccepted = cleanEmpty || cleanUninitialized ? 0L : tuple.getGlobalMinAcceptedEpoch();
        if (minAccepted < 0) {
            throw new IllegalStateException("control min epoch corrupt");
        }
        if (mode.isNonOff() || globalMode.isNonOff()) {
            minAccepted = Math.max(minAccepted, nextGlobal);
        }

        String controlChecksum = MfaChecksumUtil.computeControl(
                nextLifecycle.name(), globalMode.name(), globalFactors, nextGlobal, minAccepted);
        MfaControlStateDO nextState = MfaControlStateDO.builder()
                .id(MfaControlStateDO.SINGLETON_ID)
                .lifecycleState(nextLifecycle.name())
                .globalMode(globalMode.name())
                .globalAllowedFactors(String.join(",", globalFactors))
                .globalPolicyEpoch(nextGlobal)
                .globalMinAcceptedEpoch(minAccepted)
                .armedAt(armedAt)
                .checksum(controlChecksum)
                .legacyGlobalMerged(true)
                .build();
        store.saveControlState(nextState);

        String tenantChecksum = MfaChecksumUtil.compute(mode.name(), factors, nextGlobal);
        MfaTenantPolicyDO policy = MfaTenantPolicyDO.builder()
                .tenantId(tenantId)
                .mode(mode.name())
                .allowedFactors(String.join(",", factors))
                .policyEpoch(nextGlobal)
                .minAcceptedEpoch(nextGlobal)
                .checksum(tenantChecksum)
                .confirmed(true)
                .build();
        store.saveTenantPolicy(policy);
        invalidateCache();
        return nextGlobal;
    }

    @Override
    public boolean isReady() {
        MfaControlTuple t = readControlTuple();
        return t.isUsable() && t.getLifecycleState() != MfaLifecycleState.DEGRADED_CLOSED;
    }

    @Override
    public void invalidateCache() {
        cache.clear();
    }

    private boolean hasConfirmedNonOff(MfaControlStateDO control) {
        if (control != null) {
            MfaMode mode = MfaMode.parseStrict(control.getGlobalMode());
            // 非法 mode 也视为“不能证明 OFF”
            if (control.getGlobalMode() != null && !control.getGlobalMode().isBlank()
                    && (mode == null || mode == MfaMode.INHERIT)) {
                return true;
            }
            if (mode != null && mode.isNonOff()) {
                return true;
            }
        }
        return hasConfirmedNonOffTenants();
    }

    private boolean hasConfirmedNonOffTenants() {
        for (MfaTenantPolicyDO t : store.listConfirmedTenantPolicies()) {
            if (!Boolean.TRUE.equals(t.getConfirmed())) {
                continue;
            }
            MfaMode tm = MfaMode.parseStrict(t.getMode());
            // 非法 tenant mode 或非 OFF → 不能证明安全 OFF
            if (tm == null || tm.isNonOff()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 严格解析 lifecycle：null/blank → null（调用方 closed）；非法枚举 → DEGRADED_CLOSED。
     * 不得把 null 猜成 UNINITIALIZED（F-R4-01）。
     */
    private MfaLifecycleState resolveLifecycleStrict(MfaControlStateDO control) {
        if (control == null || control.getLifecycleState() == null || control.getLifecycleState().isBlank()) {
            return null;
        }
        try {
            return MfaLifecycleState.valueOf(control.getLifecycleState().trim());
        } catch (Exception ex) {
            return MfaLifecycleState.DEGRADED_CLOSED;
        }
    }

    /** 持久化 mode：null/blank/非法一律 null，不得猜 OFF（F-R4-01）。 */
    private static MfaMode parsePersistedModeStrict(MfaControlStateDO control) {
        if (control.getGlobalMode() == null || control.getGlobalMode().isBlank()) {
            return null;
        }
        return MfaMode.parseStrict(control.getGlobalMode().trim());
    }

    private static boolean factorsTyped(Set<String> factors) {
        if (factors == null || factors.isEmpty()) {
            return true;
        }
        for (String f : factors) {
            if (f == null || !POLICY_FACTOR_TYPES.contains(f.toUpperCase(java.util.Locale.ROOT))) {
                return false;
            }
        }
        return true;
    }

    /**
     * F-R5-03：写前 canonicalize + 白名单；非法立即拒绝（不落库）。
     */
    private static Set<String> requireTypedFactors(Set<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> out = new LinkedHashSet<>();
        for (String f : raw) {
            if (f == null || f.isBlank()) {
                throw new IllegalArgumentException("factor type blank");
            }
            String n = f.trim().toUpperCase(java.util.Locale.ROOT);
            if (!POLICY_FACTOR_TYPES.contains(n)) {
                throw new IllegalArgumentException("unknown factor type: " + f);
            }
            out.add(n);
        }
        return out;
    }

    /**
     * F-R5-01：不可逆 ARMED 证据——armed_at、lifecycle 字符串 ARMED、或已确认非 OFF 策略。
     * 存在任一证据时，global/tenant 写不得回落 UNINITIALIZED 或清除 armed_at。
     */
    private boolean hasIrreversibleArmedEvidence(MfaControlStateDO control) {
        if (control != null) {
            if (control.getArmedAt() != null) {
                return true;
            }
            if (control.getLifecycleState() != null
                    && MfaLifecycleState.ARMED.name().equalsIgnoreCase(control.getLifecycleState().trim())) {
                return true;
            }
            MfaMode m = control.getGlobalMode() == null ? null : MfaMode.parseStrict(control.getGlobalMode());
            if (m != null && m.isNonOff()) {
                return true;
            }
        }
        return hasConfirmedNonOffTenants();
    }

    private static long rawEpochOrZero(MfaControlStateDO control) {
        if (control == null || control.getGlobalPolicyEpoch() == null) {
            return 0L;
        }
        long e = control.getGlobalPolicyEpoch();
        return e < 0 ? 0L : e;
    }

    private static long nz(Long v) {
        return v == null ? 0L : v;
    }

    /** F-R2-04：checked increment，禁止绕回负值 */
    private static long nextEpoch(long current) {
        if (current < 0) {
            throw new IllegalStateException("epoch negative");
        }
        if (current == Long.MAX_VALUE) {
            throw new IllegalStateException("epoch overflow");
        }
        return current + 1;
    }

    private static String cacheKey(long epoch, Long tenantId) {
        return epoch + ":" + (tenantId == null ? "g" : tenantId);
    }

    private static Set<String> parseFactors(String raw) {
        if (raw == null || raw.isBlank()) {
            return Collections.emptySet();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
