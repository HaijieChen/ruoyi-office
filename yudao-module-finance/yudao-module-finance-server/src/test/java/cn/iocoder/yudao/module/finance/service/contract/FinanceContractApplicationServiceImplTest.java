package cn.iocoder.yudao.module.finance.service.contract;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.yudao.module.finance.controller.admin.contract.vo.FinanceContractApplicationCreateAndStartReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.contract.vo.FinanceContractApplicationResubmitReqVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.contract.FinanceContractApplicationDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.customer.FinanceCustomerCompanyDO;
import cn.iocoder.yudao.module.finance.dal.mysql.contract.FinanceContractApplicationMapper;
import cn.iocoder.yudao.module.finance.dal.redis.no.FinanceContractApplicationNoRedisDAO;
import cn.iocoder.yudao.module.finance.enums.FinanceContractApprovalStatusEnum;
import cn.iocoder.yudao.module.finance.service.customer.FinanceCustomerCompanyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;

import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.*;
import static cn.iocoder.yudao.module.finance.service.contract.FinanceContractApplicationServiceImpl.PROCESS_KEY;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FinanceContractApplicationServiceImplTest {

    private FinanceContractApplicationMapper applicationMapper;
    private FinanceContractApplicationNoRedisDAO applicationNoRedisDAO;
    private BpmProcessInstanceApi processInstanceApi;
    private FinanceCustomerCompanyService customerCompanyService;
    private FinanceContractApplicationServiceImpl service;

    @BeforeEach
    void setUp() {
        applicationMapper = mock(FinanceContractApplicationMapper.class);
        applicationNoRedisDAO = mock(FinanceContractApplicationNoRedisDAO.class);
        processInstanceApi = mock(BpmProcessInstanceApi.class);
        customerCompanyService = mock(FinanceCustomerCompanyService.class);
        service = new FinanceContractApplicationServiceImpl(
                applicationMapper, applicationNoRedisDAO, processInstanceApi, customerCompanyService);

        when(applicationNoRedisDAO.generate(any(LocalDate.class))).thenReturn("CT-20260731-1");
        when(customerCompanyService.getEnabledCustomerCompany(50L)).thenReturn(
                FinanceCustomerCompanyDO.builder()
                        .id(50L)
                        .name("客商A")
                        .taxNo("91110000MA0000000X")
                        .status(FinanceCustomerCompanyDO.STATUS_ENABLE)
                        .build());
        doAnswer(invocation -> {
            FinanceContractApplicationDO app = invocation.getArgument(0);
            app.setId(100L);
            return 1;
        }).when(applicationMapper).insert(any(FinanceContractApplicationDO.class));
    }

    @Test
    void createAndStartShouldFailWhenAmountMissingWithoutAmountNa() {
        FinanceContractApplicationCreateAndStartReqVO req = validReq();
        req.setAmountNa(false);
        req.setContractAmount(null);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.createAndStart(req, 200L));
        assertEquals(CONTRACT_APPLICATION_AMOUNT_INVALID.getCode(), ex.getCode());
        verify(applicationMapper, never()).insert(any(FinanceContractApplicationDO.class));
        verifyNoInteractions(processInstanceApi);
    }

    @Test
    void createAndStartShouldFailWhenPurchaseMissingPreProcess() {
        FinanceContractApplicationCreateAndStartReqVO req = validReq();
        req.setFileType("采购合同");
        req.setPreProcessRef(null);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.createAndStart(req, 200L));
        assertEquals(CONTRACT_APPLICATION_PRE_PROCESS_REQUIRED.getCode(), ex.getCode());
        verifyNoInteractions(processInstanceApi);
    }

    @Test
    void createAndStartShouldOccupyAndWriteProcessInstanceId() {
        when(processInstanceApi.createProcessInstance(eq(200L), any(BpmProcessInstanceCreateReqDTO.class)))
                .thenReturn(CommonResult.success("proc-1"));

        Long id = service.createAndStart(validReq(), 200L);
        assertEquals(100L, id);

        ArgumentCaptor<BpmProcessInstanceCreateReqDTO> bpmCaptor =
                ArgumentCaptor.forClass(BpmProcessInstanceCreateReqDTO.class);
        verify(processInstanceApi).createProcessInstance(eq(200L), bpmCaptor.capture());
        BpmProcessInstanceCreateReqDTO bpmReq = bpmCaptor.getValue();
        assertEquals(PROCESS_KEY, bpmReq.getProcessDefinitionKey());
        assertEquals("100", bpmReq.getBusinessKey());
        assertEquals(Boolean.FALSE, bpmReq.getVariables().get("needMail"));

        ArgumentCaptor<FinanceContractApplicationDO> updateCaptor =
                ArgumentCaptor.forClass(FinanceContractApplicationDO.class);
        verify(applicationMapper).updateById(updateCaptor.capture());
        assertEquals("proc-1", updateCaptor.getValue().getProcessInstanceId());
    }

    @Test
    void createAndStartShouldAllowAmountNaWithoutAmount() {
        when(processInstanceApi.createProcessInstance(eq(200L), any(BpmProcessInstanceCreateReqDTO.class)))
                .thenReturn(CommonResult.success("proc-2"));

        FinanceContractApplicationCreateAndStartReqVO req = validReq();
        req.setAmountNa(true);
        req.setContractAmount(null);
        assertDoesNotThrow(() -> service.createAndStart(req, 200L));
        verify(applicationMapper).insert(argThat((FinanceContractApplicationDO app) ->
                Boolean.TRUE.equals(app.getAmountNa()) && app.getContractAmount() == null));
    }

    @Test
    void cancelShouldRejectWhenAtSealNode() {
        when(applicationMapper.selectById(100L)).thenReturn(FinanceContractApplicationDO.builder()
                .id(100L)
                .approvalStatus(FinanceContractApprovalStatusEnum.PENDING.getStatus())
                .currentNodeKey("seal")
                .voided(false)
                .build());

        ServiceException ex = assertThrows(ServiceException.class, () -> service.cancel(100L, 200L));
        assertEquals(CONTRACT_APPLICATION_CANCEL_NOT_ALLOWED.getCode(), ex.getCode());
    }

    @Test
    void resubmitShouldRejectWhenNotRejected() {
        when(applicationMapper.selectById(100L)).thenReturn(FinanceContractApplicationDO.builder()
                .id(100L)
                .approvalStatus(FinanceContractApprovalStatusEnum.PENDING.getStatus())
                .voided(false)
                .build());

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.resubmit(100L, toResubmit(validReq()), 200L));
        assertEquals(CONTRACT_APPLICATION_STATUS_INVALID.getCode(), ex.getCode());
    }

    @Test
    void onApprovalOutcomeShouldBeIdempotentForSameOutcome() {
        when(applicationMapper.selectById(100L)).thenReturn(FinanceContractApplicationDO.builder()
                .id(100L)
                .approvalStatus(FinanceContractApprovalStatusEnum.APPROVED.getStatus())
                .voided(false)
                .build());
        assertDoesNotThrow(() -> service.onApprovalOutcome(100L, "APPROVED"));
        verify(applicationMapper, never()).updateById(any(FinanceContractApplicationDO.class));
    }

    private static FinanceContractApplicationCreateAndStartReqVO validReq() {
        FinanceContractApplicationCreateAndStartReqVO req = new FinanceContractApplicationCreateAndStartReqVO();
        req.setCounterpartyCompanyId(50L);
        req.setAmountNa(false);
        req.setContractAmount(new BigDecimal("1000.00"));
        req.setSignCompany("A公司");
        req.setFileName("销售合同-测试");
        req.setFileType("销售合同");
        req.setRebateRatio("10%");
        req.setSettlementMethod("月结");
        req.setCopyCount(2);
        req.setSealTypes("[\"合同专用章\"]");
        req.setNeedMail(false);
        req.setDraftFileUrl("https://example.com/draft.pdf");
        return req;
    }

    private static FinanceContractApplicationResubmitReqVO toResubmit(FinanceContractApplicationCreateAndStartReqVO src) {
        FinanceContractApplicationResubmitReqVO req = new FinanceContractApplicationResubmitReqVO();
        req.setCounterpartyCompanyId(src.getCounterpartyCompanyId());
        req.setAmountNa(src.getAmountNa());
        req.setContractAmount(src.getContractAmount());
        req.setSignCompany(src.getSignCompany());
        req.setFileName(src.getFileName());
        req.setFileType(src.getFileType());
        req.setRebateRatio(src.getRebateRatio());
        req.setSettlementMethod(src.getSettlementMethod());
        req.setCopyCount(src.getCopyCount());
        req.setSealTypes(src.getSealTypes());
        req.setNeedMail(src.getNeedMail());
        req.setDraftFileUrl(src.getDraftFileUrl());
        return req;
    }
}
