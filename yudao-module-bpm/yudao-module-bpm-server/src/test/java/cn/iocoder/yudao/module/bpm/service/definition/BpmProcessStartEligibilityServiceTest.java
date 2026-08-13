package cn.iocoder.yudao.module.bpm.service.definition;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 嵌入式流程发起权限：列表隐藏 / 预检 / 启动校验共用权威元数据。
 */
@ExtendWith(MockitoExtension.class)
class BpmProcessStartEligibilityServiceTest {

    @Mock
    private SecurityFrameworkService securityFrameworkService;

    private BpmProcessStartEligibilityService service;

    @BeforeEach
    void setUp() {
        service = new BpmProcessStartEligibilityServiceImpl(securityFrameworkService);
    }

    @Test
    void payment_missingPermission_cannotStart_withFriendlyReason() {
        when(securityFrameworkService.hasPermission(eq("finance:payment-application:create")))
                .thenReturn(false);

        BpmProcessStartEligibility e = service.evaluate("finance_payment_apply");

        assertFalse(e.isCanStart());
        assertEquals("finance:payment-application:create", e.getRequiredStartPermission());
        assertEquals("无付款发起权限，请联系管理员分配付款发起角色", e.getCannotStartReason());
    }

    @Test
    void payment_hasPermission_canStart() {
        when(securityFrameworkService.hasPermission(eq("finance:payment-application:create")))
                .thenReturn(true);

        BpmProcessStartEligibility e = service.evaluate("finance_payment_apply");

        assertTrue(e.isCanStart());
        assertNull(e.getCannotStartReason());
        assertEquals("finance:payment-application:create", e.getRequiredStartPermission());
    }

    @Test
    void contract_and_invoice_requireCreatePermission() {
        when(securityFrameworkService.hasPermission(eq("finance:contract-application:create")))
                .thenReturn(false);
        when(securityFrameworkService.hasPermission(eq("finance:invoice-application:create")))
                .thenReturn(true);

        BpmProcessStartEligibility contract = service.evaluate("finance_contract_sign");
        BpmProcessStartEligibility invoice = service.evaluate("finance_invoice_apply");

        assertFalse(contract.isCanStart());
        assertEquals("finance:contract-application:create", contract.getRequiredStartPermission());
        assertTrue(invoice.isCanStart());
    }

    @Test
    void nonEmbedProcess_noRequiredPermission_canStart() {
        BpmProcessStartEligibility e = service.evaluate("oa_leave");

        assertTrue(e.isCanStart());
        assertNull(e.getRequiredStartPermission());
        assertNull(e.getCannotStartReason());
    }

    @Test
    void blankKey_treatedAsNonEmbed_canStart() {
        assertTrue(service.evaluate(null).isCanStart());
        assertTrue(service.evaluate("  ").isCanStart());
    }

    @Test
    void listShouldHideWhenCannotStart() {
        when(securityFrameworkService.hasPermission(eq("finance:payment-application:create")))
                .thenReturn(false);

        assertTrue(service.shouldHideFromStartList("finance_payment_apply"));
        assertFalse(service.shouldHideFromStartList("oa_leave"));
    }

    @Test
    void salaryTax_alwaysHiddenAndDeniedFromGenericStart() {
        // 不依赖权限 stub：有/无权限均拒绝通用直启（须走独立菜单）
        BpmProcessStartEligibility salary = service.evaluate("finance_salary_payment_apply");
        BpmProcessStartEligibility tax = service.evaluate("finance_tax_payment_apply");
        assertFalse(salary.isCanStart());
        assertFalse(tax.isCanStart());
        assertTrue(service.shouldHideFromStartList("finance_salary_payment_apply"));
        assertTrue(service.shouldHideFromStartList("finance_tax_payment_apply"));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.validateStartOrThrow("finance_salary_payment_apply"));
        assertEquals(ErrorCodeConstants.PROCESS_INSTANCE_START_PERMISSION_DENIED.getCode(), ex.getCode());
    }

    @Test
    void validateStartOrThrow_deniesWithoutPermission() {
        when(securityFrameworkService.hasPermission(eq("finance:payment-application:create")))
                .thenReturn(false);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.validateStartOrThrow("finance_payment_apply"));
        assertEquals(ErrorCodeConstants.PROCESS_INSTANCE_START_PERMISSION_DENIED.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("付款"));
    }

    @Test
    void validateStartOrThrow_allowsWithPermission() {
        when(securityFrameworkService.hasPermission(eq("finance:payment-application:create")))
                .thenReturn(true);
        assertDoesNotThrow(() -> service.validateStartOrThrow("finance_payment_apply"));
    }

    @Test
    void validateDeploy_knownEmbedWithPermission_ok() {
        assertDoesNotThrow(() -> service.validateEmbedStartPermissionConfigured("finance_payment_apply"));
    }

    @Test
    void validateDeploy_unknownKey_ok() {
        assertDoesNotThrow(() -> service.validateEmbedStartPermissionConfigured("oa_leave"));
    }

    @Test
    void registry_exposesAllEmbedKeys_withNonBlankPermission() {
        assertTrue(BpmEmbedProcessStartPermissionRegistry.isEmbedProcess("finance_payment_apply"));
        for (String key : BpmEmbedProcessStartPermissionRegistry.allEmbedKeys()) {
            assertTrue(StrUtilSafe(BpmEmbedProcessStartPermissionRegistry.requiredPermission(key)));
            assertTrue(StrUtilSafe(BpmEmbedProcessStartPermissionRegistry.denyMessage(key)));
        }
    }

    private static boolean StrUtilSafe(String s) {
        return s != null && !s.isBlank();
    }
}
