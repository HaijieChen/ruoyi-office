package cn.iocoder.yudao.module.bpm.api.task;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmProcessDefinitionInfoDO;
import cn.iocoder.yudao.module.bpm.service.definition.BpmProcessDefinitionService;
import org.flowable.engine.repository.ProcessDefinition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BpmProcessStartApiImplTest {
    private final BpmProcessDefinitionService definitions = mock(BpmProcessDefinitionService.class);
    private final BpmProcessStartApiImpl api = new BpmProcessStartApiImpl();
    private final ProcessDefinition definition = mock(ProcessDefinition.class);
    private final BpmProcessDefinitionInfoDO info = new BpmProcessDefinitionInfoDO().setVisible(true);

    @BeforeEach void setup() {
        ReflectionTestUtils.setField(api, "processDefinitionService", definitions);
        SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(42L), new org.springframework.mock.web.MockHttpServletRequest());
        TenantContextHolder.setTenantId(1L);
        when(definitions.getActiveProcessDefinition("oa_seal_apply_bill")).thenReturn(definition);
        when(definition.getId()).thenReturn("definition-1");
        when(definitions.getProcessDefinitionInfo("definition-1")).thenReturn(info);
        when(definitions.canUserStartProcessDefinition(info, 42L)).thenReturn(true);
    }
    @AfterEach void cleanup() {
        SecurityContextHolder.clearContext();
        TenantContextHolder.clear();
    }
    @Test void visibleAllowedUserWithoutAuthoritiesCanStart() {
        assertTrue(api.validateStart("oa_seal_apply_bill").getCheckedData());
        assertTrue(SecurityFrameworkUtils.getAuthentication().getAuthorities().isEmpty());
        verify(definitions).canUserStartProcessDefinition(info, 42L);
    }
    @Test void anonymousDeniedBeforeLookup() {
        SecurityContextHolder.clearContext();
        assertThrows(AccessDeniedException.class, () -> api.validateStart("oa_seal_apply_bill"));
        verifyNoInteractions(definitions);
    }
    @Test void missingTenantDeniedBeforeLookup() {
        TenantContextHolder.clear();
        assertThrows(AccessDeniedException.class, () -> api.validateStart("oa_seal_apply_bill"));
        verifyNoInteractions(definitions);
    }
    @Test void hiddenAndNullVisibilityDenied() {
        info.setVisible(false);
        assertThrows(AccessDeniedException.class, () -> api.validateStart("oa_seal_apply_bill"));
        info.setVisible(null);
        assertThrows(AccessDeniedException.class, () -> api.validateStart("oa_seal_apply_bill"));
    }
    @Test void outOfScopeDenied() {
        when(definitions.canUserStartProcessDefinition(info, 42L)).thenReturn(false);
        assertThrows(AccessDeniedException.class, () -> api.validateStart("oa_seal_apply_bill"));
    }
    @Test void missingOrSuspendedDefinitionDenied() {
        when(definition.isSuspended()).thenReturn(true);
        assertThrows(AccessDeniedException.class, () -> api.validateStart("oa_seal_apply_bill"));
        when(definitions.getActiveProcessDefinition("oa_seal_apply_bill")).thenReturn(null);
        assertThrows(AccessDeniedException.class, () -> api.validateStart("oa_seal_apply_bill"));
    }
    @Test void missingInfoDenied() {
        when(definitions.getProcessDefinitionInfo("definition-1")).thenReturn(null);
        assertThrows(AccessDeniedException.class, () -> api.validateStart("oa_seal_apply_bill"));
    }
}
