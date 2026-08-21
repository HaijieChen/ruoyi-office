package cn.iocoder.yudao.module.finance.service.payment;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import cn.iocoder.yudao.module.finance.controller.admin.payment.vo.FinancePurchaseInstanceRespVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.contract.FinanceContractApplicationDO;
import cn.iocoder.yudao.module.finance.dal.mysql.contract.FinanceContractApplicationMapper;
import cn.iocoder.yudao.module.finance.enums.FinanceContractApprovalStatusEnum;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.finance.enums.FinancePurchaseProcessConstants;
import cn.iocoder.yudao.module.finance.service.common.FinanceRelatedProcessAccess;
import org.flowable.engine.HistoryService;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.history.HistoricProcessInstanceQuery;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.*;

@Service
@Validated
public class FinancePaymentPredocServiceImpl implements FinancePaymentPredocService {

    /** 与 BPM 引擎约定一致：BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_STATUS */
    static final String PROCESS_STATUS_VAR = "PROCESS_STATUS";

    public static final String LEASE_FILE_TYPE = "租赁合同";

    private final ObjectProvider<HistoryService> historyServiceProvider;
    private final FinanceContractApplicationMapper contractApplicationMapper;
    private final FinanceRelatedProcessAccess relatedProcessAccess;

    public FinancePaymentPredocServiceImpl(ObjectProvider<HistoryService> historyServiceProvider,
                                           FinanceContractApplicationMapper contractApplicationMapper,
                                           FinanceRelatedProcessAccess relatedProcessAccess) {
        this.historyServiceProvider = historyServiceProvider;
        this.contractApplicationMapper = contractApplicationMapper;
        this.relatedProcessAccess = relatedProcessAccess;
    }

    @Override
    public List<FinancePurchaseInstanceRespVO> listSelectablePurchaseInstances(Long userId) {
        HistoryService historyService = requireHistoryService();
        List<FinancePurchaseInstanceRespVO> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Set<String> sharedIds = relatedProcessAccess.listSharedInstanceIds(userId);
        for (String key : FinancePurchaseProcessConstants.PURCHASE_PROCESS_KEYS) {
            HistoricProcessInstanceQuery own = historyService.createHistoricProcessInstanceQuery()
                    .processDefinitionKey(key)
                    .startedBy(String.valueOf(userId))
                    .variableValueEquals(PROCESS_STATUS_VAR, BpmProcessInstanceStatusEnum.APPROVE.getStatus())
                    .includeProcessVariables()
                    .orderByProcessInstanceEndTime()
                    .desc();
            applyTenantFilter(own);
            addPurchase(result, seen, own.list());
            if (!sharedIds.isEmpty()) {
                HistoricProcessInstanceQuery shared = historyService.createHistoricProcessInstanceQuery()
                        .processDefinitionKey(key)
                        .processInstanceIds(sharedIds)
                        .variableValueEquals(PROCESS_STATUS_VAR, BpmProcessInstanceStatusEnum.APPROVE.getStatus())
                        .includeProcessVariables()
                        .orderByProcessInstanceEndTime()
                        .desc();
                applyTenantFilter(shared);
                addPurchase(result, seen, shared.list());
            }
        }
        return result;
    }

    @Override
    public String validateAndSummarizePurchaseRef(String processInstanceId, Long userId) {
        if (StrUtil.isBlank(processInstanceId)) {
            throw exception(PAYMENT_PURCHASE_REF_INVALID);
        }
        HistoryService historyService = requireHistoryService();
        HistoricProcessInstanceQuery query = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId.trim())
                .includeProcessVariables();
        applyTenantFilter(query);
        HistoricProcessInstance hi = query.singleResult();
        if (hi == null) {
            throw exception(PAYMENT_PURCHASE_REF_INVALID);
        }
        if (!FinancePurchaseProcessConstants.isPurchaseProcessKey(hi.getProcessDefinitionKey())) {
            throw exception(PAYMENT_PURCHASE_REF_INVALID);
        }
        if (!relatedProcessAccess.canAccessRelated(userId, processInstanceId.trim())) {
            throw exception(PAYMENT_PURCHASE_REF_INVALID);
        }
        Object status = hi.getProcessVariables() != null
                ? hi.getProcessVariables().get(PROCESS_STATUS_VAR)
                : null;
        if (!Objects.equals(BpmProcessInstanceStatusEnum.APPROVE.getStatus(), status)
                && !Objects.equals(BpmProcessInstanceStatusEnum.APPROVE.getStatus(), toInteger(status))) {
            throw exception(PAYMENT_PURCHASE_REF_INVALID);
        }
        return buildSummary(hi);
    }

    @Override
    public List<FinanceContractApplicationDO> listSelectableLeaseContracts(Long userId) {
        Set<String> sharedIds = relatedProcessAccess.listSharedInstanceIds(userId);
        return contractApplicationMapper.selectList(new LambdaQueryWrapperX<FinanceContractApplicationDO>()
                .eq(FinanceContractApplicationDO::getApprovalStatus,
                        FinanceContractApprovalStatusEnum.APPROVED.getStatus())
                .eq(FinanceContractApplicationDO::getFileType, LEASE_FILE_TYPE)
                .and(w -> {
                    w.eq(FinanceContractApplicationDO::getApplicantUserId, userId);
                    if (!sharedIds.isEmpty()) {
                        w.or().in(FinanceContractApplicationDO::getProcessInstanceId, sharedIds);
                    }
                })
                .and(w -> w.eq(FinanceContractApplicationDO::getVoided, Boolean.FALSE)
                        .or()
                        .isNull(FinanceContractApplicationDO::getVoided))
                .orderByDesc(FinanceContractApplicationDO::getId));
    }

    @Override
    public FinanceContractApplicationDO validateLeaseContractRef(Long contractApplicationId, Long userId) {
        if (contractApplicationId == null) {
            throw exception(PAYMENT_LEASE_REF_INVALID);
        }
        FinanceContractApplicationDO app = contractApplicationMapper.selectById(contractApplicationId);
        if (app == null) {
            throw exception(PAYMENT_LEASE_REF_INVALID);
        }
        if (!relatedProcessAccess.canAccessContract(userId, app)) {
            throw exception(PAYMENT_LEASE_REF_INVALID);
        }
        if (!FinanceContractApprovalStatusEnum.APPROVED.getStatus().equals(app.getApprovalStatus())) {
            throw exception(PAYMENT_LEASE_REF_INVALID);
        }
        if (!LEASE_FILE_TYPE.equals(app.getFileType())) {
            throw exception(PAYMENT_LEASE_REF_INVALID);
        }
        if (Boolean.TRUE.equals(app.getVoided())) {
            throw exception(PAYMENT_LEASE_REF_INVALID);
        }
        return app;
    }

    private static void addPurchase(List<FinancePurchaseInstanceRespVO> result, Set<String> seen,
                                    List<HistoricProcessInstance> list) {
        if (list == null) {
            return;
        }
        for (HistoricProcessInstance hi : list) {
            if (hi == null || hi.getId() == null || !seen.add(hi.getId())) {
                continue;
            }
            result.add(toPurchaseResp(hi));
        }
    }

    private HistoryService requireHistoryService() {
        HistoryService historyService = historyServiceProvider.getIfAvailable();
        if (historyService == null) {
            throw exception(PAYMENT_BPM_HISTORY_UNAVAILABLE);
        }
        return historyService;
    }

    /**
     * F5：对齐 BpmProcessInstanceServiceImpl 租户隔离。
     */
    private static void applyTenantFilter(HistoricProcessInstanceQuery query) {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId != null) {
            query.processInstanceTenantId(String.valueOf(tenantId));
        } else {
            query.processInstanceTenantId(ProcessEngineConfiguration.NO_TENANT_ID);
        }
    }

    private static FinancePurchaseInstanceRespVO toPurchaseResp(HistoricProcessInstance hi) {
        FinancePurchaseInstanceRespVO vo = new FinancePurchaseInstanceRespVO();
        vo.setProcessInstanceId(hi.getId());
        vo.setProcessDefinitionKey(hi.getProcessDefinitionKey());
        vo.setName(hi.getName());
        if (StrUtil.isNotBlank(hi.getStartUserId())) {
            try {
                vo.setStartUserId(Long.valueOf(hi.getStartUserId()));
            } catch (NumberFormatException ignored) {
                // leave null
            }
        }
        vo.setStartTime(toLocalDateTime(hi.getStartTime()));
        vo.setEndTime(toLocalDateTime(hi.getEndTime()));
        vo.setSummary(buildSummary(hi));
        return vo;
    }

    private static String buildSummary(HistoricProcessInstance hi) {
        StringBuilder sb = new StringBuilder();
        if (StrUtil.isNotBlank(hi.getName())) {
            sb.append(hi.getName().trim());
        } else {
            sb.append(hi.getProcessDefinitionKey());
        }
        if (hi.getEndTime() != null) {
            sb.append(" · 结束 ").append(toLocalDateTime(hi.getEndTime()));
        } else if (hi.getStartTime() != null) {
            sb.append(" · 发起 ").append(toLocalDateTime(hi.getStartTime()));
        }
        Map<String, Object> vars = hi.getProcessVariables();
        if (vars != null) {
            // 动态表常见字段尝试拼摘要
            appendVar(sb, vars, "purchaseType");
            appendVar(sb, vars, "costProject");
            appendVar(sb, vars, "totalAmount");
        }
        return sb.toString();
    }

    private static void appendVar(StringBuilder sb, Map<String, Object> vars, String key) {
        Object v = vars.get(key);
        if (v != null && StrUtil.isNotBlank(String.valueOf(v))) {
            sb.append(" · ").append(v);
        }
    }

    private static LocalDateTime toLocalDateTime(Date date) {
        if (date == null) {
            return null;
        }
        return LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }

    private static Integer toInteger(Object status) {
        if (status instanceof Integer i) {
            return i;
        }
        if (status instanceof Number n) {
            return n.intValue();
        }
        if (status instanceof String s && StrUtil.isNotBlank(s)) {
            try {
                return Integer.valueOf(s.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

}
