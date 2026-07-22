package cn.iocoder.yudao.module.finance.service.business;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.finance.controller.admin.business.vo.FinanceBusinessOrderPageReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.business.vo.FinanceBusinessOrderSaveReqVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.business.FinanceBusinessOrderDO;
import cn.iocoder.yudao.module.finance.dal.mysql.business.FinanceBusinessOrderMapper;
import cn.iocoder.yudao.module.finance.enums.FinanceBusinessOrderStatusEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class FinanceBusinessOrderServiceImplTest {

    private FinanceBusinessOrderMapper businessOrderMapper;
    private FinanceBusinessOrderServiceImpl businessOrderService;

    @BeforeEach
    void setUp() {
        businessOrderMapper = mock(FinanceBusinessOrderMapper.class);
        businessOrderService = new FinanceBusinessOrderServiceImpl(businessOrderMapper);
    }

    @Test
    void createBusinessOrderShouldInsertValidOrderWithCurrentOwner() {
        FinanceBusinessOrderSaveReqVO reqVO = order("BO-001", new BigDecimal("100.00"), BigDecimal.ZERO);
        when(businessOrderMapper.selectByOrderNo("BO-001")).thenReturn(null);

        businessOrderService.createBusinessOrder(reqVO, 100L);

        verify(businessOrderMapper).insert(argThat((FinanceBusinessOrderDO order) ->
                "BO-001".equals(order.getOrderNo())
                        && Long.valueOf(100L).equals(order.getOwnerId())
                        && FinanceBusinessOrderStatusEnum.DRAFT.getStatus().equals(order.getStatus())));
    }

    @Test
    void createBusinessOrderShouldRejectDuplicateOrderNo() {
        when(businessOrderMapper.selectByOrderNo("BO-001")).thenReturn(FinanceBusinessOrderDO.builder().id(1L).build());

        assertThrows(RuntimeException.class, () -> businessOrderService.createBusinessOrder(
                order("BO-001", BigDecimal.ONE, BigDecimal.ZERO), 100L));
        verify(businessOrderMapper, never()).insert(any(FinanceBusinessOrderDO.class));
    }

    @Test
    void createBusinessOrderShouldRejectInvalidAmountAndCurrency() {
        FinanceBusinessOrderSaveReqVO zeroAmount = order("BO-001", BigDecimal.ZERO, BigDecimal.ZERO);
        assertThrows(RuntimeException.class, () -> businessOrderService.createBusinessOrder(zeroAmount, 100L));

        FinanceBusinessOrderSaveReqVO invalidCurrency = order("BO-002", BigDecimal.ONE, BigDecimal.ZERO);
        invalidCurrency.setCurrency("cny");
        assertThrows(RuntimeException.class, () -> businessOrderService.createBusinessOrder(invalidCurrency, 100L));

        verify(businessOrderMapper, never()).insert(any(FinanceBusinessOrderDO.class));
    }

    @Test
    void updateBusinessOrderShouldRejectClosedOrder() {
        when(businessOrderMapper.selectById(1L)).thenReturn(FinanceBusinessOrderDO.builder()
                .id(1L).orderNo("BO-001").status(FinanceBusinessOrderStatusEnum.CLOSED.getStatus()).ownerId(100L).build());
        FinanceBusinessOrderSaveReqVO reqVO = order("BO-001", BigDecimal.ONE, BigDecimal.ZERO);
        reqVO.setId(1L);

        assertThrows(RuntimeException.class, () -> businessOrderService.updateBusinessOrder(reqVO));
        verify(businessOrderMapper, never()).updateById(any(FinanceBusinessOrderDO.class));
    }

    @Test
    void deleteBusinessOrderShouldDeleteDraftOnly() {
        when(businessOrderMapper.selectById(1L)).thenReturn(FinanceBusinessOrderDO.builder()
                .id(1L).status(FinanceBusinessOrderStatusEnum.DRAFT.getStatus()).build());
        when(businessOrderMapper.selectById(2L)).thenReturn(FinanceBusinessOrderDO.builder()
                .id(2L).status(FinanceBusinessOrderStatusEnum.ACTIVE.getStatus()).build());

        businessOrderService.deleteBusinessOrder(List.of(1L));
        assertThrows(RuntimeException.class, () -> businessOrderService.deleteBusinessOrder(List.of(2L)));

        verify(businessOrderMapper).deleteByIds(List.of(1L));
    }

    @Test
    void getBusinessOrderPageShouldDelegateToMapper() {
        FinanceBusinessOrderPageReqVO reqVO = new FinanceBusinessOrderPageReqVO();
        PageResult<FinanceBusinessOrderDO> expected = new PageResult<>(List.of(FinanceBusinessOrderDO.builder().id(1L).build()), 1L);
        when(businessOrderMapper.selectPage(reqVO)).thenReturn(expected);

        assertSame(expected, businessOrderService.getBusinessOrderPage(reqVO));
    }

    private static FinanceBusinessOrderSaveReqVO order(String orderNo, BigDecimal receivableAmount, BigDecimal payableAmount) {
        FinanceBusinessOrderSaveReqVO reqVO = new FinanceBusinessOrderSaveReqVO();
        reqVO.setOrderNo(orderNo);
        reqVO.setBusinessSubject("客户 A");
        reqVO.setBusinessType("销售");
        reqVO.setReceivableAmount(receivableAmount);
        reqVO.setPayableAmount(payableAmount);
        reqVO.setCurrency("CNY");
        reqVO.setStatus(FinanceBusinessOrderStatusEnum.DRAFT.getStatus());
        return reqVO;
    }

}
