package cn.iocoder.yudao.module.finance.service.business;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.finance.controller.admin.business.vo.FinanceBusinessOrderPageReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.business.vo.FinanceBusinessOrderSaveReqVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.business.FinanceBusinessOrderDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.contract.FinanceContractApplicationDO;
import cn.iocoder.yudao.module.finance.dal.mysql.business.FinanceBusinessOrderMapper;
import cn.iocoder.yudao.module.finance.dal.mysql.contract.FinanceContractApplicationMapper;
import cn.iocoder.yudao.module.finance.dal.redis.no.FinanceBusinessOrderNoRedisDAO;
import cn.iocoder.yudao.module.finance.enums.FinanceContractApprovalStatusEnum;
import cn.iocoder.yudao.module.finance.service.common.FinanceEntityCompanyResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.BUSINESS_ORDER_RECEIVABLE_BELOW_CONFIRMED;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FinanceBusinessOrderServiceImplTest {

    private static final Long IMPORTER_ID = 100L;
    private static final String ORDER_NO = "BO-20260723-1";
    private static final Long CONTRACT_APP_ID = 50L;
    private static final Long ENTITY_COMPANY_DEPT_ID = 10L;
    private static final String ENTITY_COMPANY_NAME = "示例主体公司";

    private FinanceBusinessOrderMapper businessOrderMapper;
    private FinanceContractApplicationMapper contractApplicationMapper;
    private FinanceEntityCompanyResolver entityCompanyResolver;
    private FinanceBusinessOrderServiceImpl businessOrderService;

    @BeforeEach
    void setUp() {
        businessOrderMapper = mock(FinanceBusinessOrderMapper.class);
        contractApplicationMapper = mock(FinanceContractApplicationMapper.class);
        entityCompanyResolver = mock(FinanceEntityCompanyResolver.class);
        FinanceBusinessOrderNoRedisDAO orderNoRedisDAO = mock(FinanceBusinessOrderNoRedisDAO.class);
        businessOrderService = new FinanceBusinessOrderServiceImpl(
                businessOrderMapper, orderNoRedisDAO, contractApplicationMapper, entityCompanyResolver);
        when(orderNoRedisDAO.generate(any(LocalDate.class))).thenReturn(ORDER_NO);
        when(contractApplicationMapper.selectById(anyLong())).thenReturn(FinanceContractApplicationDO.builder()
                .id(CONTRACT_APP_ID)
                .applicationNo("CT-1")
                .approvalStatus(FinanceContractApprovalStatusEnum.APPROVED.getStatus())
                .applicantUserId(IMPORTER_ID)
                .voided(false)
                .build());
        when(entityCompanyResolver.requireByDeptId(ENTITY_COMPANY_DEPT_ID))
                .thenReturn(new FinanceEntityCompanyResolver.ResolvedCompany(
                        ENTITY_COMPANY_DEPT_ID, ENTITY_COMPANY_NAME));
    }

    @Test
    void createBusinessOrderShouldGenerateMetadataAndSettlementWhenSheetFieldsAreValid() {
        FinanceBusinessOrderSaveReqVO reqVO = validOrder();
        reqVO.setDiscountRate(null);
        reqVO.setContractApplicationId(CONTRACT_APP_ID);

        businessOrderService.createBusinessOrder(reqVO, IMPORTER_ID);

        verify(businessOrderMapper).insert(argThat((FinanceBusinessOrderDO order) ->
                ORDER_NO.equals(order.getOrderNo())
                        && LocalDate.now().equals(order.getImportDate())
                        && IMPORTER_ID.equals(order.getImporterId())
                        && ENTITY_COMPANY_DEPT_ID.equals(order.getEntityCompanyDeptId())
                        && ENTITY_COMPANY_NAME.equals(order.getEntityCompanyName())
                        && CONTRACT_APP_ID.equals(order.getContractApplicationId())
                        && "产品A".equals(order.getProductName())
                        && "张三".equals(order.getContactPerson())
                        && "付款公司".equals(order.getPayerName())
                        && new BigDecimal("1000.00").compareTo(order.getSignedExecutionAmount()) == 0
                        && BigDecimal.ZERO.compareTo(order.getDiscountRate()) == 0
                        && new BigDecimal("1000.00").compareTo(order.getSettlementAmount()) == 0
                        && BigDecimal.ZERO.compareTo(order.getConfirmedClaimedAmount()) == 0
                        && "备注内容".equals(order.getRemark())));
    }

    @Test
    void createBusinessOrderShouldRoundSettlementHalfUp() {
        FinanceBusinessOrderSaveReqVO reqVO = validOrder();
        reqVO.setSignedExecutionAmount(new BigDecimal("1000.005"));
        reqVO.setDiscountRate(new BigDecimal("0.001"));

        businessOrderService.createBusinessOrder(reqVO, IMPORTER_ID);

        verify(businessOrderMapper).insert(argThat((FinanceBusinessOrderDO order) ->
                new BigDecimal("1000.01").equals(order.getSignedExecutionAmount())
                        && new BigDecimal("0.001000").equals(order.getDiscountRate())
                        && new BigDecimal("999.01").equals(order.getSettlementAmount())));
    }

    @Test
    void createBusinessOrderShouldRejectNonPositiveExecutionAmount() {
        FinanceBusinessOrderSaveReqVO reqVO = validOrder();
        reqVO.setSignedExecutionAmount(BigDecimal.ZERO);

        assertThrows(IllegalArgumentException.class,
                () -> businessOrderService.createBusinessOrder(reqVO, IMPORTER_ID));

        verify(businessOrderMapper, never()).insert(any(FinanceBusinessOrderDO.class));
    }

    @Test
    void createBusinessOrderShouldRejectExecutionEndBeforeStart() {
        FinanceBusinessOrderSaveReqVO reqVO = validOrder();
        reqVO.setExecutionEndDate(reqVO.getExecutionStartDate().minusDays(1));

        assertThrows(IllegalArgumentException.class,
                () -> businessOrderService.createBusinessOrder(reqVO, IMPORTER_ID));

        verify(businessOrderMapper, never()).insert(any(FinanceBusinessOrderDO.class));
    }

    @Test
    void createBusinessOrderShouldRejectDiscountOutsideZeroToOne() {
        FinanceBusinessOrderSaveReqVO reqVO = validOrder();
        reqVO.setDiscountRate(new BigDecimal("1.01"));

        assertThrows(IllegalArgumentException.class,
                () -> businessOrderService.createBusinessOrder(reqVO, IMPORTER_ID));

        verify(businessOrderMapper, never()).insert(any(FinanceBusinessOrderDO.class));
    }

    @Test
    void updateBusinessOrderShouldPreserveGeneratedMetadataAndRecomputeSettlement() {
        when(businessOrderMapper.selectById(1L)).thenReturn(FinanceBusinessOrderDO.builder()
                .id(1L).orderNo(ORDER_NO).importDate(LocalDate.of(2026, 7, 1)).importerId(IMPORTER_ID)
                .contractApplicationId(CONTRACT_APP_ID)
                .confirmedClaimedAmount(new BigDecimal("50.00")).sourceRowHash("hash").build());
        FinanceBusinessOrderSaveReqVO reqVO = validOrder();
        reqVO.setId(1L);
        reqVO.setDiscountRate(new BigDecimal("0.25"));

        businessOrderService.updateBusinessOrder(reqVO);

        // CS-F11：普通 update 不写合同列（null 跳过），合同仅 CAS
        verify(businessOrderMapper).updateById(argThat((FinanceBusinessOrderDO order) ->
                ORDER_NO.equals(order.getOrderNo())
                        && LocalDate.of(2026, 7, 1).equals(order.getImportDate())
                        && IMPORTER_ID.equals(order.getImporterId())
                        && ENTITY_COMPANY_DEPT_ID.equals(order.getEntityCompanyDeptId())
                        && ENTITY_COMPANY_NAME.equals(order.getEntityCompanyName())
                        && order.getContractApplicationId() == null
                        && new BigDecimal("750.00").compareTo(order.getSettlementAmount()) == 0
                        && new BigDecimal("50.00").compareTo(order.getConfirmedClaimedAmount()) == 0
                        && "hash".equals(order.getSourceRowHash())));
    }

    @Test
    void updateBusinessOrderShouldRejectSettlementBelowConfirmedClaimedAmount() {
        when(businessOrderMapper.selectById(1L)).thenReturn(FinanceBusinessOrderDO.builder()
                .id(1L)
                .importerId(IMPORTER_ID)
                .contractApplicationId(CONTRACT_APP_ID)
                .confirmedClaimedAmount(new BigDecimal("800.01")).build());
        FinanceBusinessOrderSaveReqVO reqVO = validOrder();
        reqVO.setId(1L);
        reqVO.setDiscountRate(new BigDecimal("0.20"));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> businessOrderService.updateBusinessOrder(reqVO));

        org.junit.jupiter.api.Assertions.assertEquals(BUSINESS_ORDER_RECEIVABLE_BELOW_CONFIRMED.getCode(),
                exception.getCode());

        verify(businessOrderMapper, never()).updateById(any(FinanceBusinessOrderDO.class));
    }

    @Test
    void deleteBusinessOrderShouldDeleteExistingOrder() {
        when(businessOrderMapper.selectById(1L)).thenReturn(FinanceBusinessOrderDO.builder().id(1L).build());

        businessOrderService.deleteBusinessOrder(List.of(1L));

        verify(businessOrderMapper).deleteByIds(List.of(1L));
    }

    @Test
    void getBusinessOrderPageShouldDelegateToMapper() {
        FinanceBusinessOrderPageReqVO reqVO = new FinanceBusinessOrderPageReqVO();
        PageResult<FinanceBusinessOrderDO> expected = new PageResult<>(
                List.of(FinanceBusinessOrderDO.builder().id(1L).build()), 1L);
        when(businessOrderMapper.selectPage(reqVO)).thenReturn(expected);

        assertSame(expected, businessOrderService.getBusinessOrderPage(reqVO));
    }

    private static FinanceBusinessOrderSaveReqVO validOrder() {
        FinanceBusinessOrderSaveReqVO reqVO = new FinanceBusinessOrderSaveReqVO();
        reqVO.setEntityCompanyDeptId(ENTITY_COMPANY_DEPT_ID);
        reqVO.setContractApplicationId(CONTRACT_APP_ID);
        reqVO.setOrderDate(LocalDate.of(2026, 7, 1));
        reqVO.setProductName("产品A");
        reqVO.setContactPerson("张三");
        reqVO.setExecutionStartDate(LocalDate.of(2026, 7, 5));
        reqVO.setExecutionEndDate(LocalDate.of(2026, 7, 31));
        reqVO.setPayerName("付款公司");
        reqVO.setSignedExecutionAmount(new BigDecimal("1000.00"));
        reqVO.setDiscountRate(new BigDecimal("0.10"));
        reqVO.setRemark("备注内容");
        return reqVO;
    }

}
