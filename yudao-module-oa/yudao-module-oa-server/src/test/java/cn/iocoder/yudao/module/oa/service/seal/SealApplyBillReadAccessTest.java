package cn.iocoder.yudao.module.oa.service.seal;

import cn.iocoder.yudao.common.server.attachment.service.AttachmentService;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.datapermission.core.aop.DataPermissionContextHolder;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessParticipantApi;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessStartApi;
import cn.iocoder.yudao.module.oa.framework.security.OaProcessBillReadSupport;
import cn.iocoder.yudao.module.oa.controller.admin.seal.SealApplyBillController;
import cn.iocoder.yudao.module.oa.controller.admin.seal.vo.SealApplyBillRespVO;
import cn.iocoder.yudao.module.oa.dal.dataobject.seal.SealApplyBillDO;
import cn.iocoder.yudao.module.oa.dal.mysql.seal.SealApplyBillMapper;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static cn.iocoder.yudao.module.oa.enums.ErrorCodeConstants.SEAL_APPLY_BILL_ACCESS_DENIED;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Real Spring method-security proxy; participant API mocked; GET must not require menu query. */
class SealApplyBillReadAccessTest {

    private AnnotationConfigApplicationContext context;
    private SealApplyBillController controller;
    private SealApplyBillMapper mapper;
    private BpmProcessParticipantApi participantApi;
    private SecurityFrameworkService security;

    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean SealApplyBillController controller() { return new SealApplyBillController(); }
        @Bean SealApplyBillService sealApplyBillService() { return new SealApplyBillServiceImpl(); }
        @Bean SealApplyBillMapper sealApplyBillMapper() { return mock(SealApplyBillMapper.class); }
        @Bean AttachmentService attachmentService() { return mock(AttachmentService.class); }
        @Bean BpmProcessInstanceApi processInstanceApi() { return mock(BpmProcessInstanceApi.class); }
        @Bean BpmProcessStartApi processStartApi() { return mock(BpmProcessStartApi.class); }
        @Bean BpmProcessParticipantApi processParticipantApi() { return mock(BpmProcessParticipantApi.class); }
        @Bean SecurityFrameworkService securityFrameworkService() { return mock(SecurityFrameworkService.class); }
        @Bean OaProcessBillReadSupport processBillReadSupport() { return new OaProcessBillReadSupport(); }
        @Bean AdminUserApi adminUserApi() { return mock(AdminUserApi.class); }
        @Bean DeptApi deptApi() { return mock(DeptApi.class); }
        @Bean(name = "ss") PermissionStub permissions() { return new PermissionStub(); }
    }

    public static class PermissionStub {
        public boolean hasPermission(String permission) { return false; }
    }

    @BeforeEach
    void setup() {
        context = new AnnotationConfigApplicationContext(Config.class);
        controller = context.getBean(SealApplyBillController.class);
        mapper = context.getBean(SealApplyBillMapper.class);
        participantApi = context.getBean(BpmProcessParticipantApi.class);
        security = context.getBean(SecurityFrameworkService.class);
        SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(681L).setTenantId(1L).setUserType(2),
                new org.springframework.mock.web.MockHttpServletRequest());
        TenantContextHolder.setTenantId(1L);
        when(security.hasPermission("oa:seal-apply-bill:query")).thenReturn(false);
        when(context.getBean(AttachmentService.class).getAttachmentListByBusiness(eq("103"), anyLong()))
                .thenReturn(List.of());
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        TenantContextHolder.clear();
        if (context != null) {
            context.close();
        }
    }

    @Test
    void getMappingDoesNotRequireQueryPermission() throws Exception {
        String src = Files.readString(Path.of(
                "src/main/java/cn/iocoder/yudao/module/oa/controller/admin/seal/SealApplyBillController.java"));
        int getIdx = src.indexOf("@GetMapping(\"/get\")");
        int pageIdx = src.indexOf("@GetMapping(\"/page\")");
        assertTrue(getIdx >= 0 && pageIdx > getIdx);
        String getBlock = src.substring(getIdx, pageIdx);
        assertFalse(getBlock.contains("oa:seal-apply-bill:query"));
        assertTrue(getBlock.contains("isAuthenticated()"));
        String pageBlock = src.substring(pageIdx, src.indexOf("@GetMapping(\"/export-excel\")"));
        assertTrue(pageBlock.contains("oa:seal-apply-bill:query"));
    }

    @Test
    void historicAssigneeWithoutQueryCanGet() {
        when(mapper.selectById(526L)).thenReturn(bill(526L, "838", "pi-1"));
        when(participantApi.canReadProcess("pi-1")).thenReturn(CommonResult.success(true));
        SealApplyBillRespVO vo = controller.getSealApplyBill(526L).getCheckedData();
        assertEquals(526L, vo.getId());
        assertEquals("pi-1", vo.getProcessInstanceId());
    }

    @Test
    void ownerWithoutQueryCanGetWithoutCallingProcessIdFromClient() {
        when(mapper.selectById(526L)).thenReturn(bill(526L, "681", "pi-secret"));
        SealApplyBillRespVO vo = controller.getSealApplyBill(526L).getCheckedData();
        assertEquals(526L, vo.getId());
    }

    @Test
    void strangerWithoutQueryIsDenied() {
        when(mapper.selectById(526L)).thenReturn(bill(526L, "838", "pi-1"));
        when(participantApi.canReadProcess("pi-1")).thenReturn(CommonResult.success(false));
        ServiceException ex = assertThrows(ServiceException.class, () -> controller.getSealApplyBill(526L));
        assertEquals(SEAL_APPLY_BILL_ACCESS_DENIED.getCode(), ex.getCode());
    }

    @Test
    void unauthenticatedGetIsDeniedByMethodSecurity() {
        SecurityContextHolder.clearContext();
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> controller.getSealApplyBill(526L));
    }

    @Test
    void queryPermissionDoesNotBypassSelfScopeWithoutParticipation() {
        when(security.hasPermission("oa:seal-apply-bill:query")).thenReturn(true);
        when(mapper.selectById(526L)).thenAnswer(inv -> scopedOrIgnored(bill(526L, "838", "pi-1")));
        when(participantApi.canReadProcess("pi-1")).thenReturn(CommonResult.success(false));
        ServiceException ex = assertThrows(ServiceException.class, () -> controller.getSealApplyBill(526L));
        assertEquals(SEAL_APPLY_BILL_ACCESS_DENIED.getCode(), ex.getCode());
    }

    @Test
    void historicParticipantReadsWhenSelfScopeHidesRow() {
        when(mapper.selectById(526L)).thenAnswer(inv -> scopedOrIgnored(bill(526L, "838", "pi-1")));
        when(participantApi.canReadProcess("pi-1")).thenReturn(CommonResult.success(true));
        SealApplyBillRespVO vo = controller.getSealApplyBill(526L).getCheckedData();
        assertEquals(526L, vo.getId());
    }

    private static SealApplyBillDO scopedOrIgnored(SealApplyBillDO bill) {
        var dp = DataPermissionContextHolder.get();
        boolean ignore = dp != null && !dp.enable();
        return ignore ? bill : null;
    }

    private static SealApplyBillDO bill(Long id, String creator, String processInstanceId) {
        SealApplyBillDO bill = new SealApplyBillDO();
        bill.setId(id);
        bill.setCreator(creator);
        bill.setProcessInstanceId(processInstanceId);
        bill.setBillCode("SEAL-1");
        return bill;
    }
}
