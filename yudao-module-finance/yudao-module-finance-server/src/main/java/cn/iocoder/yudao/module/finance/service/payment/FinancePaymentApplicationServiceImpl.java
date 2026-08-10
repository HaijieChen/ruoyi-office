package cn.iocoder.yudao.module.finance.service.payment;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.yudao.module.finance.controller.admin.payment.vo.FinancePaymentApplicationCreateAndStartReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.payment.vo.FinancePaymentApplicationPageReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.payment.vo.FinancePaymentApplicationResubmitReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.payment.vo.FinancePaymentRecordPayReqVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.contract.FinanceContractApplicationDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.customer.FinanceCustomerCompanyDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.payment.FinancePaymentApplicationDO;
import cn.iocoder.yudao.module.finance.dal.mysql.contract.FinanceContractApplicationMapper;
import cn.iocoder.yudao.module.finance.dal.mysql.payment.FinancePaymentApplicationMapper;
import cn.iocoder.yudao.module.finance.dal.redis.no.FinancePaymentApplicationNoRedisDAO;
import cn.iocoder.yudao.module.finance.enums.FinanceContractApprovalStatusEnum;
import cn.iocoder.yudao.module.finance.enums.FinancePaymentApplicationStatusEnum;
import cn.iocoder.yudao.module.finance.enums.FinancePaymentReasonEnum;
import cn.iocoder.yudao.module.finance.enums.FinancePaymentTimingEnum;
import cn.iocoder.yudao.module.finance.service.customer.FinanceCustomerCompanyService;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.flowable.engine.HistoryService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.*;

@Service
@Validated
public class FinancePaymentApplicationServiceImpl implements FinancePaymentApplicationService {

    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final int AMOUNT_SCALE = 2;
    private static final String DICT_COST_PROJECT = "finance_cost_project";
    private static final String DICT_PAY_METHOD = "finance_pay_method";
    private static final String DICT_ACCOUNTING_SUBJECT = "finance_accounting_subject";

    /**
     * PAY-R18：出纳节点 active 时禁止申请人撤回（TOCTOU 防线，对标合同 seal 禁止集）。
     * 台账侧另仅允许 PENDING；禁止集与 WAIT_PAY 双保险。
     */
    private static final Set<String> CANCEL_FORBIDDEN_TASK_KEYS = Set.of(TASK_CASHIER);

    private final FinancePaymentApplicationMapper applicationMapper;
    private final FinancePaymentApplicationNoRedisDAO applicationNoRedisDAO;
    private final BpmProcessInstanceApi processInstanceApi;
    private final FinanceCustomerCompanyService customerCompanyService;
    private final FinancePaymentPredocService paymentPredocService;
    private final FinanceContractApplicationMapper contractApplicationMapper;
    private final ObjectProvider<TaskService> taskServiceProvider;
    private final ObjectProvider<HistoryService> historyServiceProvider;
    private final AdminUserApi adminUserApi;
    /** PAY-R10：字典校验必需依赖，禁止缺失时 fail-open */
    private final DictDataApi dictDataApi;
    private final ObjectProvider<DeptApi> deptApiProvider;

    public FinancePaymentApplicationServiceImpl(FinancePaymentApplicationMapper applicationMapper,
                                                FinancePaymentApplicationNoRedisDAO applicationNoRedisDAO,
                                                BpmProcessInstanceApi processInstanceApi,
                                                FinanceCustomerCompanyService customerCompanyService,
                                                FinancePaymentPredocService paymentPredocService,
                                                FinanceContractApplicationMapper contractApplicationMapper,
                                                ObjectProvider<TaskService> taskServiceProvider,
                                                ObjectProvider<HistoryService> historyServiceProvider,
                                                AdminUserApi adminUserApi,
                                                DictDataApi dictDataApi,
                                                ObjectProvider<DeptApi> deptApiProvider) {
        this.applicationMapper = applicationMapper;
        this.applicationNoRedisDAO = applicationNoRedisDAO;
        this.processInstanceApi = processInstanceApi;
        this.customerCompanyService = customerCompanyService;
        this.paymentPredocService = paymentPredocService;
        this.contractApplicationMapper = contractApplicationMapper;
        this.taskServiceProvider = taskServiceProvider;
        this.historyServiceProvider = historyServiceProvider;
        this.adminUserApi = adminUserApi;
        this.dictDataApi = dictDataApi;
        this.deptApiProvider = deptApiProvider;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createAndStart(FinancePaymentApplicationCreateAndStartReqVO reqVO, Long applicantUserId) {
        PreparedPayment prepared = prepareFromReq(reqVO, applicantUserId);

        String applicationNo = applicationNoRedisDAO.generate(LocalDate.now());
        FinancePaymentApplicationDO application = prepared.toDoBuilder()
                .applicationNo(applicationNo)
                .status(FinancePaymentApplicationStatusEnum.PENDING.getStatus())
                .voided(Boolean.FALSE)
                .applyDate(LocalDate.now())
                .build();
        // PAY-R9：实体显式带当前租户（TenantLine 也会拼 SQL；双写便于契约测与排障）
        applyCurrentTenantId(application);
        applicationMapper.insert(application);

        String processInstanceId = startProcess(applicantUserId, application, reqVO);
        FinancePaymentApplicationDO processUpdate = new FinancePaymentApplicationDO();
        processUpdate.setId(application.getId());
        processUpdate.setProcessInstanceId(processInstanceId);
        applicationMapper.updateById(processUpdate);
        return application.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resubmit(Long id, FinancePaymentApplicationResubmitReqVO reqVO, Long userId) {
        FinancePaymentApplicationDO existing = getApplication(id);
        assertOwner(existing, userId);
        if (!FinancePaymentApplicationStatusEnum.REJECTED.getStatus().equals(existing.getStatus())
                || Boolean.TRUE.equals(existing.getVoided())) {
            throw exception(PAYMENT_APPLICATION_STATUS_INVALID);
        }

        PreparedPayment prepared = prepareFromReq(reqVO, userId);

        int claimed = applicationMapper.update(null, new UpdateWrapper<FinancePaymentApplicationDO>()
                .eq("id", id)
                .eq("applicant_user_id", userId)
                .eq("status", FinancePaymentApplicationStatusEnum.REJECTED.getStatus())
                .and(w -> w.eq("voided", Boolean.FALSE).or().isNull("voided"))
                .set("status", FinancePaymentApplicationStatusEnum.PENDING.getStatus())
                .set("voided", Boolean.FALSE)
                .set("current_node_key", null)
                .set("current_node_name", null)
                .set("payment_timing", prepared.getPaymentTiming())
                .set("payment_reason", prepared.getPaymentReason())
                .set("purchase_process_instance_id", prepared.getPurchaseProcessInstanceId())
                .set("purchase_snapshot", prepared.getPurchaseSnapshot())
                .set("lease_contract_application_id", prepared.getLeaseContractApplicationId())
                .set("related_contract_application_id", prepared.getRelatedContractApplicationId())
                .set("payee_company_id", prepared.getPayeeCompanyId())
                .set("payee_name", prepared.getPayeeName())
                .set("payee_bank_name", prepared.getPayeeBankName())
                .set("payee_bank_account", prepared.getPayeeBankAccount())
                .set("apply_amount", prepared.getApplyAmount())
                .set("amount_in_words", prepared.getAmountInWords())
                .set("currency", prepared.getCurrency())
                .set("business_settlement_term", prepared.getBusinessSettlementTerm())
                .set("contract_settlement_method", prepared.getContractSettlementMethod())
                .set("pay_method", prepared.getPayMethod())
                .set("cost_project", prepared.getCostProject())
                // 费用科目/性质仅由财务审批节点填写；申请人重提时清除上一轮财务填写。
                .set("accounting_subject", null)
                .set("evidence_file_urls", prepared.getEvidenceFileUrls())
                .set("special_note", prepared.getSpecialNote())
                .set("process_title", prepared.getProcessTitle())
                .set("applicant_dept_id", prepared.getApplicantDeptId())
                .set("actual_pay_date", null)
                .set("pay_voucher_url", null)
                .set("erp_voucher_no", null));
        if (claimed == 0) {
            throw exception(PAYMENT_APPLICATION_STATUS_INVALID);
        }

        FinancePaymentApplicationDO reloaded = getApplication(id);
        String processInstanceId = startProcess(userId, reloaded, reqVO);
        FinancePaymentApplicationDO processUpdate = new FinancePaymentApplicationDO();
        processUpdate.setId(id);
        processUpdate.setProcessInstanceId(processInstanceId);
        applicationMapper.updateById(processUpdate);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id, Long userId) {
        FinancePaymentApplicationDO application = getApplication(id);
        assertOwner(application, userId);
        // PAY-R18 / decision-wait-pay-no-cancel：仅审批中可撤；WAIT_PAY 禁止
        if (!FinancePaymentApplicationStatusEnum.PENDING.getStatus().equals(application.getStatus())) {
            throw exception(PAYMENT_APPLICATION_STATUS_INVALID);
        }
        String processInstanceId = application.getProcessInstanceId();
        if (StrUtil.isNotBlank(processInstanceId)) {
            processInstanceApi.cancelProcessInstanceByStartUser(
                    userId, processInstanceId, "申请人撤回付款申请",
                    CANCEL_FORBIDDEN_TASK_KEYS).checkError();
        }
        // 同步落账（主路径之一；不依赖 async StatusListener）
        onApprovalOutcome(id, FinancePaymentApplicationStatusEnum.CANCELLED.getStatus(), processInstanceId);
    }

    /**
     * PAY-R8/R14：运维重放终态（仅 REJECTED/CANCELLED）。
     * 须 PI 与台账一致，且 Flowable 历史实例已结束且 PROCESS_STATUS 与 outcome 一致。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replayTerminalOutcome(Long id, String outcome, String processInstanceId) {
        if (id == null || StrUtil.isBlank(outcome) || StrUtil.isBlank(processInstanceId)) {
            throw exception(PAYMENT_APPLICATION_APPROVAL_OUTCOME_INVALID);
        }
        String normalized = outcome.trim().toUpperCase();
        if (!FinancePaymentApplicationStatusEnum.REJECTED.getStatus().equals(normalized)
                && !FinancePaymentApplicationStatusEnum.CANCELLED.getStatus().equals(normalized)) {
            throw exception(PAYMENT_APPLICATION_APPROVAL_OUTCOME_INVALID);
        }
        String pi = processInstanceId.trim();
        FinancePaymentApplicationDO current = getApplication(id);
        if (!Objects.equals(pi, current.getProcessInstanceId())) {
            throw exception(PAYMENT_APPLICATION_APPROVAL_OUTCOME_INVALID);
        }
        // PAY-R14：禁止对仍在运行的流程改写台账
        assertHistoricProcessEndedWithOutcome(pi, normalized);
        onApprovalOutcome(id, normalized, pi);
    }

    /**
     * 历史实例须已结束，且 PROCESS_STATUS 变量映射到期望 outcome。
     */
    private void assertHistoricProcessEndedWithOutcome(String processInstanceId, String expectedOutcome) {
        HistoryService historyService = historyServiceProvider.getIfAvailable();
        if (historyService == null) {
            throw exception(PAYMENT_APPLICATION_APPROVAL_OUTCOME_INVALID);
        }
        HistoricProcessInstance hi = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .includeProcessVariables()
                .singleResult();
        if (hi == null || hi.getEndTime() == null) {
            // 不存在或未结束
            throw exception(PAYMENT_APPLICATION_APPROVAL_OUTCOME_INVALID);
        }
        Object statusVar = hi.getProcessVariables() != null
                ? hi.getProcessVariables().get(cn.iocoder.yudao.module.finance.framework.bpm.FinancePaymentApprovalOutcomeDelegate.PROCESS_STATUS_VARIABLE)
                : null;
        Integer processStatus = null;
        if (statusVar instanceof Integer i) {
            processStatus = i;
        } else if (statusVar instanceof Number n) {
            processStatus = n.intValue();
        } else if (statusVar != null) {
            try {
                processStatus = Integer.parseInt(statusVar.toString());
            } catch (NumberFormatException ignored) {
                processStatus = null;
            }
        }
        String mapped = cn.iocoder.yudao.module.finance.framework.bpm.FinancePaymentApprovalOutcomeDelegate
                .mapProcessStatusToOutcome(processStatus);
        if (!expectedOutcome.equals(mapped)) {
            throw exception(PAYMENT_APPLICATION_APPROVAL_OUTCOME_INVALID);
        }
    }

    @Override
    public FinancePaymentApplicationDO getApplication(Long id) {
        FinancePaymentApplicationDO application = applicationMapper.selectById(id);
        if (application == null) {
            throw exception(PAYMENT_APPLICATION_NOT_EXISTS);
        }
        return application;
    }

    @Override
    public FinancePaymentApplicationDO getApplicationForRead(Long id, Long userId, boolean manageAll) {
        FinancePaymentApplicationDO application = getApplication(id);
        if (manageAll || Objects.equals(application.getApplicantUserId(), userId)
                || isActiveTaskCandidateOrAssignee(application, userId)) {
            return application;
        }
        throw exception(PAYMENT_APPLICATION_ACCESS_DENIED);
    }

    @Override
    public boolean canAccessDetail(Long id, Long userId) {
        if (id == null || userId == null) {
            return false;
        }
        FinancePaymentApplicationDO application = applicationMapper.selectById(id);
        if (application == null) {
            return false;
        }
        if (Objects.equals(application.getApplicantUserId(), userId)) {
            return true;
        }
        return isActiveTaskCandidateOrAssignee(application, userId);
    }

    @Override
    public PageResult<FinancePaymentApplicationDO> getApplicationPage(FinancePaymentApplicationPageReqVO pageReqVO,
                                                                      Long loginUserId, boolean manageAll) {
        Long filterApplicant = manageAll ? null : loginUserId;
        return applicationMapper.selectPage(pageReqVO, filterApplicant);
    }

    @Override
    public BigDecimal sumPaidByPayee(Long payeeCompanyId) {
        if (payeeCompanyId == null) {
            return ZERO;
        }
        return applicationMapper.sumPaidByPayee(payeeCompanyId);
    }

    @Override
    public void updateCurrentNode(Long appId, String nodeKey, String nodeName) {
        if (appId == null) {
            return;
        }
        UpdateWrapper<FinancePaymentApplicationDO> uw = new UpdateWrapper<FinancePaymentApplicationDO>()
                .eq("id", appId)
                .set("current_node_key", nodeKey)
                .set("current_node_name", nodeName);
        // 出纳节点 → WAIT_PAY
        if (TASK_CASHIER.equals(nodeKey) || "cashier".equals(nodeKey)) {
            uw.set("status", FinancePaymentApplicationStatusEnum.WAIT_PAY.getStatus());
        }
        applicationMapper.update(null, uw);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onApprovalOutcome(Long appId, String outcome, String processInstanceId) {
        if (appId == null || StrUtil.isBlank(outcome)) {
            throw exception(PAYMENT_APPLICATION_APPROVAL_OUTCOME_INVALID);
        }
        String normalized = outcome.trim().toUpperCase();
        // 合同/BPM 可能传 APPROVED；付款成功结束映射为 PAID
        if ("APPROVED".equals(normalized)) {
            normalized = FinancePaymentApplicationStatusEnum.PAID.getStatus();
        }
        if (!FinancePaymentApplicationStatusEnum.PAID.getStatus().equals(normalized)
                && !FinancePaymentApplicationStatusEnum.REJECTED.getStatus().equals(normalized)
                && !FinancePaymentApplicationStatusEnum.CANCELLED.getStatus().equals(normalized)) {
            throw exception(PAYMENT_APPLICATION_APPROVAL_OUTCOME_INVALID);
        }

        FinancePaymentApplicationDO current = getApplication(appId);

        // F9：旧流程实例延迟终态不得改写重提后的台账（对齐合同 CS-F7）
        if (StrUtil.isNotBlank(processInstanceId)
                && StrUtil.isNotBlank(current.getProcessInstanceId())
                && !Objects.equals(processInstanceId, current.getProcessInstanceId())) {
            return;
        }

        if (normalized.equals(current.getStatus())) {
            return; // 幂等
        }
        // 已终态且不同则忽略（防乱序）
        if (isTerminal(current.getStatus()) && !normalized.equals(current.getStatus())) {
            return;
        }
        // F1：PAID 必须已有出纳证据，防止无凭证进入累计
        if (FinancePaymentApplicationStatusEnum.PAID.getStatus().equals(normalized)) {
            assertCashierEvidencePresent(current);
        }

        UpdateWrapper<FinancePaymentApplicationDO> uw = new UpdateWrapper<FinancePaymentApplicationDO>()
                .eq("id", appId)
                .in("status",
                        FinancePaymentApplicationStatusEnum.PENDING.getStatus(),
                        FinancePaymentApplicationStatusEnum.WAIT_PAY.getStatus())
                .set("status", normalized)
                .set("current_node_key", null)
                .set("current_node_name", null);
        // 绑定当前实例，避免旧实例回调误更新（与 processInstanceId 匹配检查双保险）
        if (StrUtil.isNotBlank(processInstanceId)) {
            uw.eq("process_instance_id", processInstanceId);
        }
        int rows = applicationMapper.update(null, uw);
        if (rows == 0) {
            FinancePaymentApplicationDO again = getApplication(appId);
            if (normalized.equals(again.getStatus())) {
                return; // 并发幂等
            }
            if (StrUtil.isNotBlank(processInstanceId)
                    && StrUtil.isNotBlank(again.getProcessInstanceId())
                    && !Objects.equals(processInstanceId, again.getProcessInstanceId())) {
                return; // 重提换实例
            }
            // 状态已变或条件不满足：静默（防乱序），不抛以免 BPM end 事务回滚
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordPay(FinancePaymentRecordPayReqVO reqVO, Long userId) {
        FinancePaymentApplicationDO application = getApplication(reqVO.getId());
        if (!FinancePaymentApplicationStatusEnum.WAIT_PAY.getStatus().equals(application.getStatus())
                && !FinancePaymentApplicationStatusEnum.PENDING.getStatus().equals(application.getStatus())) {
            throw exception(PAYMENT_APPLICATION_STATUS_INVALID);
        }
        if (reqVO.getActualPayDate() == null || StrUtil.isBlank(reqVO.getPayVoucherUrl())) {
            throw exception(PAYMENT_APPLICATION_CASHIER_FIELDS_REQUIRED);
        }
        if (!isAcceptableFileUrl(reqVO.getPayVoucherUrl().trim())) {
            throw exception(PAYMENT_APPLICATION_EVIDENCE_URL_INVALID);
        }

        // F2：校验办理人/候选人后再写台账
        Task task = requireCashierTask(reqVO.getTaskId(), application, userId);

        int updated = applicationMapper.update(null, new UpdateWrapper<FinancePaymentApplicationDO>()
                .eq("id", application.getId())
                .in("status",
                        FinancePaymentApplicationStatusEnum.WAIT_PAY.getStatus(),
                        FinancePaymentApplicationStatusEnum.PENDING.getStatus())
                .eq("process_instance_id", application.getProcessInstanceId())
                .set("actual_pay_date", reqVO.getActualPayDate())
                .set("pay_voucher_url", reqVO.getPayVoucherUrl().trim())
                .set("erp_voucher_no", StrUtil.blankToDefault(trimToNull(reqVO.getErpVoucherNo()), null)));
        if (updated == 0) {
            throw exception(PAYMENT_APPLICATION_STATUS_INVALID);
        }

        TaskService taskService = taskServiceProvider.getIfAvailable();
        if (taskService == null) {
            throw exception(PAYMENT_APPLICATION_TASK_INVALID);
        }
        taskService.complete(task.getId());
        // 终态 PAID 由 end Delegate / Listener 写入；此处不提前标 PAID 以免流程失败半状态
    }

    @Override
    public void assertCashierEvidenceForComplete(Long appId) {
        FinancePaymentApplicationDO application = getApplication(appId);
        assertCashierEvidencePresent(application);
    }

    @Override
    public void assertFinanceSubjectForComplete(Long appId) {
        FinancePaymentApplicationDO application = getApplication(appId);
        if (StrUtil.isBlank(application.getAccountingSubject())) {
            throw exception(PAYMENT_APPLICATION_ACCOUNTING_SUBJECT_REQUIRED);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAccountingSubject(Long id, String accountingSubject, String taskId, Long userId) {
        if (StrUtil.isBlank(accountingSubject)) {
            throw exception(PAYMENT_APPLICATION_ACCOUNTING_SUBJECT_REQUIRED);
        }
        String subject = accountingSubject.trim();
        validateDictValue(DICT_ACCOUNTING_SUBJECT, subject);
        FinancePaymentApplicationDO application = getApplication(id);
        requireTask(taskId, application, TASK_FINANCE, userId);
        applicationMapper.update(null, new UpdateWrapper<FinancePaymentApplicationDO>()
                .eq("id", id)
                .in("status",
                        FinancePaymentApplicationStatusEnum.PENDING.getStatus(),
                        FinancePaymentApplicationStatusEnum.WAIT_PAY.getStatus())
                .set("accounting_subject", subject));
    }

    private static void assertCashierEvidencePresent(FinancePaymentApplicationDO application) {
        if (application.getActualPayDate() == null || StrUtil.isBlank(application.getPayVoucherUrl())) {
            throw exception(PAYMENT_APPLICATION_CASHIER_FIELDS_REQUIRED);
        }
    }

    private boolean isActiveTaskCandidateOrAssignee(FinancePaymentApplicationDO application, Long userId) {
        if (userId == null || StrUtil.isBlank(application.getProcessInstanceId())) {
            return false;
        }
        TaskService taskService = taskServiceProvider.getIfAvailable();
        if (taskService == null) {
            return false;
        }
        return taskService.createTaskQuery()
                .processInstanceId(application.getProcessInstanceId())
                .taskCandidateOrAssigned(String.valueOf(userId))
                .count() > 0;
    }

    /**
     * PAY-R6：申请人部门仅以用户档案为准；无部门则失败关闭，绝不回退客户端伪造值。
     */
    private Long resolveApplicantDeptId(Long applicantUserId) {
        AdminUserRespDTO user = adminUserApi.getUser(applicantUserId).getCheckedData();
        if (user == null || user.getDeptId() == null) {
            throw exception(PAYMENT_APPLICATION_DEPT_REQUIRED);
        }
        return user.getDeptId();
    }

    // --- internal ---

    private PreparedPayment prepareFromReq(FinancePaymentApplicationCreateAndStartReqVO reqVO, Long applicantUserId) {
        if (!FinancePaymentTimingEnum.contains(reqVO.getPaymentTiming())) {
            throw exception(PAYMENT_APPLICATION_TIMING_INVALID);
        }
        if (!FinancePaymentReasonEnum.contains(reqVO.getPaymentReason())) {
            throw exception(PAYMENT_APPLICATION_REASON_INVALID);
        }
        BigDecimal applyAmount = normalizeApplyAmount(reqVO.getApplyAmount());
        if (CollUtil.isEmpty(reqVO.getEvidenceFileUrls())
                || reqVO.getEvidenceFileUrls().stream().noneMatch(StrUtil::isNotBlank)) {
            throw exception(PAYMENT_APPLICATION_EVIDENCE_REQUIRED);
        }
        validateEvidenceUrls(reqVO.getEvidenceFileUrls());
        if (StrUtil.isBlank(reqVO.getBusinessSettlementTerm())
                || StrUtil.isBlank(reqVO.getPayMethod())
                || StrUtil.isBlank(reqVO.getCostProject())) {
            throw exception(PAYMENT_APPLICATION_FIELD_REQUIRED);
        }
        String payMethod = reqVO.getPayMethod().trim();
        String costProject = reqVO.getCostProject().trim();
        validateDictValue(DICT_PAY_METHOD, payMethod);
        validateDictValue(DICT_COST_PROJECT, costProject);
        FinanceCustomerCompanyDO payee = customerCompanyService.getEnabledSupplierCompany(reqVO.getPayeeCompanyId());
        String bankName = StrUtil.blankToDefault(trimToNull(reqVO.getPayeeBankName()), payee.getBankName());
        String bankAccount = StrUtil.blankToDefault(trimToNull(reqVO.getPayeeBankAccount()), payee.getBankAccount());
        if (StrUtil.isBlank(bankName) || StrUtil.isBlank(bankAccount)) {
            throw exception(CUSTOMER_COMPANY_SUPPLIER_BANK_REQUIRED);
        }

        String reason = reqVO.getPaymentReason();
        String purchasePi = null;
        String purchaseSnapshot = null;
        Long leaseId = null;
        if (FinancePaymentReasonEnum.PURCHASE.getCode().equals(reason)) {
            purchaseSnapshot = paymentPredocService.validateAndSummarizePurchaseRef(
                    reqVO.getPurchaseProcessInstanceId(), applicantUserId);
            purchasePi = reqVO.getPurchaseProcessInstanceId().trim();
        } else if (FinancePaymentReasonEnum.LEASE.getCode().equals(reason)) {
            FinanceContractApplicationDO lease = paymentPredocService.validateLeaseContractRef(
                    reqVO.getLeaseContractApplicationId(), applicantUserId);
            leaseId = lease.getId();
        } else {
            purchasePi = null;
            leaseId = null;
        }

        String settlement = null;
        Long relatedContractId = reqVO.getRelatedContractApplicationId();
        // PAY-R3：仅 BUSINESS 允许可选关联合同；LEASE 结算只来自租赁前置
        if (relatedContractId != null) {
            if (!FinancePaymentReasonEnum.BUSINESS.getCode().equals(reason)) {
                throw exception(PAYMENT_RELATED_CONTRACT_REASON_INVALID);
            }
            FinanceContractApplicationDO related = contractApplicationMapper.selectById(relatedContractId);
            if (related == null
                    || Boolean.TRUE.equals(related.getVoided())
                    || !FinanceContractApprovalStatusEnum.APPROVED.getStatus().equals(related.getApprovalStatus())) {
                throw exception(PAYMENT_RELATED_CONTRACT_INVALID);
            }
            // 可见性：本人申请的合同（与租赁选择器一致）；FA 全量在租户内已由 TenantBaseDO 隔离
            if (!Objects.equals(related.getApplicantUserId(), applicantUserId)) {
                throw exception(PAYMENT_RELATED_CONTRACT_INVALID);
            }
            settlement = related.getSettlementMethod();
        }
        if (leaseId != null) {
            // LEASE：结算方式只能来自租赁合同本身，忽略 related
            relatedContractId = null;
            FinanceContractApplicationDO lease = contractApplicationMapper.selectById(leaseId);
            if (lease != null) {
                settlement = lease.getSettlementMethod();
            }
        }

        String currency = StrUtil.blankToDefault(trimToNull(reqVO.getCurrency()), "CNY");
        String evidenceJson = toEvidenceJson(reqVO.getEvidenceFileUrls());
        Long deptId = resolveApplicantDeptId(applicantUserId);
        String deptName = resolveDeptName(deptId);
        String title = buildTitle(payee.getName(), applyAmount, deptName);
        String amountInWords = toAmountInWords(applyAmount);

        return PreparedPayment.builder()
                .paymentTiming(reqVO.getPaymentTiming())
                .paymentReason(reason)
                .purchaseProcessInstanceId(purchasePi)
                .purchaseSnapshot(purchaseSnapshot)
                .leaseContractApplicationId(leaseId)
                .relatedContractApplicationId(relatedContractId)
                .payeeCompanyId(payee.getId())
                .payeeName(payee.getName())
                .payeeBankName(bankName)
                .payeeBankAccount(bankAccount)
                .applyAmount(applyAmount)
                .amountInWords(amountInWords)
                .currency(currency)
                .businessSettlementTerm(reqVO.getBusinessSettlementTerm().trim())
                .contractSettlementMethod(settlement)
                .payMethod(payMethod)
                .costProject(costProject)
                .evidenceFileUrls(evidenceJson)
                .specialNote(trimToNull(reqVO.getSpecialNote()))
                .processTitle(title)
                .applicantUserId(applicantUserId)
                .applicantDeptId(deptId)
                .build();
    }

    private String startProcess(Long userId, FinancePaymentApplicationDO application,
                                FinancePaymentApplicationCreateAndStartReqVO reqVO) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("paymentApplicationId", application.getId());
        variables.put("applicationNo", application.getApplicationNo());
        variables.put("applyAmount", application.getApplyAmount());
        variables.put("payeeName", application.getPayeeName());
        variables.put("paymentReason", application.getPaymentReason());
        variables.put("applicantUserId", application.getApplicantUserId());
        variables.put("paymentTiming", application.getPaymentTiming());
        return processInstanceApi.createProcessInstance(userId,
                        new BpmProcessInstanceCreateReqDTO()
                                .setProcessDefinitionKey(PROCESS_KEY)
                                .setBusinessKey(String.valueOf(application.getId()))
                                .setVariables(variables)
                                .setStartUserSelectAssignees(reqVO.getStartUserSelectAssignees()))
                .getCheckedData();
    }

    private Task requireCashierTask(String taskId, FinancePaymentApplicationDO application, Long userId) {
        return requireTask(taskId, application, TASK_CASHIER, userId);
    }

    /**
     * F2/F4：校验 task 存在、节点 key、绑定当前流程实例，且 user 为候选人/办理人。
     */
    private Task requireTask(String taskId, FinancePaymentApplicationDO application,
                             String expectedTaskKey, Long userId) {
        if (StrUtil.isBlank(taskId) || userId == null) {
            throw exception(PAYMENT_APPLICATION_TASK_INVALID);
        }
        TaskService taskService = taskServiceProvider.getIfAvailable();
        if (taskService == null) {
            throw exception(PAYMENT_APPLICATION_TASK_INVALID);
        }
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw exception(PAYMENT_APPLICATION_TASK_INVALID);
        }
        if (!expectedTaskKey.equals(task.getTaskDefinitionKey())) {
            throw exception(PAYMENT_APPLICATION_TASK_INVALID);
        }
        if (StrUtil.isBlank(application.getProcessInstanceId())
                || !Objects.equals(application.getProcessInstanceId(), task.getProcessInstanceId())) {
            throw exception(PAYMENT_APPLICATION_TASK_INVALID);
        }
        long visible = taskService.createTaskQuery()
                .taskId(taskId)
                .taskCandidateOrAssigned(String.valueOf(userId))
                .count();
        if (visible <= 0) {
            throw exception(PAYMENT_APPLICATION_TASK_INVALID);
        }
        return task;
    }

    private static void assertOwner(FinancePaymentApplicationDO application, Long userId) {
        if (!Objects.equals(application.getApplicantUserId(), userId)) {
            throw exception(PAYMENT_APPLICATION_ACCESS_DENIED);
        }
    }

    private static boolean isTerminal(String status) {
        return FinancePaymentApplicationStatusEnum.PAID.getStatus().equals(status)
                || FinancePaymentApplicationStatusEnum.REJECTED.getStatus().equals(status)
                || FinancePaymentApplicationStatusEnum.CANCELLED.getStatus().equals(status);
    }

    private static String buildTitle(String payeeName, BigDecimal amount, String deptName) {
        String shortName = payeeName == null ? "" : (payeeName.length() > 12 ? payeeName.substring(0, 12) : payeeName);
        String deptPart = StrUtil.isNotBlank(deptName) ? deptName + "-" : "";
        return "【付款申请】-" + deptPart + shortName + "-" + amount;
    }

    private String resolveDeptName(Long deptId) {
        if (deptId == null) {
            return null;
        }
        DeptApi deptApi = deptApiProvider.getIfAvailable();
        if (deptApi == null) {
            return null;
        }
        try {
            DeptRespDTO dept = deptApi.getDept(deptId).getCheckedData();
            return dept != null ? dept.getName() : null;
        } catch (Exception ex) {
            return null;
        }
    }

    private static BigDecimal normalizeApplyAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(ZERO) <= 0) {
            throw exception(PAYMENT_APPLICATION_AMOUNT_INVALID);
        }
        // 拒绝过多小数，避免 decimal(18,2) 静默截断为 0.00
        if (amount.scale() > AMOUNT_SCALE) {
            throw exception(PAYMENT_APPLICATION_AMOUNT_INVALID);
        }
        return amount.setScale(AMOUNT_SCALE, RoundingMode.UNNECESSARY);
    }

    private static String toAmountInWords(BigDecimal amount) {
        // hutool：阿拉伯数字转中文金额描述
        return Convert.digitToChinese(amount);
    }

    private void validateDictValue(String dictType, String value) {
        if (dictDataApi == null) {
            throw exception(PAYMENT_APPLICATION_DICT_INVALID);
        }
        try {
            dictDataApi.validateDictDataList(dictType, Collections.singletonList(value)).checkError();
        } catch (Exception ex) {
            throw exception(PAYMENT_APPLICATION_DICT_INVALID);
        }
    }

    private static void applyCurrentTenantId(FinancePaymentApplicationDO application) {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId != null) {
            application.setTenantId(tenantId);
        }
    }

    private static void validateEvidenceUrls(List<String> urls) {
        boolean anyValid = false;
        for (String u : urls) {
            if (StrUtil.isBlank(u)) {
                continue;
            }
            String t = u.trim();
            if (!isAcceptableFileUrl(t)) {
                throw exception(PAYMENT_APPLICATION_EVIDENCE_URL_INVALID);
            }
            anyValid = true;
        }
        if (!anyValid) {
            throw exception(PAYMENT_APPLICATION_EVIDENCE_REQUIRED);
        }
    }

    /** 文件中心 URL 或 http(s) 链接 */
    private static boolean isAcceptableFileUrl(String url) {
        if (StrUtil.isBlank(url)) {
            return false;
        }
        String t = url.trim();
        return t.startsWith("http://")
                || t.startsWith("https://")
                || t.startsWith("/")
                || t.contains("/admin-api/infra/file/");
    }

    private static String toEvidenceJson(List<String> urls) {
        List<String> cleaned = urls.stream()
                .filter(StrUtil::isNotBlank)
                .map(String::trim)
                .collect(Collectors.toList());
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < cleaned.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('"').append(cleaned.get(i).replace("\"", "\\\"")).append('"');
        }
        sb.append(']');
        return sb.toString();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }

    /** 准备后的字段载体（builder 复用 insert/resubmit） */
    @lombok.Builder
    @lombok.Value
    private static class PreparedPayment {
        String paymentTiming;
        String paymentReason;
        String purchaseProcessInstanceId;
        String purchaseSnapshot;
        Long leaseContractApplicationId;
        Long relatedContractApplicationId;
        Long payeeCompanyId;
        String payeeName;
        String payeeBankName;
        String payeeBankAccount;
        BigDecimal applyAmount;
        String amountInWords;
        String currency;
        String businessSettlementTerm;
        String contractSettlementMethod;
        String payMethod;
        String costProject;
        String evidenceFileUrls;
        String specialNote;
        String processTitle;
        Long applicantUserId;
        Long applicantDeptId;

        FinancePaymentApplicationDO.FinancePaymentApplicationDOBuilder toDoBuilder() {
            return FinancePaymentApplicationDO.builder()
                    .paymentTiming(paymentTiming)
                    .paymentReason(paymentReason)
                    .purchaseProcessInstanceId(purchaseProcessInstanceId)
                    .purchaseSnapshot(purchaseSnapshot)
                    .leaseContractApplicationId(leaseContractApplicationId)
                    .relatedContractApplicationId(relatedContractApplicationId)
                    .payeeCompanyId(payeeCompanyId)
                    .payeeName(payeeName)
                    .payeeBankName(payeeBankName)
                    .payeeBankAccount(payeeBankAccount)
                    .applyAmount(applyAmount)
                    .amountInWords(amountInWords)
                    .currency(currency)
                    .businessSettlementTerm(businessSettlementTerm)
                    .contractSettlementMethod(contractSettlementMethod)
                    .payMethod(payMethod)
                    .costProject(costProject)
                    .evidenceFileUrls(evidenceFileUrls)
                    .specialNote(specialNote)
                    .processTitle(processTitle)
                    .applicantUserId(applicantUserId)
                    .applicantDeptId(applicantDeptId);
        }
    }

}
