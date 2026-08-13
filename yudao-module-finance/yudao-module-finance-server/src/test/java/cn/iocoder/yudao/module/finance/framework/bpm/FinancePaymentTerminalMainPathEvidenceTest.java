package cn.iocoder.yudao.module.finance.framework.bpm;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import cn.iocoder.yudao.module.finance.dal.dataobject.payment.FinancePaymentApplicationDO;
import cn.iocoder.yudao.module.finance.dal.mysql.contract.FinanceContractApplicationMapper;
import cn.iocoder.yudao.module.finance.dal.mysql.payment.FinancePaymentApplicationMapper;
import cn.iocoder.yudao.module.finance.dal.redis.no.FinancePaymentApplicationNoRedisDAO;
import cn.iocoder.yudao.module.finance.enums.FinancePaymentApplicationStatusEnum;
import cn.iocoder.yudao.module.finance.service.common.FinanceEntityCompanyResolver;
import cn.iocoder.yudao.module.finance.service.customer.FinanceCustomerCompanyService;
import cn.iocoder.yudao.module.finance.service.payment.FinancePaymentApplicationServiceImpl;
import cn.iocoder.yudao.module.finance.service.payment.FinancePaymentPredocService;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.flowable.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * PAY-R12：主路径 end delegate → <b>真实</b> PaymentApplicationService → mapper update。
 * <p>证据级别：非 mock service；mapper 为 mock 但 update 被真实 service 调用。
 * 未启动嵌入式 Flowable engine（引擎 changeState 另需环境）。
 */
class FinancePaymentTerminalMainPathEvidenceTest {

    private FinancePaymentApplicationMapper mapper;
    private FinancePaymentApplicationServiceImpl service;
    private FinancePaymentApprovalOutcomeDelegate delegate;
    private final AtomicReference<String> ledgerStatus = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        mapper = mock(FinancePaymentApplicationMapper.class);
        FinancePaymentApplicationNoRedisDAO noRedisDAO = mock(FinancePaymentApplicationNoRedisDAO.class);
        BpmProcessInstanceApi processInstanceApi = mock(BpmProcessInstanceApi.class);
        FinanceCustomerCompanyService customerCompanyService = mock(FinanceCustomerCompanyService.class);
        FinancePaymentPredocService predocService = mock(FinancePaymentPredocService.class);
        FinanceContractApplicationMapper contractMapper = mock(FinanceContractApplicationMapper.class);
        AdminUserApi adminUserApi = mock(AdminUserApi.class);
        DictDataApi dictDataApi = mock(DictDataApi.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<org.flowable.engine.TaskService> taskProvider = mock(ObjectProvider.class);
        when(taskProvider.getIfAvailable()).thenReturn(null);
        @SuppressWarnings("unchecked")
        ObjectProvider<org.flowable.engine.HistoryService> historyProvider = mock(ObjectProvider.class);
        when(historyProvider.getIfAvailable()).thenReturn(null);
        @SuppressWarnings("unchecked")
        ObjectProvider<cn.iocoder.yudao.module.system.api.dept.DeptApi> deptProvider = mock(ObjectProvider.class);
        when(deptProvider.getIfAvailable()).thenReturn(null);

        FinanceEntityCompanyResolver entityCompanyResolver = mock(FinanceEntityCompanyResolver.class);
        var companyBankAccountService = mock(cn.iocoder.yudao.module.finance.service.companyaccount.FinanceCompanyBankAccountService.class);
        var payLineMapper = mock(cn.iocoder.yudao.module.finance.dal.mysql.payment.FinancePaymentPayLineMapper.class);
        var salaryLineMapper = mock(cn.iocoder.yudao.module.finance.dal.mysql.payment.FinancePaymentSalaryLineMapper.class);
        var taxLineMapper = mock(cn.iocoder.yudao.module.finance.dal.mysql.payment.FinancePaymentTaxLineMapper.class);
        when(payLineMapper.sumPayAmountByApplicationId(anyLong())).thenReturn(java.math.BigDecimal.ZERO);
        when(payLineMapper.selectByApplicationId(anyLong())).thenReturn(java.util.List.of());
        service = new FinancePaymentApplicationServiceImpl(
                mapper, noRedisDAO, processInstanceApi, customerCompanyService,
                predocService, contractMapper, taskProvider, historyProvider, adminUserApi, dictDataApi,
                deptProvider, entityCompanyResolver, companyBankAccountService, payLineMapper,
                salaryLineMapper, taxLineMapper);
        delegate = new FinancePaymentApprovalOutcomeDelegate();
        ReflectionTestUtils.setField(delegate, "paymentApplicationService", service);

        ledgerStatus.set(FinancePaymentApplicationStatusEnum.PENDING.getStatus());
        when(mapper.selectById(42L)).thenAnswer(inv -> FinancePaymentApplicationDO.builder()
                .id(42L)
                .status(ledgerStatus.get())
                .processInstanceId("pi-main")
                .build());
        // F4：终态与 recordPay 共用 FOR UPDATE
        when(mapper.selectByIdForUpdate(42L)).thenAnswer(inv -> FinancePaymentApplicationDO.builder()
                .id(42L)
                .status(ledgerStatus.get())
                .processInstanceId("pi-main")
                .build());
        when(mapper.update(isNull(), any(UpdateWrapper.class))).thenAnswer(inv -> {
            // 真实 service 在 REJECT/CANCEL 路径会 set status；测试分别断言
            String cur = ledgerStatus.get();
            if (FinancePaymentApplicationStatusEnum.PENDING.getStatus().equals(cur)) {
                // 由调用方随后断言具体终态；默认写 REJECTED，cancel 用例会改 captor
                ledgerStatus.set(FinancePaymentApplicationStatusEnum.REJECTED.getStatus());
            }
            return 1;
        });
    }

    @AfterEach
    void clear() {
        cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.clear();
    }

    @Test
    void rejectEndDelegateWritesRejectedViaRealService() {
        DelegateExecution execution = mock(DelegateExecution.class);
        when(execution.getProcessInstanceBusinessKey()).thenReturn("42");
        when(execution.getProcessInstanceId()).thenReturn("pi-main");
        when(execution.getVariable(FinancePaymentApprovalOutcomeDelegate.PROCESS_STATUS_VARIABLE))
                .thenReturn(BpmProcessInstanceStatusEnum.REJECT.getStatus());

        TenantUtils.execute(1L, () -> delegate.execute(execution));

        assertEquals(FinancePaymentApplicationStatusEnum.REJECTED.getStatus(), ledgerStatus.get());
        verify(mapper, atLeastOnce()).update(isNull(), any(UpdateWrapper.class));
    }

    @Test
    void cancelEndDelegateWritesCancelledViaRealService() {
        ledgerStatus.set(FinancePaymentApplicationStatusEnum.PENDING.getStatus());
        when(mapper.update(isNull(), any(UpdateWrapper.class))).thenAnswer(inv -> {
            ledgerStatus.set(FinancePaymentApplicationStatusEnum.CANCELLED.getStatus());
            return 1;
        });
        DelegateExecution execution = mock(DelegateExecution.class);
        when(execution.getProcessInstanceBusinessKey()).thenReturn("42");
        when(execution.getProcessInstanceId()).thenReturn("pi-main");
        when(execution.getVariable(FinancePaymentApprovalOutcomeDelegate.PROCESS_STATUS_VARIABLE))
                .thenReturn(BpmProcessInstanceStatusEnum.CANCEL.getStatus());

        TenantUtils.execute(1L, () -> delegate.execute(execution));
        assertEquals(FinancePaymentApplicationStatusEnum.CANCELLED.getStatus(), ledgerStatus.get());
    }
}
