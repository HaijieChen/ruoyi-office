package cn.iocoder.yudao.module.finance.service.payment;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.finance.controller.admin.payment.vo.FinancePaymentApplicationCreateAndStartReqVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.contract.FinanceContractApplicationDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.customer.FinanceCustomerCompanyDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.payment.FinancePaymentApplicationDO;
import cn.iocoder.yudao.module.finance.dal.mysql.contract.FinanceContractApplicationMapper;
import cn.iocoder.yudao.module.finance.dal.mysql.payment.FinancePaymentApplicationMapper;
import cn.iocoder.yudao.module.finance.dal.redis.no.FinancePaymentApplicationNoRedisDAO;
import cn.iocoder.yudao.module.finance.enums.FinancePaymentReasonEnum;
import cn.iocoder.yudao.module.finance.enums.FinancePaymentTimingEnum;
import cn.iocoder.yudao.module.finance.service.common.FinanceEntityCompanyResolver;
import cn.iocoder.yudao.module.finance.service.customer.FinanceCustomerCompanyService;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * PAY-R9：租户契约（服务层 + 实体继承证据）。
 * <p>证据强度：insert 实体带当前 tenantId；三 DO 为 TenantBaseDO；
 * 跨租户 get 在 mapper 返回 null 时业务失败（模拟 TenantLine 过滤）。
 * 完整 SQL TenantLine 仍依赖部署环境 MyBatis 插件。
 */
class FinancePaymentTenantIsolationContractTest {

    private FinancePaymentApplicationMapper mapper;
    private FinancePaymentApplicationServiceImpl service;

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

        AdminUserRespDTO user = new AdminUserRespDTO();
        user.setId(1L);
        user.setDeptId(10L);
        when(adminUserApi.getUser(anyLong())).thenReturn(CommonResult.success(user));
        when(dictDataApi.validateDictDataList(anyString(), anyCollection()))
                .thenReturn(CommonResult.success(true));
        when(noRedisDAO.generate(any(LocalDate.class))).thenReturn("PAY-T-1");
        doAnswer(inv -> {
            FinancePaymentApplicationDO a = inv.getArgument(0);
            a.setId(200L);
            return 1;
        }).when(mapper).insert(any(FinancePaymentApplicationDO.class));
        when(customerCompanyService.getEnabledSupplierCompany(anyLong())).thenReturn(
                FinanceCustomerCompanyDO.builder()
                        .id(9L).name("供应商甲").bankName("行").bankAccount("6222")
                        .isSupplier(true).status(0).build());
        CommonResult<String> pi = mock(CommonResult.class);
        when(pi.getCheckedData()).thenReturn("proc-t");
        when(processInstanceApi.createProcessInstance(anyLong(), any())).thenReturn(pi);
        when(processInstanceApi.createProcessInstanceByBusiness(anyLong(), any())).thenReturn(pi);

        FinanceEntityCompanyResolver entityCompanyResolver = mock(FinanceEntityCompanyResolver.class);
        when(entityCompanyResolver.requireByDeptId(anyLong()))
                .thenReturn(new FinanceEntityCompanyResolver.ResolvedCompany(20L, "主体甲", "CNY"));
        var companyBankAccountService = mock(cn.iocoder.yudao.module.finance.service.companyaccount.FinanceCompanyBankAccountService.class);
        var payLineMapper = mock(cn.iocoder.yudao.module.finance.dal.mysql.payment.FinancePaymentPayLineMapper.class);
        var salaryLineMapper = mock(cn.iocoder.yudao.module.finance.dal.mysql.payment.FinancePaymentSalaryLineMapper.class);
        var taxLineMapper = mock(cn.iocoder.yudao.module.finance.dal.mysql.payment.FinancePaymentTaxLineMapper.class);
        when(payLineMapper.sumPayAmountByApplicationId(anyLong())).thenReturn(java.math.BigDecimal.ZERO);
        service = new FinancePaymentApplicationServiceImpl(
                mapper, noRedisDAO, processInstanceApi, customerCompanyService,
                predocService, contractMapper, taskProvider, historyProvider, adminUserApi, dictDataApi,
                deptProvider, entityCompanyResolver, companyBankAccountService, payLineMapper,
                salaryLineMapper, taxLineMapper);
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void paymentCustomerContractAreTenantBaseDo() {
        assertTrue(TenantBaseDO.class.isAssignableFrom(FinancePaymentApplicationDO.class));
        assertTrue(TenantBaseDO.class.isAssignableFrom(FinanceCustomerCompanyDO.class));
        assertTrue(TenantBaseDO.class.isAssignableFrom(FinanceContractApplicationDO.class));
    }

    @Test
    void createUnderTenantAStampsTenantIdOnInsert() {
        FinancePaymentApplicationCreateAndStartReqVO req = baseReq();
        TenantUtils.execute(11L, () -> service.createAndStart(req, 1L));
        ArgumentCaptor<FinancePaymentApplicationDO> cap = ArgumentCaptor.forClass(FinancePaymentApplicationDO.class);
        verify(mapper).insert(cap.capture());
        assertEquals(11L, cap.getValue().getTenantId());
    }

    @Test
    void getMissingUnderOtherTenantFails() {
        // 模拟 TenantLine：租户 B 看不到租户 A 的行
        when(mapper.selectById(200L)).thenReturn(null);
        TenantUtils.execute(22L, () -> {
            assertThrows(Exception.class, () -> service.getApplication(200L));
        });
    }

    @Test
    void sumPaidDelegatesUnderTenantContext() {
        when(mapper.sumPaidByPayee(9L)).thenReturn(new BigDecimal("10.00"));
        BigDecimal sum = TenantUtils.execute(11L, () -> service.sumPaidByPayee(9L));
        assertEquals(new BigDecimal("10.00"), sum);
        verify(mapper).sumPaidByPayee(9L);
    }

    private static FinancePaymentApplicationCreateAndStartReqVO baseReq() {
        FinancePaymentApplicationCreateAndStartReqVO req = new FinancePaymentApplicationCreateAndStartReqVO();
        req.setPaymentTiming(FinancePaymentTimingEnum.IMMEDIATE.getCode());
        req.setPaymentReason(FinancePaymentReasonEnum.BUSINESS.getCode());
        req.setPayeeCompanyId(9L);
        req.setEntityCompanyDeptId(20L);
        req.setApplyAmount(new BigDecimal("100.00"));
        req.setCurrency("CNY");
        req.setBusinessSettlementTerm("月结");
        req.setPayMethod("wire");
        req.setCostProject("office_purchase");
        req.setEvidenceFileUrls(List.of("https://x/a.pdf"));
        return req;
    }
}
