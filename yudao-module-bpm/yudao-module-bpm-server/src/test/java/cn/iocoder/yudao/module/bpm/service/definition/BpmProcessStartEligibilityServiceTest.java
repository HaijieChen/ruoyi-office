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
    void payment_ignoresMenuPermission_canStart() {
        BpmProcessStartEligibility e = service.evaluate("finance_payment_apply");

        assertTrue(e.isCanStart());
        assertNull(e.getCannotStartReason());
    }

    @Test
    void contract_and_invoice_ignoreMenuPermission() {
        BpmProcessStartEligibility contract = service.evaluate("finance_contract_sign");
        BpmProcessStartEligibility invoice = service.evaluate("finance_invoice_apply");

        assertTrue(contract.isCanStart());
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
    void noInvoiceExpense_followsProcessDesignNotFinanceMenu() {
        BpmProcessStartEligibility e = service.evaluate("oa_expense_no_invoice");

        assertTrue(e.isCanStart());
        assertNull(e.getRequiredStartPermission());
    }

    @Test
    void tripAndOuting_notHiddenAndNotEmbed() {
        for (String key : new String[]{"oa_business_trip", "oa_outing"}) {
            assertFalse(service.shouldHideFromStartList(key), key);
            assertFalse(BpmEmbedProcessStartPermissionRegistry.isEmbedProcess(key), key);
            assertFalse(BpmEmbedProcessStartPermissionRegistry.isCreateShellEmbedAllowed(key), key);

            BpmProcessStartEligibility e = service.evaluate(key);
            assertTrue(e.isCanStart(), key);
            assertNull(e.getRequiredStartPermission(), key);
            assertNull(e.getCannotStartReason(), key);
        }
    }

    @Test
    void blankKey_treatedAsNonEmbed_canStart() {
        assertTrue(service.evaluate(null).isCanStart());
        assertTrue(service.evaluate("  ").isCanStart());
    }

    @Test
    void listShouldNotHideByMenuPermission() {
        assertFalse(service.shouldHideFromStartList("finance_payment_apply"));
        assertFalse(service.shouldHideFromStartList("oa_leave"));
    }

    @Test
    void salaryTax_withoutPermission_catalogVisible_genericStartDenied() {
        assertTrue(service.evaluate("finance_salary_payment_apply").isCanStart());
        assertTrue(service.evaluate("finance_tax_payment_apply").isCanStart());
        assertFalse(service.shouldHideFromStartList("finance_salary_payment_apply"));
        assertFalse(service.shouldHideFromStartList("finance_tax_payment_apply"));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.validateStartOrThrow("finance_salary_payment_apply"));
        assertEquals(ErrorCodeConstants.PROCESS_INSTANCE_START_PERMISSION_DENIED.getCode(), ex.getCode());
        assertThrows(ServiceException.class,
                () -> service.validateStartOrThrow("finance_tax_payment_apply", false));
    }

    @Test
    void salaryTax_withPermission_catalogVisible_butGenericStartStillDenied() {
        // 统一目录：可见（canStart=true）
        assertTrue(service.evaluate("finance_salary_payment_apply").isCanStart());
        assertTrue(service.evaluate("finance_tax_payment_apply").isCanStart());
        assertFalse(service.shouldHideFromStartList("finance_salary_payment_apply"));
        assertFalse(service.shouldHideFromStartList("finance_tax_payment_apply"));

        // 通用 createProcessInstance：仍硬拒绝
        ServiceException salaryEx = assertThrows(ServiceException.class,
                () -> service.validateStartOrThrow("finance_salary_payment_apply"));
        assertEquals(ErrorCodeConstants.PROCESS_INSTANCE_START_PERMISSION_DENIED.getCode(), salaryEx.getCode());
        assertTrue(salaryEx.getMessage().contains("禁止") || salaryEx.getMessage().contains("直启")
                || salaryEx.getMessage().contains("菜单") || salaryEx.getMessage().contains("入口"));
        assertThrows(ServiceException.class,
                () -> service.validateStartOrThrow("finance_tax_payment_apply", false));
    }

    @Test
    void salaryTax_trustedChannel_allowsWithPermission() {
        assertTrue(service.evaluate("finance_salary_payment_apply", true).isCanStart());
        assertTrue(service.evaluate("finance_tax_payment_apply", true).isCanStart());
        assertDoesNotThrow(() -> service.validateStartOrThrow("finance_salary_payment_apply", true));
        assertDoesNotThrow(() -> service.validateStartOrThrow("finance_tax_payment_apply", true));
        // 有权限时目录不隐藏（与通用 evaluate 一致）
        assertFalse(service.shouldHideFromStartList("finance_salary_payment_apply"));
        assertFalse(service.shouldHideFromStartList("finance_tax_payment_apply"));
    }

    @Test
    void catalogRedirectPaths_forSalaryTax() {
        assertEquals("/finance/salary-payment",
                BpmEmbedProcessStartPermissionRegistry.catalogRedirectPath("finance_salary_payment_apply"));
        assertEquals("/finance/tax-payment",
                BpmEmbedProcessStartPermissionRegistry.catalogRedirectPath("finance_tax_payment_apply"));
        assertNull(BpmEmbedProcessStartPermissionRegistry.catalogRedirectPath("finance_payment_apply"));
        assertFalse(BpmEmbedProcessStartPermissionRegistry.isCreateShellEmbedAllowed("finance_salary_payment_apply"));
        assertFalse(BpmEmbedProcessStartPermissionRegistry.isCreateShellEmbedAllowed("finance_tax_payment_apply"));
    }

    @Test
    void salaryTax_trustedChannel_allowsWithoutMenuPermission() {
        assertTrue(service.evaluate("finance_salary_payment_apply", true).isCanStart());
        assertDoesNotThrow(() -> service.validateStartOrThrow("finance_salary_payment_apply", true));
    }

    @Test
    void validateStartOrThrow_paymentIgnoresMenuPermission() {
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

    @Test
    void catalogAdmin_doesNotHideEvenWithoutPermission() {
        assertFalse(service.shouldHideFromStartList("finance_payment_apply"));
        assertFalse(service.shouldHideFromStartList("finance_payment_apply", true));
    }

    private static boolean StrUtilSafe(String s) {
        return s != null && !s.isBlank();
    }
}
