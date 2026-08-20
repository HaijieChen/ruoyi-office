package cn.iocoder.yudao.module.finance.service.contract;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.finance.controller.admin.business.vo.FinanceBusinessOrderSaveReqVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.contract.FinanceContractApplicationDO;
import cn.iocoder.yudao.module.finance.dal.mysql.business.FinanceBusinessOrderMapper;
import cn.iocoder.yudao.module.finance.service.business.FinanceBusinessOrderService;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FinanceApprovedSalesBusinessOrderServiceTest {

    private FinanceBusinessOrderMapper mapper;
    private FinanceBusinessOrderService businessOrderService;
    private AdminUserApi adminUserApi;
    private FinanceApprovedSalesBusinessOrderService service;

    @BeforeEach
    void setUp() {
        mapper = mock(FinanceBusinessOrderMapper.class);
        businessOrderService = mock(FinanceBusinessOrderService.class);
        adminUserApi = mock(AdminUserApi.class);
        service = new FinanceApprovedSalesBusinessOrderService(mapper, businessOrderService, adminUserApi);
    }

    @Test
    void skipWhenExistingBo() {
        when(mapper.selectCount(any())).thenReturn(1L);
        assertNull(service.createIfEligible(sales()));
    }

    @Test
    void mapsContractAmountAndApplicant() {
        when(mapper.selectCount(any())).thenReturn(0L);
        AdminUserRespDTO user = new AdminUserRespDTO();
        user.setNickname("张三");
        when(adminUserApi.getUser(9L)).thenReturn(CommonResult.success(user));
        when(businessOrderService.createBusinessOrder(any(), eq(9L))).thenReturn(88L);

        Long id = service.createIfEligible(sales());
        assertEquals(88L, id);

        ArgumentCaptor<FinanceBusinessOrderSaveReqVO> cap = ArgumentCaptor.forClass(FinanceBusinessOrderSaveReqVO.class);
        verify(businessOrderService).createBusinessOrder(cap.capture(), eq(9L));
        FinanceBusinessOrderSaveReqVO req = cap.getValue();
        assertEquals(new BigDecimal("1500.00"), req.getSignedExecutionAmount());
        assertEquals(BigDecimal.ZERO, req.getDiscountRate());
        assertEquals("张三", req.getContactPerson());
        assertEquals(7L, req.getContractApplicationId());
        assertEquals(LocalDate.of(2026, 1, 1), req.getExecutionStartDate());
        assertEquals(LocalDate.of(2026, 12, 31), req.getExecutionEndDate());
        assertEquals("客商A", req.getPayerName());
    }

    private static FinanceContractApplicationDO sales() {
        return FinanceContractApplicationDO.builder()
                .id(7L)
                .fileType("销售合同")
                .amountNa(false)
                .contractAmount(new BigDecimal("1500.00"))
                .entityCompanyDeptId(20L)
                .applicantUserId(9L)
                .counterpartyName("客商A")
                .currency("CNY")
                .startDate(LocalDate.of(2026, 1, 1))
                .endDate(LocalDate.of(2026, 12, 31))
                .build();
    }
}
