package cn.iocoder.yudao.module.bpm.service.definition;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
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
 * EXP-87 G1：Finance 可信业务通道 vs 通用 BPM 直启 分离。
 * <p>
 * 使用真实 {@link BpmProcessStartEligibilityServiceImpl}（不 mock 启动校验），
 * 按 Finance 领域服务发出的 CreateReqDTO（trustedBusinessStart=true）评估薪税 key。
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

    /** 模拟 Finance startProcess 发出的 DTO */
    private static BpmProcessInstanceCreateReqDTO financeTrustedDto(String processKey) {
        return new BpmProcessInstanceCreateReqDTO()
                .setProcessDefinitionKey(processKey)
                .setBusinessKey("100")
                .setTrustedBusinessStart(true);
    }

    /** 模拟通用 /bpm/process-instance/create 或未带标记的内部调用 */
    private static BpmProcessInstanceCreateReqDTO genericDto(String processKey) {
        return new BpmProcessInstanceCreateReqDTO()
                .setProcessDefinitionKey(processKey)
                .setBusinessKey("100")
                .setTrustedBusinessStart(false);
    }

    /**
     * createProcessInstance0 使用的校验路径：
     * validateStartOrThrow(key, Boolean.TRUE.equals(dto.getTrustedBusinessStart()))
     */
    private void validateAsCreateProcessInstance0(BpmProcessInstanceCreateReqDTO dto) {
        boolean trusted = Boolean.TRUE.equals(dto.getTrustedBusinessStart());
        eligibility.validateStartOrThrow(dto.getProcessDefinitionKey(), trusted);
    }

    @Test
    void financeSalaryCreateDto_withPermission_passesRealEligibility() {
        when(securityFrameworkService.hasPermission(eq("finance:salary-payment:create")))
                .thenReturn(true);
        assertDoesNotThrow(() -> validateAsCreateProcessInstance0(financeTrustedDto(SALARY_KEY)));
    }

    @Test
    void financeTaxResubmitDto_withPermission_passesRealEligibility() {
        when(securityFrameworkService.hasPermission(eq("finance:tax-payment:create")))
                .thenReturn(true);
        assertDoesNotThrow(() -> validateAsCreateProcessInstance0(financeTrustedDto(TAX_KEY)));
    }

    @Test
    void genericBpmCreate_salaryKey_deniedRegardlessOfPermission() {
        // 通用通道：恒 deny（不查 hasPermission）
        ServiceException ex = assertThrows(ServiceException.class,
                () -> validateAsCreateProcessInstance0(genericDto(SALARY_KEY)));
        assertEquals(ErrorCodeConstants.PROCESS_INSTANCE_START_PERMISSION_DENIED.getCode(), ex.getCode());
        // null trusted 同样按通用
        BpmProcessInstanceCreateReqDTO nullTrusted = new BpmProcessInstanceCreateReqDTO()
                .setProcessDefinitionKey(SALARY_KEY)
                .setBusinessKey("1");
        assertThrows(ServiceException.class, () -> validateAsCreateProcessInstance0(nullTrusted));
    }

    @Test
    void financeTrusted_withoutPermission_stillDenied() {
        when(securityFrameworkService.hasPermission(eq("finance:salary-payment:create")))
                .thenReturn(false);
        ServiceException ex = assertThrows(ServiceException.class,
                () -> validateAsCreateProcessInstance0(financeTrustedDto(SALARY_KEY)));
        assertEquals(ErrorCodeConstants.PROCESS_INSTANCE_START_PERMISSION_DENIED.getCode(), ex.getCode());
    }

    @Test
    void catalogStillHidesSalaryTax() {
        assertTrue(eligibility.shouldHideFromStartList(SALARY_KEY));
        assertTrue(eligibility.shouldHideFromStartList(TAX_KEY));
    }
}
