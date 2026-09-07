package cn.iocoder.yudao.module.bpm.service.oa;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.FlowableUtils;
import org.flowable.engine.ProcessEngineConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class OaAttendanceTenantExecuteTest {

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    void emptyAndNoTenantKeepCallingThreadContext() {
        TenantContextHolder.setTenantId(1L);
        FlowableUtils.execute("", () -> assertEquals(1L, TenantContextHolder.getTenantId()));
        assertEquals(1L, TenantContextHolder.getTenantId());
        FlowableUtils.execute(ProcessEngineConfiguration.NO_TENANT_ID,
                () -> assertEquals(1L, TenantContextHolder.getTenantId()));
        assertEquals(1L, TenantContextHolder.getTenantId());
        FlowableUtils.execute(null, () -> assertEquals(1L, TenantContextHolder.getTenantId()));
        assertEquals(1L, TenantContextHolder.getTenantId());
    }

    @Test
    void numericZeroRestoresExplicitlyThenRestoresCaller() {
        TenantContextHolder.setTenantId(1L);
        FlowableUtils.execute("0", () -> assertEquals(0L, TenantContextHolder.getTenantId()));
        assertEquals(1L, TenantContextHolder.getTenantId());
    }

    @Test
    void numericTenantRestoresThenRestoresCaller() {
        TenantContextHolder.setTenantId(1L);
        FlowableUtils.execute("2", () -> assertEquals(2L, TenantContextHolder.getTenantId()));
        assertEquals(1L, TenantContextHolder.getTenantId());
        TenantContextHolder.clear();
        assertNull(TenantContextHolder.getTenantId());
    }
}
