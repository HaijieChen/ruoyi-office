package cn.iocoder.yudao.module.finance.service.invoice;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.yudao.module.finance.controller.admin.invoice.vo.FinanceInvoiceApplicationCreateAndStartReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.invoice.vo.FinanceInvoiceApplicationResubmitReqVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.business.FinanceBusinessOrderDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.customer.FinanceCustomerCompanyDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.invoice.FinanceInvoiceApplicationDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.invoice.FinanceInvoiceApplicationLineDO;
import cn.iocoder.yudao.module.finance.dal.mysql.business.FinanceBusinessOrderMapper;
import cn.iocoder.yudao.module.finance.dal.mysql.invoice.FinanceInvoiceApplicationFileMapper;
import cn.iocoder.yudao.module.finance.dal.mysql.invoice.FinanceInvoiceApplicationLineMapper;
import cn.iocoder.yudao.module.finance.dal.mysql.invoice.FinanceInvoiceApplicationMapper;
import cn.iocoder.yudao.module.finance.dal.redis.no.FinanceInvoiceApplicationNoRedisDAO;
import cn.iocoder.yudao.module.finance.enums.FinanceInvoiceApprovalStatusEnum;
import cn.iocoder.yudao.module.finance.enums.FinanceInvoiceIssueStatusEnum;
import cn.iocoder.yudao.module.finance.service.customer.FinanceCustomerCompanyService;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.INVOICE_APPLICATION_BUSINESS_ORDER_NOT_EXISTS;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.INVOICE_APPLICATION_BUSINESS_ORDER_PRODUCT_MISSING;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.INVOICE_APPLICATION_CUSTOMER_COMPANY_DISABLED;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.INVOICE_APPLICATION_OCCUPY_CONCURRENT;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.INVOICE_APPLICATION_OCCUPY_EXCEED;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.INVOICE_APPLICATION_PRODUCT_MIXED;
import static cn.iocoder.yudao.module.finance.service.invoice.FinanceInvoiceApplicationServiceImpl.PROCESS_KEY;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
// isNull used by resubmit wrapper verify

/**
 * createAndStart：占用失败零副作用、成功启流回写、并发 CAS 失败。
 */
class FinanceInvoiceApplicationServiceImplTest {

    private FinanceInvoiceApplicationMapper applicationMapper;
    private FinanceInvoiceApplicationLineMapper lineMapper;
    private FinanceInvoiceApplicationFileMapper fileMapper;
    private FinanceBusinessOrderMapper businessOrderMapper;
    private FinanceInvoiceApplicationNoRedisDAO applicationNoRedisDAO;
    private BpmProcessInstanceApi processInstanceApi;
    private FinanceCustomerCompanyService customerCompanyService;
    private DictDataApi dictDataApi;
    private FinanceInvoiceApplicationServiceImpl service;

    @BeforeEach
    void setUp() {
        applicationMapper = mock(FinanceInvoiceApplicationMapper.class);
        lineMapper = mock(FinanceInvoiceApplicationLineMapper.class);
        fileMapper = mock(FinanceInvoiceApplicationFileMapper.class);
        businessOrderMapper = mock(FinanceBusinessOrderMapper.class);
        applicationNoRedisDAO = mock(FinanceInvoiceApplicationNoRedisDAO.class);
        processInstanceApi = mock(BpmProcessInstanceApi.class);
        customerCompanyService = mock(FinanceCustomerCompanyService.class);
        dictDataApi = mock(DictDataApi.class);
        cn.iocoder.yudao.module.finance.service.common.FinanceEntityCompanyResolver entityCompanyResolver =
                mock(cn.iocoder.yudao.module.finance.service.common.FinanceEntityCompanyResolver.class);
        when(entityCompanyResolver.requireByDeptId(anyLong())).thenReturn(
                new cn.iocoder.yudao.module.finance.service.common.FinanceEntityCompanyResolver.ResolvedCompany(
                        20L, "开票公司", "CNY"));
        when(dictDataApi.validateDictDataList(anyString(), anyCollection())).thenReturn(CommonResult.success(true));
        service = new FinanceInvoiceApplicationServiceImpl(applicationMapper, lineMapper, fileMapper, businessOrderMapper,
                applicationNoRedisDAO, processInstanceApi, customerCompanyService, entityCompanyResolver, dictDataApi);

        when(applicationNoRedisDAO.generate(any(LocalDate.class))).thenReturn("INV-20260729-1");
        when(customerCompanyService.getEnabledCustomerCompany(anyLong())).thenReturn(
                FinanceCustomerCompanyDO.builder()
                        .id(50L)
                        .name("购方A")
                        .taxNo("91110000MA0000000X")
                        .address("北京市朝阳区")
                        .phone("010-12345678")
                        .bankName("开户行")
                        .bankAccount("622200001111")
                        .status(FinanceCustomerCompanyDO.STATUS_ENABLE)
                        .build());
        doAnswer(invocation -> {
            FinanceInvoiceApplicationDO app = invocation.getArgument(0);
            app.setId(100L);
            return 1;
        }).when(applicationMapper).insert(any(FinanceInvoiceApplicationDO.class));
        doAnswer(invocation -> {
            FinanceInvoiceApplicationLineDO line = invocation.getArgument(0);
            line.setId(1L);
            return 1;
        }).when(lineMapper).insert(any(FinanceInvoiceApplicationLineDO.class));
    }

    @Test
    void createAndStartShouldFailOnExcessOccupyWithoutSideEffects() {
        when(businessOrderMapper.selectListByIds(any())).thenReturn(List.of(
                order(10L, "100.00", "80.00")));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.createAndStart(req(line(10L, "50.00")), 200L));

        assertEquals(INVOICE_APPLICATION_OCCUPY_EXCEED.getCode(), ex.getCode());
        verify(applicationMapper, never()).insert(any(FinanceInvoiceApplicationDO.class));
        verify(lineMapper, never()).insert(any(FinanceInvoiceApplicationLineDO.class));
        verify(businessOrderMapper, never()).increaseInvoicedOccupiedAmount(anyLong(), any(), any(), anyString());
        verifyNoInteractions(processInstanceApi);
    }

    @Test
    void createAndStartShouldIgnoreClientTaxContentAndDeriveFromBusinessOrder() {
        when(businessOrderMapper.selectListByIds(any())).thenReturn(List.of(
                order(10L, "100.00", "0.00", 50L, "软件")));
        when(businessOrderMapper.increaseInvoicedOccupiedAmount(eq(10L), eq(new BigDecimal("10.00")), any(), eq("软件"))).thenReturn(1);
        when(processInstanceApi.createProcessInstance(eq(200L), any(BpmProcessInstanceCreateReqDTO.class)))
                .thenReturn(CommonResult.success("proc-1"));

        FinanceInvoiceApplicationCreateAndStartReqVO req = req(line(10L, "10.00"));
        req.setTaxContent("客户端篡改产品");

        service.createAndStart(req, 200L);

        ArgumentCaptor<FinanceInvoiceApplicationDO> appCaptor = ArgumentCaptor.forClass(FinanceInvoiceApplicationDO.class);
        verify(applicationMapper).insert(appCaptor.capture());
        assertEquals("软件", appCaptor.getValue().getTaxContent());

        ArgumentCaptor<FinanceInvoiceApplicationLineDO> lineCaptor =
                ArgumentCaptor.forClass(FinanceInvoiceApplicationLineDO.class);
        verify(lineMapper).insert(lineCaptor.capture());
        assertEquals("软件", lineCaptor.getValue().getProductTypeSnapshot());
        assertEquals(50L, lineCaptor.getValue().getSourceContractApplicationId());
    }

    @Test
    void createAndStartShouldRejectWhenBusinessOrderProductMissing() {
        when(businessOrderMapper.selectListByIds(any())).thenReturn(List.of(
                order(10L, "100.00", "0.00", 50L, null)));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.createAndStart(req(line(10L, "10.00")), 200L));

        assertEquals(INVOICE_APPLICATION_BUSINESS_ORDER_PRODUCT_MISSING.getCode(), ex.getCode());
        verify(applicationMapper, never()).insert(any(FinanceInvoiceApplicationDO.class));
        verifyNoInteractions(processInstanceApi);
    }

    @Test
    void createAndStartShouldRejectLegacyProductNameWithoutSnapshot() {
        // P1 #3：仅有 product_name、无 snapshot、或无合同时不得新开票
        FinanceBusinessOrderDO legacyOnly = FinanceBusinessOrderDO.builder()
                .id(10L)
                .settlementAmount(new BigDecimal("100.00"))
                .invoicedOccupiedAmount(BigDecimal.ZERO)
                .contractApplicationId(50L)
                .productTypeSnapshot(null)
                .productName("自由文本旧产品")
                .build();
        when(businessOrderMapper.selectListByIds(any())).thenReturn(List.of(legacyOnly));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.createAndStart(req(line(10L, "10.00")), 200L));
        assertEquals(INVOICE_APPLICATION_BUSINESS_ORDER_PRODUCT_MISSING.getCode(), ex.getCode());
        verify(applicationMapper, never()).insert(any(FinanceInvoiceApplicationDO.class));
    }

    @Test
    void createAndStartShouldRejectNullContractBusinessOrder() {
        FinanceBusinessOrderDO noContract = FinanceBusinessOrderDO.builder()
                .id(10L)
                .settlementAmount(new BigDecimal("100.00"))
                .invoicedOccupiedAmount(BigDecimal.ZERO)
                .contractApplicationId(null)
                .productTypeSnapshot("软件")
                .productName("软件")
                .build();
        when(businessOrderMapper.selectListByIds(any())).thenReturn(List.of(noContract));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.createAndStart(req(line(10L, "10.00")), 200L));
        assertEquals(INVOICE_APPLICATION_BUSINESS_ORDER_PRODUCT_MISSING.getCode(), ex.getCode());
    }

    @Test
    void createAndStartShouldRejectMixedProductsAcrossBusinessOrders() {
        when(businessOrderMapper.selectListByIds(any())).thenReturn(List.of(
                order(10L, "200.00", "0.00", 50L, "软件"),
                order(11L, "100.00", "0.00", 51L, "硬件")));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.createAndStart(req(line(10L, "10.00"), line(11L, "10.00")), 200L));

        assertEquals(INVOICE_APPLICATION_PRODUCT_MIXED.getCode(), ex.getCode());
        verify(applicationMapper, never()).insert(any(FinanceInvoiceApplicationDO.class));
        verifyNoInteractions(processInstanceApi);
    }

    @Test
    void createAndStartShouldOccupyAndWriteProcessInstanceId() {
        when(businessOrderMapper.selectListByIds(any())).thenReturn(List.of(
                order(10L, "200.00", "20.00", 50L, "软件"),
                order(11L, "100.00", "0.00", 50L, "软件")));
        when(businessOrderMapper.increaseInvoicedOccupiedAmount(eq(10L), eq(new BigDecimal("80.00")), any(), eq("软件"))).thenReturn(1);
        when(businessOrderMapper.increaseInvoicedOccupiedAmount(eq(11L), eq(new BigDecimal("30.00")), any(), eq("软件"))).thenReturn(1);
        when(processInstanceApi.createProcessInstance(eq(200L), any(BpmProcessInstanceCreateReqDTO.class)))
                .thenReturn(CommonResult.success("proc-xyz"));

        Long appId = service.createAndStart(req(
                line(10L, "50.00"),
                line(10L, "30.00"),
                line(11L, "30.00")), 200L);

        assertEquals(100L, appId);

        ArgumentCaptor<FinanceInvoiceApplicationDO> appCaptor = ArgumentCaptor.forClass(FinanceInvoiceApplicationDO.class);
        verify(applicationMapper).insert(appCaptor.capture());
        FinanceInvoiceApplicationDO inserted = appCaptor.getValue();
        assertEquals("INV-20260729-1", inserted.getApplicationNo());
        assertEquals(FinanceInvoiceApprovalStatusEnum.PENDING.getStatus(), inserted.getApprovalStatus());
        assertEquals(FinanceInvoiceIssueStatusEnum.NONE.getStatus(), inserted.getIssueStatus());
        assertEquals(0, inserted.getTotalAmount().compareTo(new BigDecimal("110.00")));
        assertEquals(0, inserted.getConfirmedClaimedAmount().compareTo(new BigDecimal("0.00")));
        assertEquals(0, inserted.getPendingClaimedAmount().compareTo(new BigDecimal("0.00")));
        assertEquals(Boolean.FALSE, inserted.getVoided());
        assertEquals(200L, inserted.getApplicantUserId());
        assertEquals("购方A", inserted.getBuyerName());
        assertEquals("91110000MA0000000X", inserted.getBuyerTaxNo());
        assertEquals(50L, inserted.getCustomerCompanyId());
        assertEquals("北京市朝阳区 010-12345678", inserted.getBuyerAddressPhone());
        assertEquals("开户行 622200001111", inserted.getBuyerBankAccount());
        assertEquals("软件", inserted.getTaxContent());

        verify(lineMapper, times(3)).insert(any(FinanceInvoiceApplicationLineDO.class));
        // 同 BO 明细汇总后一次占用
        verify(businessOrderMapper).increaseInvoicedOccupiedAmount(eq(10L), eq(new BigDecimal("80.00")), any(), eq("软件"));
        verify(businessOrderMapper).increaseInvoicedOccupiedAmount(eq(11L), eq(new BigDecimal("30.00")), any(), eq("软件"));

        ArgumentCaptor<BpmProcessInstanceCreateReqDTO> bpmCaptor =
                ArgumentCaptor.forClass(BpmProcessInstanceCreateReqDTO.class);
        verify(processInstanceApi).createProcessInstance(eq(200L), bpmCaptor.capture());
        BpmProcessInstanceCreateReqDTO bpmReq = bpmCaptor.getValue();
        assertEquals(PROCESS_KEY, bpmReq.getProcessDefinitionKey());
        assertEquals("100", bpmReq.getBusinessKey());
        assertEquals(new BigDecimal("110.00"), bpmReq.getVariables().get("totalAmount"));
        assertEquals("购方A", bpmReq.getVariables().get("buyerName"));
        assertEquals(200L, bpmReq.getVariables().get("applicantUserId"));
        assertEquals("INV-20260729-1", bpmReq.getVariables().get("applicationNo"));
        assertEquals("软件", bpmReq.getVariables().get("taxContent"));

        ArgumentCaptor<FinanceInvoiceApplicationDO> updateCaptor =
                ArgumentCaptor.forClass(FinanceInvoiceApplicationDO.class);
        verify(applicationMapper).updateById(updateCaptor.capture());
        assertEquals(100L, updateCaptor.getValue().getId());
        assertEquals("proc-xyz", updateCaptor.getValue().getProcessInstanceId());
    }

    @Test
    void createAndStartShouldFailWhenConcurrentOccupyReturnsZero() {
        when(businessOrderMapper.selectListByIds(any())).thenReturn(List.of(
                order(10L, "100.00", "0.00")));
        // 预检通过，CAS 返回 0（并发第二单 / 合同产品被换）
        when(businessOrderMapper.increaseInvoicedOccupiedAmount(eq(10L), eq(new BigDecimal("60.00")), any(), eq("软件"))).thenReturn(0);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.createAndStart(req(line(10L, "60.00")), 200L));

        assertEquals(INVOICE_APPLICATION_OCCUPY_CONCURRENT.getCode(), ex.getCode());
        verify(applicationMapper).insert(any(FinanceInvoiceApplicationDO.class));
        verify(lineMapper).insert(any(FinanceInvoiceApplicationLineDO.class));
        verify(businessOrderMapper).increaseInvoicedOccupiedAmount(eq(10L), eq(new BigDecimal("60.00")), any(), eq("软件"));
        verifyNoInteractions(processInstanceApi);
        verify(applicationMapper, never()).updateById(any(FinanceInvoiceApplicationDO.class));
    }

    @Test
    void resubmitShouldRejectWhenSecondBoSelectReturnsNull() {
        // P2 #4：释占后二次 selectById 为空须业务错误，非 NPE
        FinanceInvoiceApplicationDO rejected = pendingApp(100L);
        rejected.setApprovalStatus(FinanceInvoiceApprovalStatusEnum.REJECTED.getStatus());
        when(applicationMapper.selectById(100L)).thenReturn(rejected);
        when(lineMapper.selectListByApplicationId(100L)).thenReturn(List.of(
                FinanceInvoiceApplicationLineDO.builder()
                        .id(1L).applicationId(100L).businessOrderId(10L)
                        .amount(new BigDecimal("10.00")).build()));
        when(businessOrderMapper.selectListByIds(any())).thenReturn(List.of(
                order(10L, "100.00", "0.00", 50L, "软件")));
        when(businessOrderMapper.selectById(10L)).thenReturn(null);

        FinanceInvoiceApplicationResubmitReqVO resubmitReq = new FinanceInvoiceApplicationResubmitReqVO();
        resubmitReq.setId(100L);
        resubmitReq.setCustomerCompanyId(50L);
        resubmitReq.setInvoiceCompanyDeptId(20L);
        resubmitReq.setCurrency("CNY");
        resubmitReq.setTaxContent("软件");
        resubmitReq.setLines(List.of(line(10L, "10.00")));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.resubmit(100L, resubmitReq, 200L));
        assertEquals(INVOICE_APPLICATION_BUSINESS_ORDER_NOT_EXISTS.getCode(), ex.getCode());
        verify(processInstanceApi, never()).createProcessInstance(anyLong(), any());
    }

    @Test
    void createAndStartOccupyCasMustPassExpectedContractAndProduct() {
        // #1：占用 CAS 必须携带读快照时的合同+产品期望值
        when(businessOrderMapper.selectListByIds(any())).thenReturn(List.of(
                order(10L, "100.00", "0.00", 77L, "软件")));
        when(businessOrderMapper.increaseInvoicedOccupiedAmount(
                eq(10L), eq(new BigDecimal("10.00")), eq(77L), eq("软件"))).thenReturn(1);
        when(processInstanceApi.createProcessInstance(eq(200L), any(BpmProcessInstanceCreateReqDTO.class)))
                .thenReturn(CommonResult.success("proc-cas"));

        service.createAndStart(req(line(10L, "10.00")), 200L);

        verify(businessOrderMapper).increaseInvoicedOccupiedAmount(
                eq(10L), eq(new BigDecimal("10.00")), eq(77L), eq("软件"));
    }

    @Test
    void createAndStartShouldRejectDisabledCustomerCompany() {
        when(customerCompanyService.getEnabledCustomerCompany(99L)).thenThrow(
                new ServiceException(INVOICE_APPLICATION_CUSTOMER_COMPANY_DISABLED));
        when(businessOrderMapper.selectListByIds(any())).thenReturn(List.of(
                order(10L, "100.00", "0.00")));

        FinanceInvoiceApplicationCreateAndStartReqVO reqVO = req(line(10L, "10.00"));
        reqVO.setCustomerCompanyId(99L);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.createAndStart(reqVO, 200L));
        assertEquals(INVOICE_APPLICATION_CUSTOMER_COMPANY_DISABLED.getCode(), ex.getCode());
        verify(applicationMapper, never()).insert(any(FinanceInvoiceApplicationDO.class));
        verifyNoInteractions(processInstanceApi);
    }

    @Test
    void resubmitShouldRefreshBuyerSnapshotFromCurrentCompanyIncludingEmptySegments() {
        FinanceInvoiceApplicationDO rejected = pendingApp(100L);
        rejected.setApprovalStatus(FinanceInvoiceApprovalStatusEnum.REJECTED.getStatus());
        rejected.setBuyerName("旧购方");
        rejected.setBuyerTaxNo("OLDTAX");
        rejected.setBuyerAddressPhone("旧地址 旧电话");
        rejected.setBuyerBankAccount("旧行 旧账号");
        rejected.setCustomerCompanyId(50L);

        when(applicationMapper.selectById(100L)).thenReturn(rejected);
        // 驳回后占用已空
        when(lineMapper.selectListByApplicationId(100L)).thenReturn(List.of(
                FinanceInvoiceApplicationLineDO.builder()
                        .id(1L).applicationId(100L).businessOrderId(10L)
                        .amount(new BigDecimal("30.00")).build()));
        when(businessOrderMapper.selectById(10L)).thenReturn(order(10L, "200.00", "0.00"));
        when(businessOrderMapper.selectListByIds(any())).thenReturn(List.of(order(10L, "200.00", "0.00")));
        when(businessOrderMapper.increaseInvoicedOccupiedAmount(eq(10L), eq(new BigDecimal("30.00")), any(), eq("软件"))).thenReturn(1);
        when(processInstanceApi.createProcessInstance(eq(200L), any(BpmProcessInstanceCreateReqDTO.class)))
                .thenReturn(CommonResult.success("proc-resubmit"));

        // 档案已改税号，且清空银行/地址 → 合成 null
        when(customerCompanyService.getEnabledCustomerCompany(50L)).thenReturn(
                FinanceCustomerCompanyDO.builder()
                        .id(50L)
                        .name("新购方")
                        .taxNo("NEWTAX001")
                        .address(null)
                        .phone(null)
                        .bankName(null)
                        .bankAccount(null)
                        .status(FinanceCustomerCompanyDO.STATUS_ENABLE)
                        .build());

        FinanceInvoiceApplicationResubmitReqVO resubmitReq = new FinanceInvoiceApplicationResubmitReqVO();
        resubmitReq.setId(100L);
        resubmitReq.setCustomerCompanyId(50L);
        resubmitReq.setBuyerName("客户端伪造");
        resubmitReq.setBuyerTaxNo("FAKE");
        resubmitReq.setInvoiceCompanyDeptId(20L);
        resubmitReq.setInvoiceCompany("开票公司");
        resubmitReq.setCurrency("CNY");
        resubmitReq.setInvoiceType("普票");
        resubmitReq.setTaxContent("软件");
        resubmitReq.setLines(List.of(line(10L, "30.00")));

        service.resubmit(100L, resubmitReq, 200L);

        // resubmit 表头快照走 UpdateWrapper（可 set null）
        verify(applicationMapper, atLeastOnce()).update(isNull(), any());
        verify(processInstanceApi).createProcessInstance(eq(200L), any(BpmProcessInstanceCreateReqDTO.class));
    }

    @Test
    void resubmitShouldRejectDisabledCustomerCompany() {
        FinanceInvoiceApplicationDO rejected = pendingApp(100L);
        rejected.setApprovalStatus(FinanceInvoiceApprovalStatusEnum.REJECTED.getStatus());
        when(applicationMapper.selectById(100L)).thenReturn(rejected);
        when(lineMapper.selectListByApplicationId(100L)).thenReturn(List.of());
        when(businessOrderMapper.selectListByIds(any())).thenReturn(List.of(order(10L, "100.00", "0.00")));
        when(businessOrderMapper.selectById(10L)).thenReturn(order(10L, "100.00", "0.00"));
        when(customerCompanyService.getEnabledCustomerCompany(88L)).thenThrow(
                new ServiceException(INVOICE_APPLICATION_CUSTOMER_COMPANY_DISABLED));

        FinanceInvoiceApplicationResubmitReqVO resubmitReq = new FinanceInvoiceApplicationResubmitReqVO();
        resubmitReq.setId(100L);
        resubmitReq.setCustomerCompanyId(88L);
        resubmitReq.setInvoiceCompanyDeptId(20L);
        resubmitReq.setCurrency("CNY");
        resubmitReq.setTaxContent("软件");
        resubmitReq.setLines(List.of(line(10L, "10.00")));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.resubmit(100L, resubmitReq, 200L));
        assertEquals(INVOICE_APPLICATION_CUSTOMER_COMPANY_DISABLED.getCode(), ex.getCode());
        verify(processInstanceApi, never()).createProcessInstance(anyLong(), any());
    }

    private static FinanceInvoiceApplicationCreateAndStartReqVO req(
            FinanceInvoiceApplicationCreateAndStartReqVO.Line... lines) {
        FinanceInvoiceApplicationCreateAndStartReqVO reqVO = new FinanceInvoiceApplicationCreateAndStartReqVO();
        reqVO.setCustomerCompanyId(50L);
        // 客户端伪造税项：服务端应以档案覆盖
        reqVO.setBuyerName("伪造购方");
        reqVO.setBuyerTaxNo("FAKE");
        reqVO.setInvoiceCompanyDeptId(20L);
        reqVO.setInvoiceCompany("开票公司");
        reqVO.setCurrency("CNY");
        reqVO.setInvoiceType("专票");
        reqVO.setTaxContent("软件");
        reqVO.setLines(List.of(lines));
        return reqVO;
    }

    private static FinanceInvoiceApplicationCreateAndStartReqVO.Line line(Long businessOrderId, String amount) {
        FinanceInvoiceApplicationCreateAndStartReqVO.Line line =
                new FinanceInvoiceApplicationCreateAndStartReqVO.Line();
        line.setBusinessOrderId(businessOrderId);
        line.setAmount(new BigDecimal(amount));
        return line;
    }

    private static FinanceBusinessOrderDO order(Long id, String settlement, String occupied) {
        return order(id, settlement, occupied, 50L, "软件");
    }

    private static FinanceBusinessOrderDO order(Long id, String settlement, String occupied,
                                                Long contractId, String productTypeSnapshot) {
        return FinanceBusinessOrderDO.builder()
                .id(id)
                .settlementAmount(new BigDecimal(settlement))
                .invoicedOccupiedAmount(new BigDecimal(occupied))
                .currency("CNY")
                .contractApplicationId(contractId)
                .productTypeSnapshot(productTypeSnapshot)
                .build();
    }

    // ---------- T3 / T4 ----------

    @Test
    void onApprovalOutcomeApprovedShouldNotReleaseOccupy() {
        when(applicationMapper.selectById(100L)).thenReturn(pendingApp(100L));
        service.onApprovalOutcome(100L, "APPROVED");
        ArgumentCaptor<FinanceInvoiceApplicationDO> captor =
                ArgumentCaptor.forClass(FinanceInvoiceApplicationDO.class);
        verify(applicationMapper).updateById(captor.capture());
        assertEquals(FinanceInvoiceApprovalStatusEnum.APPROVED.getStatus(),
                captor.getValue().getApprovalStatus());
        verify(businessOrderMapper, never()).decreaseInvoicedOccupiedAmount(anyLong(), any());
    }

    @Test
    void onApprovalOutcomeRejectedShouldReleaseOccupy() {
        when(applicationMapper.selectById(100L)).thenReturn(pendingApp(100L));
        when(lineMapper.selectListByApplicationId(100L)).thenReturn(List.of(
                FinanceInvoiceApplicationLineDO.builder().id(1L).applicationId(100L)
                        .businessOrderId(10L).amount(new BigDecimal("50.00")).build()));
        when(businessOrderMapper.decreaseInvoicedOccupiedAmount(eq(10L), eq(new BigDecimal("50.00"))))
                .thenReturn(1);

        service.onApprovalOutcome(100L, "REJECTED");

        verify(businessOrderMapper).decreaseInvoicedOccupiedAmount(10L, new BigDecimal("50.00"));
        ArgumentCaptor<FinanceInvoiceApplicationDO> captor =
                ArgumentCaptor.forClass(FinanceInvoiceApplicationDO.class);
        verify(applicationMapper).updateById(captor.capture());
        assertEquals(FinanceInvoiceApprovalStatusEnum.REJECTED.getStatus(),
                captor.getValue().getApprovalStatus());
    }

    @Test
    void onApprovalOutcomeIdempotentSameOutcome() {
        FinanceInvoiceApplicationDO approved = pendingApp(100L);
        approved.setApprovalStatus(FinanceInvoiceApprovalStatusEnum.APPROVED.getStatus());
        when(applicationMapper.selectById(100L)).thenReturn(approved);
        service.onApprovalOutcome(100L, "APPROVED");
        verify(applicationMapper, never()).updateById(any(FinanceInvoiceApplicationDO.class));
        verify(businessOrderMapper, never()).decreaseInvoicedOccupiedAmount(anyLong(), any());
    }

    @Test
    void updateIssueProgressShouldRedirectToCompleteIssue() {
        cn.iocoder.yudao.module.finance.controller.admin.invoice.vo.FinanceInvoiceApplicationUpdateIssueProgressReqVO req =
                new cn.iocoder.yudao.module.finance.controller.admin.invoice.vo.FinanceInvoiceApplicationUpdateIssueProgressReqVO();
        req.setApplicationId(100L);
        req.setLineId(1L);
        req.setInvoiceNo("INV-NO-1");
        ServiceException ex = assertThrows(ServiceException.class, () -> service.updateIssueProgress(req));
        assertEquals(cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.INVOICE_APPLICATION_USE_COMPLETE_ISSUE.getCode(),
                ex.getCode());
        verify(applicationMapper, never()).updateById(any(FinanceInvoiceApplicationDO.class));
        verify(lineMapper, never()).updateById(any(FinanceInvoiceApplicationLineDO.class));
    }

    @Test
    void completeIssueShouldWriteFullAndReplaceFiles() {
        FinanceInvoiceApplicationDO app = pendingApp(100L);
        app.setApprovalStatus(FinanceInvoiceApprovalStatusEnum.APPROVED.getStatus());
        when(applicationMapper.selectById(100L)).thenReturn(app);
        when(fileMapper.deleteByApplicationId(100L)).thenReturn(0);
        when(lineMapper.selectListByApplicationId(100L)).thenReturn(List.of(
                FinanceInvoiceApplicationLineDO.builder().id(1L).applicationId(100L).build()));

        cn.iocoder.yudao.module.finance.controller.admin.invoice.vo.FinanceInvoiceApplicationCompleteIssueReqVO req =
                new cn.iocoder.yudao.module.finance.controller.admin.invoice.vo.FinanceInvoiceApplicationCompleteIssueReqVO();
        req.setApplicationId(100L);
        req.setInvoiceNos(List.of("INV-A", "INV-B"));
        cn.iocoder.yudao.module.finance.controller.admin.invoice.vo.FinanceInvoiceApplicationCompleteIssueReqVO.FileItem f1 =
                new cn.iocoder.yudao.module.finance.controller.admin.invoice.vo.FinanceInvoiceApplicationCompleteIssueReqVO.FileItem();
        f1.setUrl("https://cdn.example/a.pdf");
        f1.setName("a.pdf");
        cn.iocoder.yudao.module.finance.controller.admin.invoice.vo.FinanceInvoiceApplicationCompleteIssueReqVO.FileItem f2 =
                new cn.iocoder.yudao.module.finance.controller.admin.invoice.vo.FinanceInvoiceApplicationCompleteIssueReqVO.FileItem();
        f2.setUrl("https://cdn.example/b.pdf");
        f2.setName("b.pdf");
        req.setFiles(List.of(f1, f2));

        service.completeIssue(req);

        verify(fileMapper).deleteByApplicationId(100L);
        verify(fileMapper, times(2)).insert(any(cn.iocoder.yudao.module.finance.dal.dataobject.invoice.FinanceInvoiceApplicationFileDO.class));
        ArgumentCaptor<FinanceInvoiceApplicationDO> appCaptor =
                ArgumentCaptor.forClass(FinanceInvoiceApplicationDO.class);
        verify(applicationMapper).updateById(appCaptor.capture());
        assertEquals(FinanceInvoiceIssueStatusEnum.FULL.getStatus(), appCaptor.getValue().getIssueStatus());
    }

    @Test
    void completeIssueShouldRejectWhenNotApproved() {
        when(applicationMapper.selectById(100L)).thenReturn(pendingApp(100L));
        cn.iocoder.yudao.module.finance.controller.admin.invoice.vo.FinanceInvoiceApplicationCompleteIssueReqVO req =
                new cn.iocoder.yudao.module.finance.controller.admin.invoice.vo.FinanceInvoiceApplicationCompleteIssueReqVO();
        req.setApplicationId(100L);
        cn.iocoder.yudao.module.finance.controller.admin.invoice.vo.FinanceInvoiceApplicationCompleteIssueReqVO.FileItem f =
                new cn.iocoder.yudao.module.finance.controller.admin.invoice.vo.FinanceInvoiceApplicationCompleteIssueReqVO.FileItem();
        f.setUrl("https://cdn.example/a.pdf");
        req.setFiles(List.of(f));
        ServiceException ex = assertThrows(ServiceException.class, () -> service.completeIssue(req));
        assertEquals(cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.INVOICE_APPLICATION_ISSUE_NOT_ALLOWED.getCode(),
                ex.getCode());
    }

    private static FinanceInvoiceApplicationDO pendingApp(Long id) {
        return FinanceInvoiceApplicationDO.builder()
                .id(id)
                .applicationNo("INV-1")
                .approvalStatus(FinanceInvoiceApprovalStatusEnum.PENDING.getStatus())
                .issueStatus(FinanceInvoiceIssueStatusEnum.NONE.getStatus())
                .totalAmount(new BigDecimal("50.00"))
                .confirmedClaimedAmount(ZERO)
                .pendingClaimedAmount(ZERO)
                .applicantUserId(200L)
                .voided(Boolean.FALSE)
                .build();
    }

    private static final BigDecimal ZERO = new BigDecimal("0.00");

}
