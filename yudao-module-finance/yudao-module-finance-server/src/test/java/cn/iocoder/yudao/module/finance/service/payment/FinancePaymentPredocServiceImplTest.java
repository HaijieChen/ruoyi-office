package cn.iocoder.yudao.module.finance.service.payment;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import cn.iocoder.yudao.module.finance.controller.admin.payment.vo.FinancePurchaseInstanceRespVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.contract.FinanceContractApplicationDO;
import cn.iocoder.yudao.module.finance.dal.mysql.contract.FinanceContractApplicationMapper;
import cn.iocoder.yudao.module.finance.enums.FinanceContractApprovalStatusEnum;
import cn.iocoder.yudao.module.finance.enums.FinancePurchaseProcessConstants;
import cn.iocoder.yudao.module.finance.service.common.FinanceRelatedProcessAccess;
import org.flowable.engine.HistoryService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.history.HistoricProcessInstanceQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FinancePaymentPredocServiceImplTest {

    private HistoryService historyService;
    private HistoricProcessInstanceQuery query;
    private FinanceContractApplicationMapper contractMapper;
    private FinanceRelatedProcessAccess relatedProcessAccess;
    private FinancePaymentPredocServiceImpl service;

    @BeforeEach
    void setUp() {
        historyService = mock(HistoryService.class);
        query = mock(HistoricProcessInstanceQuery.class);
        contractMapper = mock(FinanceContractApplicationMapper.class);
        relatedProcessAccess = mock(FinanceRelatedProcessAccess.class);
        when(relatedProcessAccess.listSharedInstanceIds(any())).thenReturn(java.util.Set.of());
        when(relatedProcessAccess.canAccessRelated(eq(1L), anyString())).thenReturn(true);
        when(relatedProcessAccess.canAccessRelated(eq(1L), eq("pi-3"))).thenReturn(false);
        when(relatedProcessAccess.canAccessContract(eq(1L), any())).thenReturn(true);

        @SuppressWarnings("unchecked")
        ObjectProvider<HistoryService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(historyService);

        service = new FinancePaymentPredocServiceImpl(provider, contractMapper, relatedProcessAccess);

        when(historyService.createHistoricProcessInstanceQuery()).thenReturn(query);
        when(query.processDefinitionKey(anyString())).thenReturn(query);
        when(query.processInstanceId(anyString())).thenReturn(query);
        when(query.processInstanceIds(any())).thenReturn(query);
        when(query.startedBy(anyString())).thenReturn(query);
        when(query.variableValueEquals(anyString(), any())).thenReturn(query);
        when(query.includeProcessVariables()).thenReturn(query);
        when(query.orderByProcessInstanceEndTime()).thenReturn(query);
        when(query.desc()).thenReturn(query);
        // F5：History 查询默认挂租户过滤（TenantContext 为空时用 NO_TENANT_ID）
        when(query.processInstanceTenantId(anyString())).thenReturn(query);
    }

    @Test
    void listPurchaseReturnsApprovedWhitelistOnly() {
        HistoricProcessInstance hi = mockHistoric("pi-1", FinancePurchaseProcessConstants.OA_PURCHASE_APPLY,
                "1", BpmProcessInstanceStatusEnum.APPROVE.getStatus(), "采购-办公");
        when(query.list()).thenReturn(List.of(hi));

        List<FinancePurchaseInstanceRespVO> list = service.listSelectablePurchaseInstances(1L);
        assertEquals(1, list.size());
        assertEquals("pi-1", list.get(0).getProcessInstanceId());
        assertTrue(list.get(0).getSummary().contains("采购"));
        // F5：list 路径必须挂租户过滤（无租户上下文 → NO_TENANT_ID）
        verify(query, atLeastOnce()).processInstanceTenantId(anyString());
    }

    @Test
    void validatePurchaseAppliesTenantFilter() {
        HistoricProcessInstance hi = mockHistoric("pi-ok", FinancePurchaseProcessConstants.OA_PURCHASE_APPLY,
                "1", BpmProcessInstanceStatusEnum.APPROVE.getStatus(), "采购通过");
        when(query.singleResult()).thenReturn(hi);

        service.validateAndSummarizePurchaseRef("pi-ok", 1L);
        verify(query, atLeastOnce()).processInstanceTenantId(anyString());
    }

    @Test
    void validatePurchaseRejectsWrongKey() {
        HistoricProcessInstance hi = mockHistoric("pi-x", "oa_payment_apply",
                "1", BpmProcessInstanceStatusEnum.APPROVE.getStatus(), "付款");
        when(query.singleResult()).thenReturn(hi);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.validateAndSummarizePurchaseRef("pi-x", 1L));
        assertEquals(PAYMENT_PURCHASE_REF_INVALID.getCode(), ex.getCode());
    }

    @Test
    void validatePurchaseRejectsRunning() {
        HistoricProcessInstance hi = mockHistoric("pi-2", FinancePurchaseProcessConstants.OA_PURCHASE_APPLY,
                "1", BpmProcessInstanceStatusEnum.RUNNING.getStatus(), "采购中");
        when(query.singleResult()).thenReturn(hi);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.validateAndSummarizePurchaseRef("pi-2", 1L));
        assertEquals(PAYMENT_PURCHASE_REF_INVALID.getCode(), ex.getCode());
    }

    @Test
    void validatePurchaseRejectsOtherUser() {
        HistoricProcessInstance hi = mockHistoric("pi-3", FinancePurchaseProcessConstants.OA_PURCHASE_APPLY,
                "99", BpmProcessInstanceStatusEnum.APPROVE.getStatus(), "他人采购");
        when(query.singleResult()).thenReturn(hi);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.validateAndSummarizePurchaseRef("pi-3", 1L));
        assertEquals(PAYMENT_PURCHASE_REF_INVALID.getCode(), ex.getCode());
    }

    @Test
    void validatePurchaseAllowsSharedRecipient() {
        HistoricProcessInstance hi = mockHistoric("pi-share", FinancePurchaseProcessConstants.OA_PURCHASE_APPLY,
                "99", BpmProcessInstanceStatusEnum.APPROVE.getStatus(), "分享采购");
        when(query.singleResult()).thenReturn(hi);
        when(relatedProcessAccess.canAccessRelated(1L, "pi-share")).thenReturn(true);

        String summary = service.validateAndSummarizePurchaseRef("pi-share", 1L);
        assertTrue(summary.contains("分享采购"));
    }

    @Test
    void validatePurchaseOk() {
        HistoricProcessInstance hi = mockHistoric("pi-ok", FinancePurchaseProcessConstants.OA_PURCHASE_APPLY,
                "1", BpmProcessInstanceStatusEnum.APPROVE.getStatus(), "采购通过");
        when(query.singleResult()).thenReturn(hi);

        String summary = service.validateAndSummarizePurchaseRef("pi-ok", 1L);
        assertNotNull(summary);
        assertTrue(summary.contains("采购通过"));
    }

    @Test
    void validatePurchaseBlankFails() {
        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.validateAndSummarizePurchaseRef("  ", 1L));
        assertEquals(PAYMENT_PURCHASE_REF_INVALID.getCode(), ex.getCode());
    }

    @Test
    void listLeaseFiltersTypeAndApproved() {
        FinanceContractApplicationDO lease = FinanceContractApplicationDO.builder()
                .id(10L)
                .approvalStatus(FinanceContractApprovalStatusEnum.APPROVED.getStatus())
                .fileType("租赁合同")
                .applicantUserId(1L)
                .voided(false)
                .build();
        when(contractMapper.selectList(any())).thenReturn(List.of(lease));

        List<FinanceContractApplicationDO> list = service.listSelectableLeaseContracts(1L);
        assertEquals(1, list.size());
        assertEquals(10L, list.get(0).getId());
        verify(contractMapper).selectList(any());
    }

    @Test
    void validateLeaseRejectsSalesContract() {
        when(contractMapper.selectById(20L)).thenReturn(FinanceContractApplicationDO.builder()
                .id(20L)
                .approvalStatus(FinanceContractApprovalStatusEnum.APPROVED.getStatus())
                .fileType("销售合同")
                .applicantUserId(1L)
                .voided(false)
                .build());

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.validateLeaseContractRef(20L, 1L));
        assertEquals(PAYMENT_LEASE_REF_INVALID.getCode(), ex.getCode());
    }

    @Test
    void validateLeaseRejectsPending() {
        when(contractMapper.selectById(21L)).thenReturn(FinanceContractApplicationDO.builder()
                .id(21L)
                .approvalStatus(FinanceContractApprovalStatusEnum.PENDING.getStatus())
                .fileType("租赁合同")
                .applicantUserId(1L)
                .build());

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.validateLeaseContractRef(21L, 1L));
        assertEquals(PAYMENT_LEASE_REF_INVALID.getCode(), ex.getCode());
    }

    @Test
    void validateLeaseOk() {
        when(contractMapper.selectById(22L)).thenReturn(FinanceContractApplicationDO.builder()
                .id(22L)
                .approvalStatus(FinanceContractApprovalStatusEnum.APPROVED.getStatus())
                .fileType("租赁合同")
                .applicantUserId(1L)
                .voided(false)
                .settlementMethod("月付")
                .build());

        FinanceContractApplicationDO app = service.validateLeaseContractRef(22L, 1L);
        assertEquals(22L, app.getId());
        assertEquals("月付", app.getSettlementMethod());
    }

    private HistoricProcessInstance mockHistoric(String id, String key, String startUserId,
                                                 Integer status, String name) {
        HistoricProcessInstance hi = mock(HistoricProcessInstance.class);
        when(hi.getId()).thenReturn(id);
        when(hi.getProcessDefinitionKey()).thenReturn(key);
        when(hi.getStartUserId()).thenReturn(startUserId);
        when(hi.getName()).thenReturn(name);
        when(hi.getStartTime()).thenReturn(new Date());
        when(hi.getEndTime()).thenReturn(new Date());
        Map<String, Object> vars = new HashMap<>();
        vars.put(FinancePaymentPredocServiceImpl.PROCESS_STATUS_VAR, status);
        when(hi.getProcessVariables()).thenReturn(vars);
        return hi;
    }

}
