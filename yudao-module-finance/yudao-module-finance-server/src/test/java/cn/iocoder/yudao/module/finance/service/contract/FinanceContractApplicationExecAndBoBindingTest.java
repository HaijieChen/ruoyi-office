package cn.iocoder.yudao.module.finance.service.contract;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.finance.controller.admin.business.vo.FinanceBusinessOrderSaveReqVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.business.FinanceBusinessOrderDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.contract.FinanceContractApplicationDO;
import cn.iocoder.yudao.module.finance.dal.mysql.business.FinanceBusinessOrderMapper;
import cn.iocoder.yudao.module.finance.dal.mysql.contract.FinanceContractApplicationMapper;
import cn.iocoder.yudao.module.finance.dal.redis.no.FinanceBusinessOrderNoRedisDAO;
import cn.iocoder.yudao.module.finance.enums.FinanceContractApprovalStatusEnum;
import cn.iocoder.yudao.module.finance.service.business.FinanceBusinessOrderServiceImpl;
import cn.iocoder.yudao.module.finance.service.common.FinanceEntityCompanyResolver;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.finance.framework.rpc.FinanceBpmProcessInstanceApi;
import cn.iocoder.yudao.module.finance.dal.redis.no.FinanceContractApplicationNoRedisDAO;
import cn.iocoder.yudao.module.finance.service.customer.FinanceCustomerCompanyService;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import org.flowable.engine.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * CS-T4/T5 + CS-F：用印/邮寄门槛 + BO 挂靠 + 占用 CAS。
 */
class FinanceContractApplicationExecAndBoBindingTest {

    private static final Long USER = 100L;
    private static final Long OTHER = 200L;
    private static final Long COMPANY_DEPT_ID = 10L;

    private FinanceContractApplicationMapper contractMapper;
    private FinanceContractApplicationServiceImpl contractService;
    private FinanceBusinessOrderMapper boMapper;
    private FinanceBusinessOrderServiceImpl boService;

    @BeforeEach
    void setUp() {
        contractMapper = mock(FinanceContractApplicationMapper.class);
        FinanceContractApplicationNoRedisDAO noDao = mock(FinanceContractApplicationNoRedisDAO.class);
        FinanceBpmProcessInstanceApi bpm = mock(FinanceBpmProcessInstanceApi.class);
        FinanceCustomerCompanyService customer = mock(FinanceCustomerCompanyService.class);
        ObjectProvider<TaskService> taskProvider = mock(ObjectProvider.class);
        DictDataApi dictDataApi = mock(DictDataApi.class);
        when(taskProvider.getIfAvailable()).thenReturn(null);
        FinanceEntityCompanyResolver entityResolver = mock(FinanceEntityCompanyResolver.class);
        when(entityResolver.requireByDeptId(anyLong()))
                .thenReturn(new FinanceEntityCompanyResolver.ResolvedCompany(COMPANY_DEPT_ID, "示例主体公司", "CNY"));
        @SuppressWarnings("unchecked")
        ObjectProvider<FinanceApprovedSalesBusinessOrderService> autoBo =
                mock(ObjectProvider.class);
        when(autoBo.getIfAvailable()).thenReturn(null);
        contractService = new FinanceContractApplicationServiceImpl(
                contractMapper, noDao, bpm, customer, entityResolver, taskProvider, dictDataApi, autoBo);

        boMapper = mock(FinanceBusinessOrderMapper.class);
        FinanceBusinessOrderNoRedisDAO boNo = mock(FinanceBusinessOrderNoRedisDAO.class);
        FinanceEntityCompanyResolver companyResolver = mock(FinanceEntityCompanyResolver.class);
        when(boNo.generate(any(LocalDate.class))).thenReturn("BO-1");
        when(companyResolver.requireByDeptId(anyLong())).thenReturn(
                new FinanceEntityCompanyResolver.ResolvedCompany(COMPANY_DEPT_ID, "示例主体公司"));
        boService = new FinanceBusinessOrderServiceImpl(boMapper, boNo, contractMapper, companyResolver);
    }

    @Test
    void recordSealShouldRejectBlankFile() {
        when(contractMapper.selectById(1L)).thenReturn(pending(1L, USER, "seal"));
        ServiceException ex = assertThrows(ServiceException.class,
                () -> contractService.recordSeal(1L, "task-1", "  ", USER));
        assertEquals(CONTRACT_APPLICATION_SEAL_FILE_REQUIRED.getCode(), ex.getCode());
    }

    @Test
    void recordSealShouldRejectWhenTaskServiceUnavailable() {
        when(contractMapper.selectById(1L)).thenReturn(pending(1L, USER, "seal"));
        ServiceException ex = assertThrows(ServiceException.class,
                () -> contractService.recordSeal(1L, "task-1", "https://x/seal.pdf", USER));
        assertEquals(CONTRACT_APPLICATION_TASK_INVALID.getCode(), ex.getCode());
    }

    @Test
    void recordMailShouldRejectWhenNeedMailFalse() {
        FinanceContractApplicationDO app = pending(1L, USER, "mail");
        app.setNeedMail(false);
        when(contractMapper.selectById(1L)).thenReturn(app);
        ServiceException ex = assertThrows(ServiceException.class,
                () -> contractService.recordMail(1L, "task-1", "SF123", USER));
        assertEquals(CONTRACT_APPLICATION_EXEC_NOT_ALLOWED.getCode(), ex.getCode());
    }

    @Test
    void recordMailShouldRequireTrackingNo() {
        FinanceContractApplicationDO app = pending(1L, USER, "mail");
        app.setNeedMail(true);
        when(contractMapper.selectById(1L)).thenReturn(app);
        ServiceException ex = assertThrows(ServiceException.class,
                () -> contractService.recordMail(1L, "task-1", "", USER));
        assertEquals(CONTRACT_APPLICATION_MAIL_TRACKING_REQUIRED.getCode(), ex.getCode());
    }

    @Test
    void assertExecutionEvidenceShouldRejectEmptySealOnTaskSeal() {
        when(contractMapper.selectById(1L)).thenReturn(pending(1L, USER, "seal"));
        ServiceException ex = assertThrows(ServiceException.class,
                () -> contractService.assertExecutionEvidenceForComplete(1L, "taskSeal"));
        assertEquals(CONTRACT_APPLICATION_SEAL_FILE_REQUIRED.getCode(), ex.getCode());
    }

    @Test
    void listSelectableForBoShouldQueryApprovedSelf() {
        when(contractMapper.selectList(any())).thenReturn(List.of(approved(5L, USER)));
        List<FinanceContractApplicationDO> list = contractService.listSelectableForBo(USER);
        assertEquals(1, list.size());
        verify(contractMapper).selectList(any());
    }

    @Test
    void createBoShouldRequireContract() {
        FinanceBusinessOrderSaveReqVO req = validBo();
        req.setContractApplicationId(null);
        ServiceException ex = assertThrows(ServiceException.class,
                () -> boService.createBusinessOrder(req, USER));
        assertEquals(BUSINESS_ORDER_CONTRACT_REQUIRED.getCode(), ex.getCode());
        verify(boMapper, never()).insert(any(FinanceBusinessOrderDO.class));
    }

    @Test
    void createBoShouldRejectOthersApprovedContract() {
        when(contractMapper.selectById(5L)).thenReturn(approved(5L, OTHER));
        FinanceBusinessOrderSaveReqVO req = validBo();
        req.setContractApplicationId(5L);
        ServiceException ex = assertThrows(ServiceException.class,
                () -> boService.createBusinessOrder(req, USER));
        assertEquals(BUSINESS_ORDER_CONTRACT_INVALID.getCode(), ex.getCode());
    }

    @Test
    void createBoShouldRejectPendingContract() {
        when(contractMapper.selectById(5L)).thenReturn(pending(5L, USER, "legal"));
        FinanceBusinessOrderSaveReqVO req = validBo();
        req.setContractApplicationId(5L);
        ServiceException ex = assertThrows(ServiceException.class,
                () -> boService.createBusinessOrder(req, USER));
        assertEquals(BUSINESS_ORDER_CONTRACT_INVALID.getCode(), ex.getCode());
    }

    @Test
    void updateBoHistoricalEmptyWithoutContractShouldSucceed() {
        when(boMapper.selectById(1L)).thenReturn(FinanceBusinessOrderDO.builder()
                .id(1L)
                .importerId(USER)
                .contractApplicationId(null)
                .productName("历史产品")
                .confirmedClaimedAmount(BigDecimal.ZERO)
                .orderNo("BO-OLD")
                .importDate(LocalDate.now())
                .build());
        FinanceBusinessOrderSaveReqVO req = validBo();
        req.setId(1L);
        req.setContractApplicationId(null);
        req.setProductName(null);
        req.setRemark("仅改备注");
        assertDoesNotThrow(() -> boService.updateBusinessOrder(req));
        // #3：不写产品字段（null 跳过）；绝不把请求 productName 写入
        verify(boMapper).updateById(argThat((FinanceBusinessOrderDO u) ->
                u.getProductName() == null && u.getProductTypeSnapshot() == null
                        && u.getContractApplicationId() == null));
    }

    @Test
    void updateBoHistoricalEmptyShouldIgnoreClientProductName() {
        when(boMapper.selectById(1L)).thenReturn(FinanceBusinessOrderDO.builder()
                .id(1L)
                .importerId(USER)
                .contractApplicationId(null)
                .productName("库内产品")
                .confirmedClaimedAmount(BigDecimal.ZERO)
                .orderNo("BO-OLD")
                .importDate(LocalDate.now())
                .build());
        FinanceBusinessOrderSaveReqVO req = validBo();
        req.setId(1L);
        req.setContractApplicationId(null);
        req.setProductName("客户端伪造产品");
        assertDoesNotThrow(() -> boService.updateBusinessOrder(req));
        verify(boMapper).updateById(argThat((FinanceBusinessOrderDO u) ->
                u.getProductName() == null && u.getProductTypeSnapshot() == null));
        verify(boMapper, never()).casChangeContractApplicationId(anyLong(), anyLong(), anyLong(), anyString());
    }

    @Test
    void updateBoShouldForbidClearingMappedContract() {
        when(boMapper.selectById(1L)).thenReturn(FinanceBusinessOrderDO.builder()
                .id(1L)
                .importerId(USER)
                .contractApplicationId(5L)
                .confirmedClaimedAmount(BigDecimal.ZERO)
                .invoicedOccupiedAmount(BigDecimal.ZERO)
                .orderNo("BO-1")
                .importDate(LocalDate.now())
                .build());
        FinanceBusinessOrderSaveReqVO req = validBo();
        req.setId(1L);
        req.setContractApplicationId(null);
        ServiceException ex = assertThrows(ServiceException.class,
                () -> boService.updateBusinessOrder(req));
        assertEquals(BUSINESS_ORDER_CONTRACT_CLEAR_FORBIDDEN.getCode(), ex.getCode());
    }

    @Test
    void updateBoShouldForbidChangeWhenInvoicedOccupied() {
        when(boMapper.selectById(1L)).thenReturn(FinanceBusinessOrderDO.builder()
                .id(1L)
                .importerId(USER)
                .contractApplicationId(5L)
                .confirmedClaimedAmount(BigDecimal.ZERO)
                .invoicedOccupiedAmount(new BigDecimal("10.00"))
                .orderNo("BO-1")
                .importDate(LocalDate.now())
                .build());
        when(contractMapper.selectById(6L)).thenReturn(approved(6L, USER));
        FinanceBusinessOrderSaveReqVO req = validBo();
        req.setId(1L);
        req.setContractApplicationId(6L);
        ServiceException ex = assertThrows(ServiceException.class,
                () -> boService.updateBusinessOrder(req));
        assertEquals(BUSINESS_ORDER_CONTRACT_CHANGE_FORBIDDEN.getCode(), ex.getCode());
    }

    @Test
    void updateBoShouldUseCasWhenChangingContractWithoutBlindWrite() {
        when(boMapper.selectById(1L)).thenReturn(FinanceBusinessOrderDO.builder()
                .id(1L)
                .importerId(USER)
                .contractApplicationId(5L)
                .confirmedClaimedAmount(BigDecimal.ZERO)
                .invoicedOccupiedAmount(BigDecimal.ZERO)
                .orderNo("BO-1")
                .importDate(LocalDate.now())
                .build());
        when(contractMapper.selectById(6L)).thenReturn(approved(6L, USER));
        when(boMapper.countActiveInvoiceLinesByBusinessOrderId(1L)).thenReturn(0L);
        when(boMapper.casChangeContractApplicationId(1L, 5L, 6L, "软件")).thenReturn(1);
        FinanceBusinessOrderSaveReqVO req = validBo();
        req.setId(1L);
        req.setContractApplicationId(6L);
        req.setProductName("客户端篡改");
        assertDoesNotThrow(() -> boService.updateBusinessOrder(req));
        // EXP-70：CAS 原子刷新合同 + 产品快照
        verify(boMapper).casChangeContractApplicationId(1L, 5L, 6L, "软件");
        // #2：CAS 写产品后，普通 updateById 禁止再写权威产品字段
        verify(boMapper).updateById(argThat((FinanceBusinessOrderDO u) ->
                u.getContractApplicationId() == null
                        && u.getProductTypeSnapshot() == null
                        && u.getProductName() == null));
    }

    @Test
    void updateBoSameContractShouldNotRewriteExistingSnapshot() {
        when(boMapper.selectById(1L)).thenReturn(FinanceBusinessOrderDO.builder()
                .id(1L)
                .importerId(USER)
                .contractApplicationId(5L)
                .productTypeSnapshot("软件")
                .productName("软件")
                .confirmedClaimedAmount(BigDecimal.ZERO)
                .invoicedOccupiedAmount(BigDecimal.ZERO)
                .orderNo("BO-1")
                .importDate(LocalDate.now())
                .build());
        // 合同当前值被管理员改成「硬件」——同合同编辑不得回写快照（#6）
        when(contractMapper.selectById(5L)).thenReturn(FinanceContractApplicationDO.builder()
                .id(5L)
                .applicationNo("CT-5")
                .productType("硬件")
                .applicantUserId(USER)
                .approvalStatus(FinanceContractApprovalStatusEnum.APPROVED.getStatus())
                .voided(false)
                .build());
        FinanceBusinessOrderSaveReqVO req = validBo();
        req.setId(1L);
        req.setContractApplicationId(5L);
        req.setContactPerson("李四");
        assertDoesNotThrow(() -> boService.updateBusinessOrder(req));
        verify(boMapper, never()).casChangeContractApplicationId(anyLong(), anyLong(), anyLong(), anyString());
        verify(boMapper, never()).casFillProductSnapshotWhenEmpty(anyLong(), anyLong(), anyString());
        verify(boMapper).updateById(argThat((FinanceBusinessOrderDO u) ->
                "李四".equals(u.getContactPerson())
                        && u.getProductTypeSnapshot() == null
                        && u.getProductName() == null));
    }

    @Test
    void updateBoSameContractEmptySnapshotShouldFillViaSelectableContract() {
        // 复审 #6 正例：同合同、快照/name 皆空、合同 APPROVED 可补齐
        when(boMapper.selectById(1L)).thenReturn(FinanceBusinessOrderDO.builder()
                .id(1L)
                .importerId(USER)
                .contractApplicationId(5L)
                .productTypeSnapshot(null)
                .productName(null)
                .confirmedClaimedAmount(BigDecimal.ZERO)
                .invoicedOccupiedAmount(BigDecimal.ZERO)
                .orderNo("BO-1")
                .importDate(LocalDate.now())
                .build());
        when(contractMapper.selectById(5L)).thenReturn(approved(5L, USER));
        when(boMapper.casFillProductSnapshotWhenEmpty(1L, 5L, "软件")).thenReturn(1);
        FinanceBusinessOrderSaveReqVO req = validBo();
        req.setId(1L);
        req.setContractApplicationId(5L);
        assertDoesNotThrow(() -> boService.updateBusinessOrder(req));
        verify(boMapper).casFillProductSnapshotWhenEmpty(1L, 5L, "软件");
        verify(boMapper).updateById(argThat((FinanceBusinessOrderDO u) ->
                u.getProductTypeSnapshot() == null && u.getProductName() == null));
    }

    @Test
    void updateBoSameContractEmptySnapshotShouldRejectRejectedContract() {
        // 复审 #6 负例：rejected 合同不得升级为权威快照
        when(boMapper.selectById(1L)).thenReturn(emptySnapshotBo(5L));
        when(contractMapper.selectById(5L)).thenReturn(FinanceContractApplicationDO.builder()
                .id(5L)
                .applicationNo("CT-5")
                .productType("软件")
                .applicantUserId(USER)
                .approvalStatus(FinanceContractApprovalStatusEnum.REJECTED.getStatus())
                .voided(false)
                .build());
        FinanceBusinessOrderSaveReqVO req = validBo();
        req.setId(1L);
        req.setContractApplicationId(5L);
        ServiceException ex = assertThrows(ServiceException.class,
                () -> boService.updateBusinessOrder(req));
        assertEquals(BUSINESS_ORDER_CONTRACT_INVALID.getCode(), ex.getCode());
        verify(boMapper, never()).casFillProductSnapshotWhenEmpty(anyLong(), anyLong(), anyString());
    }

    @Test
    void updateBoSameContractEmptySnapshotShouldRejectVoidedContract() {
        // 复审 #6 负例：已作废合同
        when(boMapper.selectById(1L)).thenReturn(emptySnapshotBo(5L));
        when(contractMapper.selectById(5L)).thenReturn(FinanceContractApplicationDO.builder()
                .id(5L)
                .applicationNo("CT-5")
                .productType("软件")
                .applicantUserId(USER)
                .approvalStatus(FinanceContractApprovalStatusEnum.APPROVED.getStatus())
                .voided(true)
                .build());
        FinanceBusinessOrderSaveReqVO req = validBo();
        req.setId(1L);
        req.setContractApplicationId(5L);
        ServiceException ex = assertThrows(ServiceException.class,
                () -> boService.updateBusinessOrder(req));
        assertEquals(BUSINESS_ORDER_CONTRACT_INVALID.getCode(), ex.getCode());
        verify(boMapper, never()).casFillProductSnapshotWhenEmpty(anyLong(), anyLong(), anyString());
    }

    @Test
    void updateBoSameContractEmptySnapshotShouldRejectOthersContract() {
        // 复审 #6 负例：他人合同（applicant ≠ importer）
        when(boMapper.selectById(1L)).thenReturn(emptySnapshotBo(5L));
        when(contractMapper.selectById(5L)).thenReturn(approved(5L, OTHER));
        FinanceBusinessOrderSaveReqVO req = validBo();
        req.setId(1L);
        req.setContractApplicationId(5L);
        ServiceException ex = assertThrows(ServiceException.class,
                () -> boService.updateBusinessOrder(req));
        assertEquals(BUSINESS_ORDER_CONTRACT_INVALID.getCode(), ex.getCode());
        verify(boMapper, never()).casFillProductSnapshotWhenEmpty(anyLong(), anyLong(), anyString());
    }

    private static FinanceBusinessOrderDO emptySnapshotBo(Long contractId) {
        return FinanceBusinessOrderDO.builder()
                .id(1L)
                .importerId(USER)
                .contractApplicationId(contractId)
                .productTypeSnapshot(null)
                .productName(null)
                .confirmedClaimedAmount(BigDecimal.ZERO)
                .invoicedOccupiedAmount(BigDecimal.ZERO)
                .orderNo("BO-1")
                .importDate(LocalDate.now())
                .build();
    }

    @Test
    void updateBoShouldCasWhenFirstMappingFromEmpty() {
        when(boMapper.selectById(1L)).thenReturn(FinanceBusinessOrderDO.builder()
                .id(1L)
                .importerId(USER)
                .contractApplicationId(null)
                .productName("旧产品")
                .confirmedClaimedAmount(BigDecimal.ZERO)
                .invoicedOccupiedAmount(BigDecimal.ZERO)
                .orderNo("BO-OLD")
                .importDate(LocalDate.now())
                .build());
        when(contractMapper.selectById(6L)).thenReturn(approved(6L, USER));
        when(boMapper.countActiveInvoiceLinesByBusinessOrderId(1L)).thenReturn(0L);
        when(boMapper.casSetContractApplicationIdWhenEmpty(1L, 6L, "软件")).thenReturn(1);
        FinanceBusinessOrderSaveReqVO req = validBo();
        req.setId(1L);
        req.setContractApplicationId(6L);
        assertDoesNotThrow(() -> boService.updateBusinessOrder(req));
        verify(boMapper).countActiveInvoiceLinesByBusinessOrderId(1L);
        verify(boMapper).casSetContractApplicationIdWhenEmpty(1L, 6L, "软件");
        verify(boMapper).updateById(argThat((FinanceBusinessOrderDO u) ->
                u.getContractApplicationId() == null
                        && u.getProductTypeSnapshot() == null
                        && u.getProductName() == null));
    }

    @Test
    void updateBoFirstMapShouldBlockWhenActiveInvoiceExistsEvenIfOccupyZero() {
        // P2 #9
        when(boMapper.selectById(1L)).thenReturn(FinanceBusinessOrderDO.builder()
                .id(1L)
                .importerId(USER)
                .contractApplicationId(null)
                .productName("旧")
                .confirmedClaimedAmount(BigDecimal.ZERO)
                .invoicedOccupiedAmount(BigDecimal.ZERO)
                .orderNo("BO-OLD")
                .importDate(LocalDate.now())
                .build());
        when(contractMapper.selectById(6L)).thenReturn(approved(6L, USER));
        when(boMapper.countActiveInvoiceLinesByBusinessOrderId(1L)).thenReturn(2L);
        FinanceBusinessOrderSaveReqVO req = validBo();
        req.setId(1L);
        req.setContractApplicationId(6L);
        ServiceException ex = assertThrows(ServiceException.class,
                () -> boService.updateBusinessOrder(req));
        assertEquals(BUSINESS_ORDER_ACTIVE_INVOICE_BLOCKS_CONTRACT_CHANGE.getCode(), ex.getCode());
        verify(boMapper, never()).casSetContractApplicationIdWhenEmpty(anyLong(), anyLong(), anyString());
    }

    @Test
    void updateBoShouldFailWhenCasLosesRace() {
        when(boMapper.selectById(1L)).thenReturn(FinanceBusinessOrderDO.builder()
                .id(1L)
                .importerId(USER)
                .contractApplicationId(5L)
                .confirmedClaimedAmount(BigDecimal.ZERO)
                .invoicedOccupiedAmount(BigDecimal.ZERO)
                .orderNo("BO-1")
                .importDate(LocalDate.now())
                .build());
        when(contractMapper.selectById(6L)).thenReturn(approved(6L, USER));
        when(boMapper.countActiveInvoiceLinesByBusinessOrderId(1L)).thenReturn(0L);
        when(boMapper.casChangeContractApplicationId(1L, 5L, 6L, "软件")).thenReturn(0);
        FinanceBusinessOrderSaveReqVO req = validBo();
        req.setId(1L);
        req.setContractApplicationId(6L);
        ServiceException ex = assertThrows(ServiceException.class,
                () -> boService.updateBusinessOrder(req));
        assertEquals(BUSINESS_ORDER_CONTRACT_CHANGE_FORBIDDEN.getCode(), ex.getCode());
    }

    @Test
    void updateBoShouldBlockChangeWhenActiveInvoiceExists() {
        when(boMapper.selectById(1L)).thenReturn(FinanceBusinessOrderDO.builder()
                .id(1L)
                .importerId(USER)
                .contractApplicationId(5L)
                .confirmedClaimedAmount(BigDecimal.ZERO)
                .invoicedOccupiedAmount(BigDecimal.ZERO)
                .orderNo("BO-1")
                .importDate(LocalDate.now())
                .build());
        when(contractMapper.selectById(6L)).thenReturn(approved(6L, USER));
        when(boMapper.countActiveInvoiceLinesByBusinessOrderId(1L)).thenReturn(1L);
        FinanceBusinessOrderSaveReqVO req = validBo();
        req.setId(1L);
        req.setContractApplicationId(6L);
        ServiceException ex = assertThrows(ServiceException.class,
                () -> boService.updateBusinessOrder(req));
        assertEquals(BUSINESS_ORDER_ACTIVE_INVOICE_BLOCKS_CONTRACT_CHANGE.getCode(), ex.getCode());
        verify(boMapper, never()).casChangeContractApplicationId(anyLong(), anyLong(), anyLong(), anyString());
    }

    private static FinanceContractApplicationDO pending(Long id, Long applicant, String node) {
        return FinanceContractApplicationDO.builder()
                .id(id)
                .applicantUserId(applicant)
                .approvalStatus(FinanceContractApprovalStatusEnum.PENDING.getStatus())
                .currentNodeKey(node)
                .voided(false)
                .build();
    }

    private static FinanceContractApplicationDO approved(Long id, Long applicant) {
        return FinanceContractApplicationDO.builder()
                .id(id)
                .applicationNo("CT-" + id)
                .productType("软件")
                .applicantUserId(applicant)
                .approvalStatus(FinanceContractApprovalStatusEnum.APPROVED.getStatus())
                .voided(false)
                .build();
    }

    private static FinanceBusinessOrderSaveReqVO validBo() {
        FinanceBusinessOrderSaveReqVO req = new FinanceBusinessOrderSaveReqVO();
        req.setEntityCompanyDeptId(COMPANY_DEPT_ID);
        req.setOrderDate(LocalDate.of(2026, 7, 1));
        req.setProductName("产品A");
        req.setContactPerson("张三");
        req.setExecutionStartDate(LocalDate.of(2026, 7, 5));
        req.setExecutionEndDate(LocalDate.of(2026, 7, 31));
        req.setSignedExecutionAmount(new BigDecimal("1000.00"));
        req.setDiscountRate(BigDecimal.ZERO);
        req.setCurrency("CNY");
        return req;
    }
}
