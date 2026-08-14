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

    @Override
    public MfaControlTuple readControlTuple() {
        MfaLockOrder.requireValidOrder(List.of(MfaLockOrder.Resource.GLOBAL_POLICY));
        try {
            MfaControlStateDO control = store.getControlState();
            // F-S1-01：control 缺失时仍须扫描已确认策略；存在非 OFF 则 closed，不得 usable OFF
            if (control == null) {
                if (hasConfirmedNonOffTenants()) {
                    return MfaControlTuple.degraded(0L, "missing-control-with-confirmed-non-off-policy");
                }
                return MfaControlTuple.uninitializedOff();
            }
            MfaLifecycleState lifecycle = resolveLifecycle(control);
            if (lifecycle == MfaLifecycleState.DEGRADED_CLOSED) {
                return MfaControlTuple.degraded(nz(control.getGlobalPolicyEpoch()), "lifecycle-illegal");
            }
            if (lifecycle == MfaLifecycleState.UNINITIALIZED) {
                if (hasConfirmedNonOff(control)) {
                    return MfaControlTuple.degraded(nz(control.getGlobalPolicyEpoch()), "uninitialized-but-non-off-present");
                }
                // F-S1-01：UNINITIALIZED 下非法 global_mode 不得回落 OFF
                if (control.getGlobalMode() != null && !control.getGlobalMode().isBlank()) {
                    MfaMode modeProbe = MfaMode.parseStrict(control.getGlobalMode());
                    if (modeProbe == null || modeProbe == MfaMode.INHERIT) {
                        return MfaControlTuple.degraded(nz(control.getGlobalPolicyEpoch()),
                                "uninitialized-global-mode-illegal:" + control.getGlobalMode());
                    }
                }
                return MfaControlTuple.uninitializedOff();
            }
            MfaMode mode = MfaMode.parseStrict(control.getGlobalMode());
            if (mode == null || mode == MfaMode.INHERIT) {
                return MfaControlTuple.degraded(nz(control.getGlobalPolicyEpoch()), "global-mode-illegal");
            }
            Set<String> factors = parseFactors(control.getGlobalAllowedFactors());
            long epoch = nz(control.getGlobalPolicyEpoch());
            long min = nz(control.getGlobalMinAcceptedEpoch());
            // F-R2-04：负 epoch / min 不一致 → closed
            if (epoch < 0 || min < 0 || min > epoch) {
                return MfaControlTuple.degraded(epoch < 0 ? 0L : epoch, "epoch-invalid");
            }
            String expected = MfaChecksumUtil.computeControl(
                    lifecycle.name(), mode.name(), factors, epoch, min);
            if (control.getChecksum() == null || !control.getChecksum().equals(expected)) {
                return MfaControlTuple.degraded(epoch, "control-checksum-mismatch");
            }
            return MfaControlTuple.builder()
                    .lifecycleState(MfaLifecycleState.ARMED)
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
        Set<String> factors = allowedFactors == null ? Collections.emptySet() : allowedFactors;

        MfaControlStateDO control = store.getControlState();
        // 受控 global repair：允许从 DEGRADED 用显式合法 mode 修复；写前仍校验 epoch 单调
        long base = control == null || control.getGlobalPolicyEpoch() == null ? 0L : control.getGlobalPolicyEpoch();
        if (base < 0) {
            throw new IllegalStateException("control epoch corrupt; refuse global confirm");
        }
        long next = nextEpoch(base);

        MfaLifecycleState lifecycle = resolveLifecycle(control);
        if (lifecycle == MfaLifecycleState.DEGRADED_CLOSED) {
            lifecycle = MfaLifecycleState.ARMED; // 显式合法写修复
        } else if (lifecycle == MfaLifecycleState.ARMED) {
            // keep
        } else if (mode.isNonOff()) {
            lifecycle = MfaLifecycleState.ARMED;
        } else {
            lifecycle = MfaLifecycleState.UNINITIALIZED;
        }
        if (control != null && MfaLifecycleState.ARMED.name().equals(control.getLifecycleState())) {
            lifecycle = MfaLifecycleState.ARMED;
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

        String checksum = MfaChecksumUtil.computeControl(
                lifecycle.name(), mode.name(), factors, next, minAccepted);

        MfaControlStateDO nextState = MfaControlStateDO.builder()
                .id(MfaControlStateDO.SINGLETON_ID)
                .lifecycleState(lifecycle.name())
                .globalMode(mode.name())
                .globalAllowedFactors(String.join(",", factors))
                .globalPolicyEpoch(next)
                .globalMinAcceptedEpoch(minAccepted)
                .armedAt(lifecycle == MfaLifecycleState.ARMED
                        ? (control != null && control.getArmedAt() != null
                        ? control.getArmedAt() : LocalDateTime.now())
                        : null)
                .checksum(checksum)
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
        Set<String> factors = allowedFactors == null ? Collections.emptySet() : allowedFactors;

        long base = cleanEmpty ? 0L : tuple.getGlobalPolicyEpoch();
        if (base < 0) {
            throw new IllegalStateException("control epoch corrupt; refuse tenant confirm");
        }
        long nextGlobal = nextEpoch(base);

        // 任一已确认非 OFF（含本事务 tenant）或已 ARMED → 保持/进入 ARMED
        boolean arm = mode.isNonOff()
                || globalMode.isNonOff()
                || controlUsableArmedOrOff
                || hasConfirmedNonOffTenants();
        MfaLifecycleState nextLifecycle = arm ? MfaLifecycleState.ARMED : MfaLifecycleState.UNINITIALIZED;

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
                .armedAt(nextLifecycle == MfaLifecycleState.ARMED
                        ? (control != null && control.getArmedAt() != null
                        ? control.getArmedAt() : LocalDateTime.now())
                        : null)
                .checksum(controlChecksum)
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

    private MfaLifecycleState resolveLifecycle(MfaControlStateDO control) {
        if (control == null || control.getLifecycleState() == null) {
            return MfaLifecycleState.UNINITIALIZED;
        }
        try {
            return MfaLifecycleState.valueOf(control.getLifecycleState());
        } catch (Exception ex) {
            return MfaLifecycleState.DEGRADED_CLOSED;
        }
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
