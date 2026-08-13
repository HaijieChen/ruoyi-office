package cn.iocoder.yudao.module.bpm.service.definition;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApiImpl;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants;
import cn.iocoder.yudao.module.bpm.service.task.BpmProcessInstanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * EXP-87 G1：服务端派生可信业务通道 vs 通用 HTTP/RPC 边界。
 * <p>
 * - 线路 DTO 无 trusted 字段；信任仅 ThreadLocal
 * - 通用 createProcessInstance：薪税恒 deny
 * - createProcessInstanceByBusiness：服务端置位后按业务权限放行
 */
@ExtendWith(MockitoExtension.class)
class BpmTrustedBusinessStartChannelTest {

    private static final String SALARY_KEY = "finance_salary_payment_apply";
    private static final String TAX_KEY = "finance_tax_payment_apply";

    @Mock
    private SecurityFrameworkService securityFrameworkService;

    private BpmProcessStartEligibilityService eligibility;

    @BeforeEach
    void setUp() {
        eligibility = new BpmProcessStartEligibilityServiceImpl(securityFrameworkService);
    }

    @Test
    void genericRpcCreate_salaryKey_denied_evenIfCallerWantsTrust() {
        // 通用通道：ThreadLocal 未置位 → 恒 deny（DTO 也无法自报）
        assertFalse(BpmBusinessStartChannelHolder.isTrustedBusinessStart());
        ServiceException ex = assertThrows(ServiceException.class,
                () -> eligibility.validateStartOrThrow(SALARY_KEY,
                        BpmBusinessStartChannelHolder.isTrustedBusinessStart()));
        assertEquals(ErrorCodeConstants.PROCESS_INSTANCE_START_PERMISSION_DENIED.getCode(), ex.getCode());
    }

    @Test
    void businessChannel_withPermission_passesRealEligibility() {
        when(securityFrameworkService.hasPermission(eq("finance:salary-payment:create")))
                .thenReturn(true);
        assertDoesNotThrow(() -> BpmBusinessStartChannelHolder.callTrusted(() -> {
            eligibility.validateStartOrThrow(SALARY_KEY, BpmBusinessStartChannelHolder.isTrustedBusinessStart());
            return null;
        }));
        // finally 清理
        assertFalse(BpmBusinessStartChannelHolder.isTrustedBusinessStart());
    }

    @Test
    void businessChannel_tax_withPermission_passes() {
        when(securityFrameworkService.hasPermission(eq("finance:tax-payment:create")))
                .thenReturn(true);
        assertDoesNotThrow(() -> BpmBusinessStartChannelHolder.callTrusted(() -> {
            eligibility.validateStartOrThrow(TAX_KEY, true);
            return null;
        }));
    }

    @Test
    void businessChannel_withoutPermission_stillDenied() {
        when(securityFrameworkService.hasPermission(eq("finance:salary-payment:create")))
                .thenReturn(false);
        ServiceException ex = assertThrows(ServiceException.class,
                () -> BpmBusinessStartChannelHolder.callTrusted(() -> {
                    eligibility.validateStartOrThrow(SALARY_KEY, true);
                    return null;
                }));
        assertEquals(ErrorCodeConstants.PROCESS_INSTANCE_START_PERMISSION_DENIED.getCode(), ex.getCode());
    }

    @Test
    void apiImpl_genericCreate_neverElevatesTrust_delegatesToService() {
        BpmProcessInstanceService service = mock(BpmProcessInstanceService.class);
        when(service.createProcessInstance(anyLong(), any(BpmProcessInstanceCreateReqDTO.class)))
                .thenReturn("pi-generic");
        BpmProcessInstanceApiImpl api = new BpmProcessInstanceApiImpl();
        ReflectionTestUtils.setField(api, "processInstanceService", service);

        BpmProcessInstanceCreateReqDTO dto = new BpmProcessInstanceCreateReqDTO()
                .setProcessDefinitionKey(SALARY_KEY)
                .setBusinessKey("1");
        api.createProcessInstance(1L, dto);
        // 通用入口走 createProcessInstance，不走 ByBusiness
        verify(service).createProcessInstance(eq(1L), any(BpmProcessInstanceCreateReqDTO.class));
        verify(service, never()).createProcessInstanceByBusiness(anyLong(), any(BpmProcessInstanceCreateReqDTO.class));
    }

    @Test
    void apiImpl_businessCreate_usesServerDerivedChannel() {
        BpmProcessInstanceService service = mock(BpmProcessInstanceService.class);
        when(service.createProcessInstanceByBusiness(anyLong(), any(BpmProcessInstanceCreateReqDTO.class)))
                .thenReturn("pi-biz");
        BpmProcessInstanceApiImpl api = new BpmProcessInstanceApiImpl();
        ReflectionTestUtils.setField(api, "processInstanceService", service);

        BpmProcessInstanceCreateReqDTO dto = new BpmProcessInstanceCreateReqDTO()
                .setProcessDefinitionKey(SALARY_KEY)
                .setBusinessKey("1");
        api.createProcessInstanceByBusiness(1L, dto);
        verify(service).createProcessInstanceByBusiness(eq(1L), any(BpmProcessInstanceCreateReqDTO.class));
        verify(service, never()).createProcessInstance(anyLong(), any(BpmProcessInstanceCreateReqDTO.class));
    }

    @Test
    void dtoHasNoTrustedBusinessStartField() {
        // 线路 DTO 禁止自报：反射确认无该字段
        assertThrows(NoSuchFieldException.class,
                () -> BpmProcessInstanceCreateReqDTO.class.getDeclaredField("trustedBusinessStart"));
    }

    @Test
    void catalogStillHidesSalaryTax() {
        assertTrue(eligibility.shouldHideFromStartList(SALARY_KEY));
        assertTrue(eligibility.shouldHideFromStartList(TAX_KEY));
    }
}
