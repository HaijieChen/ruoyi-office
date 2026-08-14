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
            if (control == null) {
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
                return MfaControlTuple.uninitializedOff();
            }
            MfaMode mode = MfaMode.parseStrict(control.getGlobalMode());
            if (mode == null || mode == MfaMode.INHERIT) {
                return MfaControlTuple.degraded(nz(control.getGlobalPolicyEpoch()), "global-mode-illegal");
            }
            Set<String> factors = parseFactors(control.getGlobalAllowedFactors());
            long epoch = nz(control.getGlobalPolicyEpoch());
            long min = nz(control.getGlobalMinAcceptedEpoch());
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
        long base = control == null || control.getGlobalPolicyEpoch() == null ? 0L : control.getGlobalPolicyEpoch();
        long next = base + 1;

        MfaLifecycleState lifecycle = resolveLifecycle(control);
        if (lifecycle == MfaLifecycleState.DEGRADED_CLOSED) {
            lifecycle = MfaLifecycleState.ARMED;
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

        MfaPolicySnapshot global = resolveEffectivePolicy(null);
        if (!global.isUsable() || global.getLifecycleState() == MfaLifecycleState.DEGRADED_CLOSED) {
            throw new IllegalStateException("cannot update tenant policy while control plane closed");
        }
        if (global.getMode() == MfaMode.REQUIRED && mode != MfaMode.REQUIRED && mode != MfaMode.INHERIT) {
            throw new IllegalArgumentException("global REQUIRED forbids tenant downgrade");
        }

        // 推进 global epoch，保持门闩一致（切片 1 简化：与租户同事务递增）
        long nextGlobal = confirmGlobalPolicy(
                global.getMode() == null ? MfaMode.OFF : global.getMode(),
                global.getAllowedFactors());

        Set<String> factors = allowedFactors == null ? Collections.emptySet() : allowedFactors;
        long minAccepted = nextGlobal;
        String checksum = MfaChecksumUtil.compute(mode.name(), factors, nextGlobal);
        MfaTenantPolicyDO policy = MfaTenantPolicyDO.builder()
                .tenantId(tenantId)
                .mode(mode.name())
                .allowedFactors(String.join(",", factors))
                .policyEpoch(nextGlobal)
                .minAcceptedEpoch(minAccepted)
                .checksum(checksum)
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
        MfaMode mode = MfaMode.parseStrict(control.getGlobalMode());
        if (mode != null && mode.isNonOff()) {
            return true;
        }
        for (MfaTenantPolicyDO t : store.listConfirmedTenantPolicies()) {
            MfaMode tm = MfaMode.parseStrict(t.getMode());
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
