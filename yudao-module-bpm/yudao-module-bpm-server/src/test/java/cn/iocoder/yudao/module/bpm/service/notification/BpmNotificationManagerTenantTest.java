package cn.iocoder.yudao.module.bpm.service.notification;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceInfo;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BpmNotificationManagerTenantTest {

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void resolveTenantPrefersRequestContextThenMessage() {
        TenantContextHolder.setTenantId(9L);
        BpmProcessInstanceStatusMessage message = message("1");
        assertEquals(9L, BpmNotificationManager.resolveNotificationTenantId(message));

        TenantContextHolder.clear();
        assertEquals(1L, BpmNotificationManager.resolveNotificationTenantId(message));
        assertNull(BpmNotificationManager.resolveNotificationTenantId(message("")));
        assertNull(BpmNotificationManager.resolveNotificationTenantId(null));
    }

    private static BpmProcessInstanceStatusMessage message(String tenantId) {
        return BpmProcessInstanceStatusMessage.builder()
                .processInstanceInfo(BpmProcessInstanceInfo.builder()
                        .processInstanceId("pi-1")
                        .processDefinitionKey("oa_business_trip")
                        .tenantId(tenantId)
                        .status(2)
                        .businessKey("6")
                        .build())
                .build();
    }
}
