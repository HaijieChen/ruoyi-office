package cn.iocoder.yudao.module.finance.framework.bpm;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * PAY-R7/R11：BPM 状态辅路径写台账时的租户 + 流程实例绑定。
 * <p>合法 tenant（&gt;0）→ {@link TenantUtils#execute}；缺失/非法 → fail-closed。
 * <p>事件 processInstanceId 必须非空；fallback 解析要求 eventPi 与 rowPi 均非空且相等。
 * <p>禁止在 ignore 租户状态下执行写操作。
 */
@Slf4j
public final class FinanceBpmTenantSupport {

    private FinanceBpmTenantSupport() {
    }

    public static void runLedgerWrite(BpmProcessInstanceStatusEvent event, String domain,
                                      Long appId, Runnable write) {
        runLedgerWrite(event, domain, appId, null, write);
    }

    /**
     * @param resolveTenantReadOnly 只读解析（在 ignore 中 select）；须在内部自行校验 PI 绑定，
     *                              返回合法 tenantId（&gt;0）或 null
     */
    public static void runLedgerWrite(BpmProcessInstanceStatusEvent event, String domain,
                                      Long appId, Supplier<Long> resolveTenantReadOnly,
                                      Runnable write) {
        if (write == null) {
            throw new IllegalArgumentException("write runnable required");
        }
        String pi = event != null ? event.getProcessInstanceId() : null;
        // PAY-R11：事件 PI 必填，防止无实例绑定改写重提后台账
        if (StrUtil.isBlank(pi)) {
            log.error("[runLedgerWrite][{}] blank event processInstanceId, fail-closed appId={}",
                    domain, appId);
            throw new IllegalStateException(
                    "finance BPM ledger write requires processInstanceId (appId=" + appId + ")");
        }

        Long tenantId = parseTenantId(
                event != null && event.getProcessInstanceInfo() != null
                        ? event.getProcessInstanceInfo().getTenantId()
                        : null);

        if (tenantId == null && resolveTenantReadOnly != null) {
            try {
                tenantId = TenantUtils.executeIgnore(resolveTenantReadOnly::get);
            } catch (Exception ex) {
                log.error("[runLedgerWrite][{}] read-only tenant resolve failed appId={} pi={}",
                        domain, appId, pi, ex);
                throw new IllegalStateException(
                        "finance BPM ledger write: cannot resolve tenant for appId=" + appId, ex);
            }
        }

        if (!isValidTenantId(tenantId)) {
            log.error("[runLedgerWrite][{}] missing/illegal tenant, fail-closed appId={} pi={} tenantId={}",
                    domain, appId, pi, tenantId);
            throw new IllegalStateException(
                    "finance BPM ledger write requires tenantId>0 (appId=" + appId
                            + ", processInstanceId=" + pi + ")");
        }

        final Long tid = tenantId;
        TenantUtils.execute(tid, write);
    }

    /**
     * PAY-R11：eventPi 与 rowPi 均非空且相等时返回行 tenant；否则 null。
     */
    public static Long resolveTenantIfProcessBound(Long rowTenantId, String eventPi, String rowPi,
                                                   String domain, Long appId) {
        if (StrUtil.isBlank(eventPi) || StrUtil.isBlank(rowPi)) {
            log.warn("[resolveTenantIfProcessBound][{}] blank PI, fail-closed appId={} eventPi={} rowPi={}",
                    domain, appId, eventPi, rowPi);
            return null;
        }
        if (!Objects.equals(eventPi, rowPi)) {
            log.warn("[resolveTenantIfProcessBound][{}] PI mismatch appId={} eventPi={} rowPi={}",
                    domain, appId, eventPi, rowPi);
            return null;
        }
        if (!isValidTenantId(rowTenantId)) {
            log.warn("[resolveTenantIfProcessBound][{}] illegal row tenant appId={} tenantId={}",
                    domain, appId, rowTenantId);
            return null;
        }
        return rowTenantId;
    }

    public static Long parseTenantId(String tenantId) {
        if (StrUtil.isBlank(tenantId)) {
            return null;
        }
        try {
            Long id = Long.parseLong(tenantId.trim());
            return isValidTenantId(id) ? id : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /** 合法租户：非 null 且 &gt; 0（0/负数视为未归属脏数据） */
    public static boolean isValidTenantId(Long tenantId) {
        return tenantId != null && tenantId > 0L;
    }
}
