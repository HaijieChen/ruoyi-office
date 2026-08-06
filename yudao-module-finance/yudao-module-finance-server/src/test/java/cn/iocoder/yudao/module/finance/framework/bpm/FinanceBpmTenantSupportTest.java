package cn.iocoder.yudao.module.finance.framework.bpm;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceInfo;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/** PAY-R7/R11：租户与 PI fail-closed */
class FinanceBpmTenantSupportTest {

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void parseTenantIdRejectsBlankZeroNegativeMalformed() {
        assertNull(FinanceBpmTenantSupport.parseTenantId(null));
        assertNull(FinanceBpmTenantSupport.parseTenantId("  "));
        assertNull(FinanceBpmTenantSupport.parseTenantId("0"));
        assertNull(FinanceBpmTenantSupport.parseTenantId("-1"));
        assertNull(FinanceBpmTenantSupport.parseTenantId("abc"));
        assertEquals(1L, FinanceBpmTenantSupport.parseTenantId("1"));
        assertEquals(99L, FinanceBpmTenantSupport.parseTenantId(" 99 "));
    }

    @Test
    void isValidTenantId() {
        assertFalse(FinanceBpmTenantSupport.isValidTenantId(null));
        assertFalse(FinanceBpmTenantSupport.isValidTenantId(0L));
        assertFalse(FinanceBpmTenantSupport.isValidTenantId(-5L));
        assertTrue(FinanceBpmTenantSupport.isValidTenantId(1L));
    }

    @Test
    void runLedgerWriteWithEventTenantSetsContext() {
        BpmProcessInstanceStatusEvent event = event("7", "pi-1");
        AtomicLong seen = new AtomicLong(-1);
        FinanceBpmTenantSupport.runLedgerWrite(event, "payment", 100L, () -> {
            seen.set(TenantContextHolder.getTenantId());
            assertFalse(TenantContextHolder.isIgnore());
        });
        assertEquals(7L, seen.get());
    }

    @Test
    void runLedgerWriteBlankProcessInstanceFailClosed() {
        BpmProcessInstanceStatusEvent event = event("1", null);
        AtomicInteger writes = new AtomicInteger();
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> FinanceBpmTenantSupport.runLedgerWrite(event, "payment", 1L, writes::incrementAndGet));
        assertTrue(ex.getMessage().contains("processInstanceId"));
        assertEquals(0, writes.get());
    }

    @Test
    void runLedgerWriteTenantZeroFailClosed() {
        BpmProcessInstanceStatusEvent event = event("0", "pi-x");
        AtomicInteger writes = new AtomicInteger();
        assertThrows(IllegalStateException.class,
                () -> FinanceBpmTenantSupport.runLedgerWrite(event, "payment", 1L, writes::incrementAndGet));
        assertEquals(0, writes.get());
    }

    @Test
    void runLedgerWriteResolvesTenantWhenEventMissingTenant() {
        BpmProcessInstanceStatusEvent event = event(null, "pi-ok");
        AtomicLong writeTenant = new AtomicLong();
        FinanceBpmTenantSupport.runLedgerWrite(event, "payment", 5L,
                () -> {
                    assertTrue(TenantContextHolder.isIgnore());
                    return FinanceBpmTenantSupport.resolveTenantIfProcessBound(42L, "pi-ok", "pi-ok", "payment", 5L);
                },
                () -> writeTenant.set(TenantContextHolder.getTenantId()));
        assertEquals(42L, writeTenant.get());
    }

    @Test
    void resolveTenantIfProcessBoundRequiresBothPi() {
        assertNull(FinanceBpmTenantSupport.resolveTenantIfProcessBound(1L, null, "pi", "p", 1L));
        assertNull(FinanceBpmTenantSupport.resolveTenantIfProcessBound(1L, "pi", null, "p", 1L));
        assertNull(FinanceBpmTenantSupport.resolveTenantIfProcessBound(1L, "a", "b", "p", 1L));
        assertNull(FinanceBpmTenantSupport.resolveTenantIfProcessBound(0L, "pi", "pi", "p", 1L));
        assertEquals(3L, FinanceBpmTenantSupport.resolveTenantIfProcessBound(3L, "pi", "pi", "p", 1L));
    }

    private static BpmProcessInstanceStatusEvent event(String tenantId, String pi) {
        BpmProcessInstanceStatusEvent event = new BpmProcessInstanceStatusEvent();
        BpmProcessInstanceInfo info = new BpmProcessInstanceInfo();
        info.setProcessInstanceId(pi);
        info.setBusinessKey("1");
        info.setTenantId(tenantId);
        event.setProcessInstanceInfo(info);
        return event;
    }
}
