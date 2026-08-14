package cn.iocoder.yudao.module.system.service.mfa;

import cn.iocoder.yudao.module.system.dal.dataobject.mfa.MfaControlStateDO;
import cn.iocoder.yudao.module.system.dal.dataobject.mfa.MfaGlobalPolicyDO;
import cn.iocoder.yudao.module.system.dal.dataobject.mfa.MfaTenantPolicyDO;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaLifecycleState;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaMode;
import cn.iocoder.yudao.module.system.service.mfa.model.MfaPolicySnapshot;
import cn.iocoder.yudao.module.system.service.mfa.support.MfaChecksumUtil;
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * MFA 控制面实现：UNINITIALIZED / ARMED / DEGRADED_CLOSED。
 * <p>
 * 硬规则：
 * <ul>
 *   <li>UNINITIALIZED 不是异常兜底；仅无 ARMED 且无已确认非 OFF 时可读 OFF</li>
 *   <li>ARMED 单调永久；禁止 ARMED→UNINITIALIZED</li>
 *   <li>ARMED 后丢失/非法/超时 → DEGRADED_CLOSED，绝不回落 OFF</li>
 *   <li>缓存含 lifecycleState+policyVersion+mode+checksum；版本门闩以主库为准</li>
 * </ul>
 */
@Service
@Slf4j
public class MfaPolicyControlServiceImpl implements MfaPolicyControlService {

    private final MfaPolicyStore store;
    private final AtomicReference<MfaPolicySnapshot> cache = new AtomicReference<>();

    public MfaPolicyControlServiceImpl(MfaPolicyStore store) {
        this.store = store;
    }

    @Override
    public MfaPolicySnapshot resolveEffectivePolicy(Long tenantId) {
        try {
            long gateVersion = readPrimaryPolicyVersionGateInternal();
            MfaPolicySnapshot cached = cache.get();
            if (cached != null && cached.isUsable()
                    && cached.getPolicyVersion() == gateVersion
                    && cached.getLifecycleState() != MfaLifecycleState.DEGRADED_CLOSED) {
                // 租户覆盖需在版本一致时仍按租户解析；缓存仅存全局生效快照时重载租户
                return loadAndCache(tenantId, gateVersion);
            }
            return loadAndCache(tenantId, gateVersion);
        } catch (Exception ex) {
            log.error("[mfa][policy] resolve failed", ex);
            // 读失败时无法证明仍是「空库 UNINITIALIZED」：必须 fail-closed，绝不能回落 OFF
            long version = safeVersion();
            MfaPolicySnapshot degraded = MfaPolicySnapshot.degraded(
                    version < 0 ? 0L : version, "read-failure:" + ex.getMessage());
            cache.set(degraded);
            return degraded;
        }
    }

    private MfaPolicySnapshot loadAndCache(Long tenantId, long gateVersion) {
        MfaControlStateDO control = store.getControlState();
        MfaLifecycleState lifecycle = resolveLifecycle(control);

        if (lifecycle == MfaLifecycleState.UNINITIALIZED) {
            // 扫描是否已有非 OFF 已确认策略（异常恢复路径）
            if (hasConfirmedNonOff()) {
                // 发现非 OFF 必须转 ARMED（数据修复路径）
                armFromScan(gateVersion);
                lifecycle = MfaLifecycleState.ARMED;
            } else {
                MfaPolicySnapshot snap = MfaPolicySnapshot.uninitializedOff();
                cache.set(snap);
                return snap;
            }
        }

        // ARMED / DEGRADED_CLOSED 路径：必须完整加载
        MfaGlobalPolicyDO global = store.getGlobalPolicy();
        if (global == null) {
            return degradeAndCache(gateVersion, "global-policy-missing");
        }
        MfaMode globalMode = MfaMode.parseStrict(global.getMode());
        if (globalMode == null || globalMode == MfaMode.INHERIT) {
            return degradeAndCache(gateVersion, "global-mode-illegal:" + global.getMode());
        }
        if (!Boolean.TRUE.equals(global.getConfirmed())) {
            return degradeAndCache(gateVersion, "global-not-confirmed");
        }
        long policyVersion = global.getPolicyVersion() == null ? 0L : global.getPolicyVersion();
        if (policyVersion != gateVersion && control != null
                && control.getPolicyVersion() != null
                && !control.getPolicyVersion().equals(policyVersion)) {
            // 版本不一致：以门闩为准，策略行版本不符则 closed
            return degradeAndCache(gateVersion, "version-mismatch gate=" + gateVersion + " policy=" + policyVersion);
        }
        Set<String> factors = parseFactors(global.getAllowedFactors());
        String expectedChecksum = MfaChecksumUtil.compute(globalMode.name(), factors, policyVersion);
        if (global.getChecksum() == null || !global.getChecksum().equals(expectedChecksum)) {
            return degradeAndCache(gateVersion, "checksum-mismatch");
        }
        if (control != null && control.getChecksum() != null
                && !control.getChecksum().equals(expectedChecksum)
                && lifecycle == MfaLifecycleState.ARMED) {
            // control checksum 应与当前生效策略对齐；显式 OFF 后也会更新
            // 若不匹配仍允许用策略行 checksum 校验通过的数据，但门闩 checksum 异常时 closed
            if (control.getPolicyVersion() != null && control.getPolicyVersion().equals(policyVersion)) {
                return degradeAndCache(gateVersion, "control-checksum-mismatch");
            }
        }

        MfaMode effective = globalMode;
        Set<String> effectiveFactors = factors;

        if (tenantId != null && globalMode != MfaMode.REQUIRED) {
            MfaTenantPolicyDO tenant = store.getTenantPolicy(tenantId);
            if (tenant != null && Boolean.TRUE.equals(tenant.getConfirmed())) {
                MfaMode tenantMode = MfaMode.parseStrict(tenant.getMode());
                if (tenantMode == null) {
                    return degradeAndCache(gateVersion, "tenant-mode-illegal");
                }
                if (tenantMode != MfaMode.INHERIT) {
                    if (globalMode == MfaMode.OPTIONAL || globalMode == MfaMode.OFF) {
                        effective = tenantMode;
                    }
                }
                Set<String> tenantFactors = parseFactors(tenant.getAllowedFactors());
                if (!tenantFactors.isEmpty()) {
                    // 有效因子 = 全局 ∩ 租户
                    Set<String> intersection = new LinkedHashSet<>(effectiveFactors);
                    intersection.retainAll(tenantFactors);
                    effectiveFactors = intersection.isEmpty() ? effectiveFactors : intersection;
                }
            }
        }

        // 使用门闩版本作为快照版本
        long snapVersion = control != null && control.getPolicyVersion() != null
                ? control.getPolicyVersion() : policyVersion;

        MfaPolicySnapshot snap = MfaPolicySnapshot.builder()
                .lifecycleState(MfaLifecycleState.ARMED)
                .policyVersion(snapVersion)
                .mode(effective)
                .allowedFactors(effectiveFactors)
                .checksum(expectedChecksum)
                .loadedAt(Instant.now())
                .usable(true)
                .build();
        cache.set(snap);
        return snap;
    }

    private MfaPolicySnapshot degradeAndCache(long version, String reason) {
        MfaPolicySnapshot degraded = MfaPolicySnapshot.degraded(version, reason);
        cache.set(degraded);
        // 持久化 DEGRADED 标记（可选）；状态机运行时表现为 closed
        try {
            MfaControlStateDO control = store.getControlState();
            if (control != null && MfaLifecycleState.ARMED.name().equals(control.getLifecycleState())) {
                // 不把 lifecycle 写回 DEGRADED 到 DB，避免运维修复后仍锁死；运行时 closed 即可
                // 但绝不写回 UNINITIALIZED
            }
        } catch (Exception ignored) {
            // ignore
        }
        return degraded;
    }

    private MfaLifecycleState resolveLifecycle(MfaControlStateDO control) {
        if (control == null || control.getLifecycleState() == null) {
            return MfaLifecycleState.UNINITIALIZED;
        }
        try {
            return MfaLifecycleState.valueOf(control.getLifecycleState());
        } catch (Exception ex) {
            // 非法 lifecycle 在有 ARMED 历史不可知时，按 closed 处理更安全
            return MfaLifecycleState.DEGRADED_CLOSED;
        }
    }

    private boolean hasConfirmedNonOff() {
        List<MfaPolicyStore.ConfirmedPolicyProbe> probes = store.scanConfirmedPolicies();
        for (MfaPolicyStore.ConfirmedPolicyProbe p : probes) {
            MfaMode mode = MfaMode.parseStrict(p.mode());
            if (mode == null) {
                // 非法 mode 存在于已确认行：不能当 OFF
                return true;
            }
            if (mode.isNonOff()) {
                return true;
            }
        }
        return false;
    }

    private void armFromScan(long gateVersion) {
        MfaControlStateDO control = store.getControlState();
        if (control != null && MfaLifecycleState.ARMED.name().equals(control.getLifecycleState())) {
            return;
        }
        long version = Math.max(gateVersion, 1L);
        MfaControlStateDO armed = MfaControlStateDO.builder()
                .id(MfaControlStateDO.SINGLETON_ID)
                .lifecycleState(MfaLifecycleState.ARMED.name())
                .policyVersion(version)
                .armedAt(LocalDateTime.now())
                .checksum("armed-from-scan")
                .build();
        store.saveControlState(armed);
    }

    @Override
    public long readPrimaryPolicyVersionGate() {
        try {
            return readPrimaryPolicyVersionGateInternal();
        } catch (Exception ex) {
            throw new IllegalStateException("MFA policy version gate unavailable", ex);
        }
    }

    private long readPrimaryPolicyVersionGateInternal() {
        MfaControlStateDO control = store.getControlState();
        if (control == null || control.getPolicyVersion() == null) {
            return 0L;
        }
        return control.getPolicyVersion();
    }

    private long safeVersion() {
        try {
            return readPrimaryPolicyVersionGateInternal();
        } catch (Exception e) {
            return -1L;
        }
    }

    private MfaLifecycleState safeLifecycle() {
        try {
            return resolveLifecycle(store.getControlState());
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public long confirmGlobalPolicy(MfaMode mode, Set<String> allowedFactors) {
        Objects.requireNonNull(mode, "mode");
        if (mode == MfaMode.INHERIT) {
            throw new IllegalArgumentException("global mode cannot be INHERIT");
        }
        Set<String> factors = allowedFactors == null ? Collections.emptySet() : allowedFactors;
        MfaControlStateDO control = store.getControlState();
        MfaLifecycleState lifecycle = resolveLifecycle(control);
        if (lifecycle == MfaLifecycleState.ARMED || lifecycle == MfaLifecycleState.DEGRADED_CLOSED) {
            // 保持 ARMED
        } else if (mode.isNonOff()) {
            lifecycle = MfaLifecycleState.ARMED;
        } else {
            lifecycle = MfaLifecycleState.UNINITIALIZED;
        }

        long nextVersion = (control == null || control.getPolicyVersion() == null ? 0L : control.getPolicyVersion()) + 1;
        String checksum = MfaChecksumUtil.compute(mode.name(), factors, nextVersion);

        MfaGlobalPolicyDO policy = MfaGlobalPolicyDO.builder()
                .id(MfaGlobalPolicyDO.SINGLETON_ID)
                .mode(mode.name())
                .allowedFactors(String.join(",", factors))
                .policyVersion(nextVersion)
                .checksum(checksum)
                .confirmed(true)
                .build();
        store.saveGlobalPolicy(policy);

        MfaControlStateDO newControl = MfaControlStateDO.builder()
                .id(MfaControlStateDO.SINGLETON_ID)
                .lifecycleState(lifecycle.name())
                .policyVersion(nextVersion)
                .armedAt(lifecycle == MfaLifecycleState.ARMED
                        ? (control != null && control.getArmedAt() != null ? control.getArmedAt() : LocalDateTime.now())
                        : null)
                .checksum(checksum)
                .build();
        // 禁止 ARMED → UNINITIALIZED
        if (control != null && MfaLifecycleState.ARMED.name().equals(control.getLifecycleState())
                && lifecycle == MfaLifecycleState.UNINITIALIZED) {
            newControl.setLifecycleState(MfaLifecycleState.ARMED.name());
            if (newControl.getArmedAt() == null) {
                newControl.setArmedAt(LocalDateTime.now());
            }
        }
        store.saveControlState(newControl);
        invalidateCache();
        return nextVersion;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public long confirmTenantPolicy(Long tenantId, MfaMode mode, Set<String> allowedFactors) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(mode, "mode");
        MfaPolicySnapshot globalSnap = resolveEffectivePolicy(null);
        if (globalSnap.getLifecycleState() == MfaLifecycleState.DEGRADED_CLOSED || !globalSnap.isUsable()) {
            throw new IllegalStateException("cannot update tenant policy while MFA control plane is closed");
        }
        if (globalSnap.getMode() == MfaMode.REQUIRED && mode != MfaMode.REQUIRED && mode != MfaMode.INHERIT) {
            throw new IllegalArgumentException("global REQUIRED forbids tenant downgrade");
        }
        Set<String> factors = allowedFactors == null ? Collections.emptySet() : allowedFactors;
        long nextVersion = readPrimaryPolicyVersionGateInternal() + 1;
        String checksum = MfaChecksumUtil.compute(mode.name(), factors, nextVersion);

        MfaTenantPolicyDO policy = MfaTenantPolicyDO.builder()
                .tenantId(tenantId)
                .mode(mode.name())
                .allowedFactors(String.join(",", factors))
                .policyVersion(nextVersion)
                .checksum(checksum)
                .confirmed(true)
                .build();
        store.saveTenantPolicy(policy);

        // 租户非 OFF 时确保 ARMED
        if (mode.isNonOff()) {
            MfaControlStateDO control = store.getControlState();
            if (control == null || !MfaLifecycleState.ARMED.name().equals(control.getLifecycleState())) {
                store.saveControlState(MfaControlStateDO.builder()
                        .id(MfaControlStateDO.SINGLETON_ID)
                        .lifecycleState(MfaLifecycleState.ARMED.name())
                        .policyVersion(nextVersion)
                        .armedAt(LocalDateTime.now())
                        .checksum(checksum)
                        .build());
            } else {
                control.setPolicyVersion(nextVersion);
                control.setChecksum(checksum);
                store.saveControlState(control);
            }
        } else {
            MfaControlStateDO control = store.getControlState();
            if (control != null) {
                control.setPolicyVersion(nextVersion);
                store.saveControlState(control);
            }
        }
        invalidateCache();
        return nextVersion;
    }

    @Override
    public boolean isReady() {
        try {
            MfaPolicySnapshot snap = resolveEffectivePolicy(null);
            // UNINITIALIZED/OFF 与 ARMED+合法策略：就绪；DEGRADED_CLOSED 或不 usable：不就绪
            return snap.isUsable() && snap.getLifecycleState() != MfaLifecycleState.DEGRADED_CLOSED;
        } catch (Exception ex) {
            return false;
        }
    }

    @Override
    public void invalidateCache() {
        cache.set(null);
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
