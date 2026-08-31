package cn.iocoder.yudao.module.finance.service.payment;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.yudao.module.finance.framework.rpc.FinanceBpmProcessInstanceApi;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.finance.controller.admin.payment.vo.FinancePaymentApplicationCreateAndStartReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.payment.vo.FinancePaymentApplicationPageReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.payment.vo.FinancePaymentApplicationResubmitReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.payment.vo.FinancePaymentPayLineRespVO;
import cn.iocoder.yudao.module.finance.controller.admin.payment.vo.FinancePaymentRecordPayReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.payment.vo.FinancePaymentSalaryLineReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.payment.vo.FinancePaymentSalaryLineRespVO;
import cn.iocoder.yudao.module.finance.controller.admin.payment.vo.FinancePaymentTaxLineReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.payment.vo.FinancePaymentTaxLineRespVO;
import cn.iocoder.yudao.module.finance.controller.admin.payment.vo.FinanceSalaryPaymentCreateAndStartReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.payment.vo.FinanceTaxPaymentCreateAndStartReqVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.companyaccount.FinanceCompanyBankAccountDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.contract.FinanceContractApplicationDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.customer.FinanceCustomerCompanyDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.payment.FinancePaymentApplicationDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.payment.FinancePaymentPayLineDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.payment.FinancePaymentSalaryLineDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.payment.FinancePaymentTaxLineDO;
import cn.iocoder.yudao.module.finance.dal.mysql.contract.FinanceContractApplicationMapper;
import cn.iocoder.yudao.module.finance.dal.mysql.payment.FinancePaymentApplicationMapper;
import cn.iocoder.yudao.module.finance.dal.mysql.payment.FinancePaymentPayLineMapper;
import cn.iocoder.yudao.module.finance.dal.mysql.payment.FinancePaymentSalaryLineMapper;
import cn.iocoder.yudao.module.finance.dal.mysql.payment.FinancePaymentTaxLineMapper;
import cn.iocoder.yudao.module.finance.dal.redis.no.FinancePaymentApplicationNoRedisDAO;
import cn.iocoder.yudao.module.finance.enums.FinanceContractApprovalStatusEnum;
import cn.iocoder.yudao.module.finance.enums.FinancePaymentApplicationKindEnum;
import cn.iocoder.yudao.module.finance.enums.FinancePaymentApplicationStatusEnum;
import cn.iocoder.yudao.module.finance.enums.FinancePaymentReasonEnum;
import cn.iocoder.yudao.module.finance.enums.FinancePaymentTimingEnum;
import cn.iocoder.yudao.module.bpm.enums.BpmProcessVariableConstants;
import cn.iocoder.yudao.module.finance.framework.security.FinanceProcessParticipantSupport;
import cn.iocoder.yudao.module.finance.service.common.FinanceBusinessStaffSupport;
import cn.iocoder.yudao.module.finance.service.common.FinanceEntityCompanyResolver;
import cn.iocoder.yudao.module.finance.service.companyaccount.FinanceCompanyBankAccountService;
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
import jakarta.annotation.Resource;
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
    private static final String DICT_COST_PROJECT = "finance_product_type";
    private static final String DICT_PAY_METHOD = "finance_pay_method";
    private static final String DICT_ACCOUNTING_SUBJECT = "finance_accounting_subject";
    /** EXP-73：交易币种白名单 */
    public static final Set<String> SUPPORTED_CURRENCIES = Set.of("CNY", "USD", "HKD");

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
    private final FinanceEntityCompanyResolver entityCompanyResolver;
    private final FinanceCompanyBankAccountService companyBankAccountService;
    private final FinancePaymentPayLineMapper payLineMapper;
    private final FinancePaymentSalaryLineMapper salaryLineMapper;
    private final FinancePaymentTaxLineMapper taxLineMapper;
    @Resource
    private FinanceBusinessStaffSupport businessStaffSupport;
    @Resource
    private FinanceProcessParticipantSupport processParticipantSupport;

    public FinancePaymentApplicationServiceImpl(FinancePaymentApplicationMapper applicationMapper,
                                                FinancePaymentApplicationNoRedisDAO applicationNoRedisDAO,
                                                // EXP-87 F4：必须注入带 identity interceptor 的 Finance 专用 BPM 客户端
                                                FinanceBpmProcessInstanceApi processInstanceApi,
                                                FinanceCustomerCompanyService customerCompanyService,
                                                FinancePaymentPredocService paymentPredocService,
                                                FinanceContractApplicationMapper contractApplicationMapper,
                                                ObjectProvider<TaskService> taskServiceProvider,
                                                ObjectProvider<HistoryService> historyServiceProvider,
                                                AdminUserApi adminUserApi,
                                                DictDataApi dictDataApi,
                                                ObjectProvider<DeptApi> deptApiProvider,
                                                FinanceEntityCompanyResolver entityCompanyResolver,
                                                FinanceCompanyBankAccountService companyBankAccountService,
                                                FinancePaymentPayLineMapper payLineMapper,
                                                FinancePaymentSalaryLineMapper salaryLineMapper,
                                                FinancePaymentTaxLineMapper taxLineMapper) {
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
        this.entityCompanyResolver = entityCompanyResolver;
        this.companyBankAccountService = companyBankAccountService;
        this.payLineMapper = payLineMapper;
        this.salaryLineMapper = salaryLineMapper;
        this.taxLineMapper = taxLineMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createAndStart(FinancePaymentApplicationCreateAndStartReqVO reqVO, Long applicantUserId) {
        PreparedPayment prepared = prepareFromReq(reqVO, applicantUserId);

        String applicationNo = applicationNoRedisDAO.generate(LocalDate.now());
        FinancePaymentApplicationDO application = prepared.toDoBuilder()
                .applicationNo(applicationNo)
                .status(FinancePaymentApplicationStatusEnum.PENDING.getStatus())
                .applicationKind(FinancePaymentApplicationKindEnum.ORDINARY.getCode())
                .voided(Boolean.FALSE)
                .applyDate(LocalDate.now())
                .build();
        // PAY-R9：实体显式带当前租户（TenantLine 也会拼 SQL；双写便于契约测与排障）
        applyCurrentTenantId(application);
        applicationMapper.insert(application);

        String processInstanceId = startProcess(applicantUserId, application,
                reqVO.getStartUserSelectAssignees(), PROCESS_KEY, reqVO.getStartCompanyDeptId(), reqVO.getStartDeptId());
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
        assertOrdinaryKind(existing);
        assertNoPayLines(id);
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
                .and(w -> w.eq("application_kind", FinancePaymentApplicationKindEnum.ORDINARY.getCode())
                        .or().isNull("application_kind"))
                .set("status", FinancePaymentApplicationStatusEnum.PENDING.getStatus())
                .set("application_kind", FinancePaymentApplicationKindEnum.ORDINARY.getCode())
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
                .set("entity_company_dept_id", prepared.getEntityCompanyDeptId())
                .set("entity_company_name", prepared.getEntityCompanyName())
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
        String processInstanceId = startProcess(userId, reloaded, reqVO.getStartUserSelectAssignees(), PROCESS_KEY, reqVO.getStartCompanyDeptId(), reqVO.getStartDeptId());
        FinancePaymentApplicationDO processUpdate = new FinancePaymentApplicationDO();
        processUpdate.setId(id);
        processUpdate.setProcessInstanceId(processInstanceId);
        applicationMapper.updateById(processUpdate);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createAndStartSalary(FinanceSalaryPaymentCreateAndStartReqVO reqVO, Long applicantUserId) {
        if (!FinancePaymentTimingEnum.contains(reqVO.getPaymentTiming())) {
            throw exception(PAYMENT_APPLICATION_TIMING_INVALID);
        }
        String currency = normalizeCurrency(reqVO.getCurrency());
        String periodLabel = requirePeriod(reqVO.getPeriodLabel());
        List<String> evidence = reqVO.getEvidenceFileUrls() == null ? List.of() : reqVO.getEvidenceFileUrls();
        if (CollUtil.isNotEmpty(evidence)) {
            validateEvidenceUrls(evidence);
        }
        PreparedLines preparedLines = prepareSalaryLines(reqVO.getLines(), currency);
        Long deptId = resolveApplicantDeptId(applicantUserId);
        String title = "【薪资付款】-" + periodLabel + "-" + preparedLines.applyAmount();
        String applicationNo = applicationNoRedisDAO.generate(LocalDate.now());
        FinancePaymentApplicationDO application = FinancePaymentApplicationDO.builder()
                .applicationNo(applicationNo)
                .status(FinancePaymentApplicationStatusEnum.PENDING.getStatus())
                .applicationKind(FinancePaymentApplicationKindEnum.SALARY.getCode())
                .voided(Boolean.FALSE)
                .applyDate(LocalDate.now())
                .periodLabel(periodLabel)
                .paymentTiming(reqVO.getPaymentTiming())
                .paymentReason(FinancePaymentReasonEnum.SALARY.getCode())
                .applyAmount(preparedLines.applyAmount())
                .amountInWords(toAmountInWords(preparedLines.applyAmount()))
                .currency(currency)
                .entityCompanyDeptId(preparedLines.primaryEntityDeptId())
                .entityCompanyName(preparedLines.primaryEntityName())
                .evidenceFileUrls(CollUtil.isEmpty(evidence) ? "[]" : toEvidenceJson(evidence))
                .specialNote(trimToNull(reqVO.getSpecialNote()))
                .processTitle(title)
                .applicantUserId(applicantUserId)
                .applicantDeptId(deptId)
                .build();
        applyCurrentTenantId(application);
        applicationMapper.insert(application);
        insertSalaryLines(application.getId(), preparedLines.salaryLines());
        String processInstanceId = startProcess(applicantUserId, application,
                reqVO.getStartUserSelectAssignees(), PROCESS_KEY_SALARY, reqVO.getStartCompanyDeptId(), reqVO.getStartDeptId());
        FinancePaymentApplicationDO processUpdate = new FinancePaymentApplicationDO();
        processUpdate.setId(application.getId());
        processUpdate.setProcessInstanceId(processInstanceId);
        applicationMapper.updateById(processUpdate);
        return application.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createAndStartTax(FinanceTaxPaymentCreateAndStartReqVO reqVO, Long applicantUserId) {
        if (!FinancePaymentTimingEnum.contains(reqVO.getPaymentTiming())) {
            throw exception(PAYMENT_APPLICATION_TIMING_INVALID);
        }
        String currency = normalizeCurrency(reqVO.getCurrency());
        String periodLabel = requirePeriod(reqVO.getPeriodLabel());
        if (CollUtil.isEmpty(reqVO.getEvidenceFileUrls())
                || reqVO.getEvidenceFileUrls().stream().noneMatch(StrUtil::isNotBlank)) {
            throw exception(PAYMENT_APPLICATION_EVIDENCE_REQUIRED);
        }
        validateEvidenceUrls(reqVO.getEvidenceFileUrls());
        PreparedLines preparedLines = prepareTaxLines(reqVO.getLines(), currency);
        Long deptId = resolveApplicantDeptId(applicantUserId);
        String title = "【税金付款】-" + periodLabel + "-" + preparedLines.applyAmount();
        String applicationNo = applicationNoRedisDAO.generate(LocalDate.now());
        FinancePaymentApplicationDO application = FinancePaymentApplicationDO.builder()
                .applicationNo(applicationNo)
                .status(FinancePaymentApplicationStatusEnum.PENDING.getStatus())
                .applicationKind(FinancePaymentApplicationKindEnum.TAX.getCode())
                .voided(Boolean.FALSE)
                .applyDate(LocalDate.now())
                .periodLabel(periodLabel)
                .paymentTiming(reqVO.getPaymentTiming())
                .paymentReason(FinancePaymentReasonEnum.TAX.getCode())
                .applyAmount(preparedLines.applyAmount())
                .amountInWords(toAmountInWords(preparedLines.applyAmount()))
                .currency(currency)
                .entityCompanyDeptId(preparedLines.primaryEntityDeptId())
                .entityCompanyName(preparedLines.primaryEntityName())
                .evidenceFileUrls(toEvidenceJson(reqVO.getEvidenceFileUrls()))
                .specialNote(trimToNull(reqVO.getSpecialNote()))
                .processTitle(title)
                .applicantUserId(applicantUserId)
                .applicantDeptId(deptId)
                .build();
        applyCurrentTenantId(application);
        applicationMapper.insert(application);
        insertTaxLines(application.getId(), preparedLines.taxLines());
        String processInstanceId = startProcess(applicantUserId, application,
                reqVO.getStartUserSelectAssignees(), PROCESS_KEY_TAX, reqVO.getStartCompanyDeptId(), reqVO.getStartDeptId());
        FinancePaymentApplicationDO processUpdate = new FinancePaymentApplicationDO();
        processUpdate.setId(application.getId());
        processUpdate.setProcessInstanceId(processInstanceId);
        applicationMapper.updateById(processUpdate);
        return application.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resubmitSalary(Long id, FinanceSalaryPaymentCreateAndStartReqVO reqVO, Long userId) {
        FinancePaymentApplicationDO existing = getApplication(id);
        assertOwner(existing, userId);
        if (!FinancePaymentApplicationKindEnum.SALARY.getCode().equals(existing.getApplicationKind())) {
            throw exception(PAYMENT_APPLICATION_KIND_INVALID);
        }
        // 支付流水不可变：有任一 pay_line 禁止重提
        assertNoPayLines(id);
        if (!FinancePaymentApplicationStatusEnum.REJECTED.getStatus().equals(existing.getStatus())
                || Boolean.TRUE.equals(existing.getVoided())) {
            throw exception(PAYMENT_APPLICATION_STATUS_INVALID);
        }
        if (!FinancePaymentTimingEnum.contains(reqVO.getPaymentTiming())) {
            throw exception(PAYMENT_APPLICATION_TIMING_INVALID);
        }
        String currency = normalizeCurrency(reqVO.getCurrency());
        String periodLabel = requirePeriod(reqVO.getPeriodLabel());
        List<String> evidence = reqVO.getEvidenceFileUrls() == null ? List.of() : reqVO.getEvidenceFileUrls();
        if (CollUtil.isNotEmpty(evidence)) {
            validateEvidenceUrls(evidence);
        }
        PreparedLines preparedLines = prepareSalaryLines(reqVO.getLines(), currency);
        Long deptId = resolveApplicantDeptId(userId);
        String title = "【薪资付款】-" + periodLabel + "-" + preparedLines.applyAmount();

        int claimed = applicationMapper.update(null, new UpdateWrapper<FinancePaymentApplicationDO>()
                .eq("id", id)
                .eq("applicant_user_id", userId)
                .eq("status", FinancePaymentApplicationStatusEnum.REJECTED.getStatus())
                .eq("application_kind", FinancePaymentApplicationKindEnum.SALARY.getCode())
                .and(w -> w.eq("voided", Boolean.FALSE).or().isNull("voided"))
                .set("status", FinancePaymentApplicationStatusEnum.PENDING.getStatus())
                .set("voided", Boolean.FALSE)
                .set("current_node_key", null)
                .set("current_node_name", null)
                .set("payment_timing", reqVO.getPaymentTiming())
                .set("payment_reason", FinancePaymentReasonEnum.SALARY.getCode())
                .set("period_label", periodLabel)
                .set("apply_amount", preparedLines.applyAmount())
                .set("amount_in_words", toAmountInWords(preparedLines.applyAmount()))
                .set("currency", currency)
                .set("entity_company_dept_id", preparedLines.primaryEntityDeptId())
                .set("entity_company_name", preparedLines.primaryEntityName())
                .set("evidence_file_urls", CollUtil.isEmpty(evidence) ? "[]" : toEvidenceJson(evidence))
                .set("special_note", trimToNull(reqVO.getSpecialNote()))
                .set("process_title", title)
                .set("applicant_dept_id", deptId)
                .set("accounting_subject", null)
                .set("actual_pay_date", null)
                .set("pay_voucher_url", null)
                .set("erp_voucher_no", null));
        if (claimed == 0) {
            throw exception(PAYMENT_APPLICATION_STATUS_INVALID);
        }
        // 业务明细可重写；支付流水不可变（此处已 assertNoPayLines）
        salaryLineMapper.deleteByApplicationId(id);
        insertSalaryLines(id, preparedLines.salaryLines());

        FinancePaymentApplicationDO reloaded = getApplication(id);
        String processInstanceId = startProcess(userId, reloaded,
                reqVO.getStartUserSelectAssignees(), PROCESS_KEY_SALARY, reqVO.getStartCompanyDeptId(), reqVO.getStartDeptId());
        FinancePaymentApplicationDO processUpdate = new FinancePaymentApplicationDO();
        processUpdate.setId(id);
        processUpdate.setProcessInstanceId(processInstanceId);
        applicationMapper.updateById(processUpdate);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resubmitTax(Long id, FinanceTaxPaymentCreateAndStartReqVO reqVO, Long userId) {
        FinancePaymentApplicationDO existing = getApplication(id);
        assertOwner(existing, userId);
        if (!FinancePaymentApplicationKindEnum.TAX.getCode().equals(existing.getApplicationKind())) {
            throw exception(PAYMENT_APPLICATION_KIND_INVALID);
        }
        assertNoPayLines(id);
        if (!FinancePaymentApplicationStatusEnum.REJECTED.getStatus().equals(existing.getStatus())
                || Boolean.TRUE.equals(existing.getVoided())) {
            throw exception(PAYMENT_APPLICATION_STATUS_INVALID);
        }
        if (!FinancePaymentTimingEnum.contains(reqVO.getPaymentTiming())) {
            throw exception(PAYMENT_APPLICATION_TIMING_INVALID);
        }
        String currency = normalizeCurrency(reqVO.getCurrency());
        String periodLabel = requirePeriod(reqVO.getPeriodLabel());
        if (CollUtil.isEmpty(reqVO.getEvidenceFileUrls())
                || reqVO.getEvidenceFileUrls().stream().noneMatch(StrUtil::isNotBlank)) {
            throw exception(PAYMENT_APPLICATION_EVIDENCE_REQUIRED);
        }
        validateEvidenceUrls(reqVO.getEvidenceFileUrls());
        PreparedLines preparedLines = prepareTaxLines(reqVO.getLines(), currency);
        Long deptId = resolveApplicantDeptId(userId);
        String title = "【税金付款】-" + periodLabel + "-" + preparedLines.applyAmount();

        int claimed = applicationMapper.update(null, new UpdateWrapper<FinancePaymentApplicationDO>()
                .eq("id", id)
                .eq("applicant_user_id", userId)
                .eq("status", FinancePaymentApplicationStatusEnum.REJECTED.getStatus())
                .eq("application_kind", FinancePaymentApplicationKindEnum.TAX.getCode())
                .and(w -> w.eq("voided", Boolean.FALSE).or().isNull("voided"))
                .set("status", FinancePaymentApplicationStatusEnum.PENDING.getStatus())
                .set("voided", Boolean.FALSE)
                .set("current_node_key", null)
                .set("current_node_name", null)
                .set("payment_timing", reqVO.getPaymentTiming())
                .set("payment_reason", FinancePaymentReasonEnum.TAX.getCode())
                .set("period_label", periodLabel)
                .set("apply_amount", preparedLines.applyAmount())
                .set("amount_in_words", toAmountInWords(preparedLines.applyAmount()))
                .set("currency", currency)
                .set("entity_company_dept_id", preparedLines.primaryEntityDeptId())
                .set("entity_company_name", preparedLines.primaryEntityName())
                .set("evidence_file_urls", toEvidenceJson(reqVO.getEvidenceFileUrls()))
                .set("special_note", trimToNull(reqVO.getSpecialNote()))
                .set("process_title", title)
                .set("applicant_dept_id", deptId)
                .set("accounting_subject", null)
                .set("actual_pay_date", null)
                .set("pay_voucher_url", null)
                .set("erp_voucher_no", null));
        if (claimed == 0) {
            throw exception(PAYMENT_APPLICATION_STATUS_INVALID);
        }
        taxLineMapper.deleteByApplicationId(id);
        insertTaxLines(id, preparedLines.taxLines());

        FinancePaymentApplicationDO reloaded = getApplication(id);
        String processInstanceId = startProcess(userId, reloaded,
                reqVO.getStartUserSelectAssignees(), PROCESS_KEY_TAX, reqVO.getStartCompanyDeptId(), reqVO.getStartDeptId());
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
        if (manageAll || processParticipantSupport.canReadBill(
                userId, application.getApplicantUserId(), application.getProcessInstanceId())) {
            return application;
        }
        throw exception(PAYMENT_APPLICATION_ACCESS_DENIED);
    }

    /**
     * 普通付款读路径类型闭合：仅 ORDINARY（历史 null 视为 ORDINARY）。
     */
    public FinancePaymentApplicationDO getOrdinaryApplicationForRead(Long id, Long userId, boolean manageAll) {
        FinancePaymentApplicationDO application = getApplicationForRead(id, userId, manageAll);
        assertOrdinaryKind(application);
        return application;
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
        return processParticipantSupport.canReadBill(
                userId, application.getApplicantUserId(), application.getProcessInstanceId());
    }

    @Override
    public PageResult<FinancePaymentApplicationDO> getApplicationPage(FinancePaymentApplicationPageReqVO pageReqVO,
                                                                      Long loginUserId, boolean manageAll) {
        return applicationMapper.selectPage(pageReqVO, (Long) null);
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

        // EXP-87 F4：与 recordPay 共享申请行 FOR UPDATE，杜绝 REJECTED+pay_line 竞态
        FinancePaymentApplicationDO current = applicationMapper.selectByIdForUpdate(appId);
        if (current == null) {
            throw exception(PAYMENT_APPLICATION_NOT_EXISTS);
        }

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
        // 锁内再判 pay_line，与 recordPay 串行
        if (FinancePaymentApplicationStatusEnum.REJECTED.getStatus().equals(normalized)
                && hasPayLines(appId)) {
            throw exception(PAYMENT_APPLICATION_HAS_PAY_LINES);
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
            FinancePaymentApplicationDO again = applicationMapper.selectByIdForUpdate(appId);
            if (again == null) {
                return;
            }
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
        if (reqVO.getActualPayDate() == null || StrUtil.isBlank(reqVO.getPayVoucherUrl())) {
            throw exception(PAYMENT_APPLICATION_CASHIER_FIELDS_REQUIRED);
        }
        if (reqVO.getCompanyBankAccountId() == null) {
            throw exception(PAYMENT_APPLICATION_PAY_ACCOUNT_REQUIRED);
        }
        if (!isAcceptableFileUrl(reqVO.getPayVoucherUrl().trim())) {
            throw exception(PAYMENT_APPLICATION_EVIDENCE_URL_INVALID);
        }

        // EXP-87 F5：悲观锁串行化 remaining 读 + 明细写，防止并发超付
        FinancePaymentApplicationDO application = applicationMapper.selectByIdForUpdate(reqVO.getId());
        if (application == null) {
            throw exception(PAYMENT_APPLICATION_NOT_EXISTS);
        }

        // 幂等键必填（禁止空键多行）
        String idem = trimToNull(reqVO.getIdempotencyKey());
        if (idem == null) {
            throw exception(PAYMENT_APPLICATION_IDEMPOTENCY_KEY_REQUIRED);
        }
        // 幂等优先：同键已落库时，任务可能已 complete，不得先校验 task 导致 TASK_INVALID
        FinancePaymentPayLineDO existingLine =
                payLineMapper.selectByAppAndIdempotencyKey(application.getId(), idem);
        if (existingLine != null) {
            // EXP-87 G3：同键比对规范化指纹（缺省金额归一化为「若无本行时的剩余未付」）；金额始终参与
            assertIdempotentPayLineMatches(existingLine, reqVO, application);
            ensureHeaderEvidenceIfFullyPaid(application, reqVO);
            return;
        }

        if (!FinancePaymentApplicationStatusEnum.WAIT_PAY.getStatus().equals(application.getStatus())
                && !FinancePaymentApplicationStatusEnum.PENDING.getStatus().equals(application.getStatus())) {
            // 已足额且终态：无新键时也视为幂等成功
            BigDecimal paid = sumPayLines(application.getId());
            if (application.getApplyAmount() != null
                    && paid.compareTo(application.getApplyAmount()) == 0
                    && (FinancePaymentApplicationStatusEnum.PAID.getStatus().equals(application.getStatus())
                    || application.getActualPayDate() != null)) {
                return;
            }
            throw exception(PAYMENT_APPLICATION_STATUS_INVALID);
        }

        // F2：新支付须校验办理人/候选人
        Task task = requireCashierTask(reqVO.getTaskId(), application, userId);

        // 账户归属主体：普通付款用单头主体；薪资/税金可用账户所属主体（须在明细主体集合内）
        Long entityForAccount = resolvePayEntityCompanyDeptId(application, reqVO.getCompanyBankAccountId());
        FinanceCompanyBankAccountDO account = companyBankAccountService
                .requireEnabledForEntityCompany(reqVO.getCompanyBankAccountId(), entityForAccount);
        assertAccountCurrencyMatchesApplication(application, account);

        BigDecimal alreadyPaid = sumPayLines(application.getId());
        BigDecimal remaining = application.getApplyAmount().subtract(alreadyPaid);
        if (remaining.compareTo(ZERO) <= 0) {
            // 已足额：幂等办结（任务仍有效时 complete）
            maybeCompleteCashier(application, task, reqVO);
            return;
        }

        BigDecimal payAmount = reqVO.getPayAmount() != null
                ? normalizePayAmount(reqVO.getPayAmount())
                : remaining.setScale(AMOUNT_SCALE, RoundingMode.UNNECESSARY);
        if (payAmount.compareTo(ZERO) <= 0 || payAmount.compareTo(remaining) > 0) {
            throw exception(PAYMENT_APPLICATION_PAY_AMOUNT_INVALID);
        }

        FinancePaymentPayLineDO line = FinancePaymentPayLineDO.builder()
                .paymentApplicationId(application.getId())
                .companyBankAccountId(account.getId())
                .entityCompanyDeptId(account.getEntityCompanyDeptId())
                .accountNameSnapshot(account.getAccountName())
                .bankNameSnapshot(account.getBankName())
                .accountHolderSnapshot(account.getAccountHolder())
                .accountNoSnapshot(account.getAccountNo())
                .accountNoMaskedSnapshot(FinanceCompanyBankAccountService.maskAccountNo(account.getAccountNo()))
                .currencySnapshot(account.getCurrency())
                .payAmount(payAmount)
                .actualPayDate(reqVO.getActualPayDate())
                .payVoucherUrl(reqVO.getPayVoucherUrl().trim())
                .erpVoucherNo(trimToNull(reqVO.getErpVoucherNo()))
                .idempotencyKey(idem)
                .build();
        applyCurrentTenantId(line);
        payLineMapper.insert(line);

        BigDecimal newSum = alreadyPaid.add(payAmount);
        if (newSum.compareTo(application.getApplyAmount()) == 0) {
            // 写申请头证据字段（兼容 F1 / 历史详情展示）
            int updated = applicationMapper.update(null, new UpdateWrapper<FinancePaymentApplicationDO>()
                    .eq("id", application.getId())
                    .in("status",
                            FinancePaymentApplicationStatusEnum.WAIT_PAY.getStatus(),
                            FinancePaymentApplicationStatusEnum.PENDING.getStatus())
                    .eq("process_instance_id", application.getProcessInstanceId())
                    .set("actual_pay_date", reqVO.getActualPayDate())
                    .set("pay_voucher_url", reqVO.getPayVoucherUrl().trim())
                    .set("erp_voucher_no", StrUtil.blankToDefault(trimToNull(reqVO.getErpVoucherNo()), null))
                    .set("materials_status", Boolean.FALSE.equals(reqVO.getMaterialsComplete()) ? "WAIT_INVOICE" : "COMPLETE"));
            if (updated == 0) {
                throw exception(PAYMENT_APPLICATION_STATUS_INVALID);
            }
            boolean complete = reqVO.getCompleteWhenFullyPaid() == null || Boolean.TRUE.equals(reqVO.getCompleteWhenFullyPaid());
            if (Boolean.FALSE.equals(reqVO.getMaterialsComplete())) {
                complete = false;
                processInstanceApi.returnCurrentTaskToStartUserTask(
                        userId, task.getId(), "发票/资料不齐，退回发起人补传");
            }
            if (complete) {
                completeCashierTask(task);
            }
        } else if (newSum.compareTo(application.getApplyAmount()) > 0) {
            throw exception(PAYMENT_APPLICATION_PAY_AMOUNT_INVALID);
        }
        // 未足额：仅落支付明细，不 complete（允许多笔）
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmMaterials(Long id, String taskId, Long userId) {
        FinancePaymentApplicationDO application = applicationMapper.selectByIdForUpdate(id);
        if (application == null) {
            throw exception(PAYMENT_APPLICATION_NOT_EXISTS);
        }
        applicationMapper.update(null, new UpdateWrapper<FinancePaymentApplicationDO>()
                .eq("id", id)
                .set("materials_status", "COMPLETE"));
        if (StrUtil.isNotBlank(taskId)) {
            Task task = requireCashierTask(taskId, application, userId);
            completeCashierTask(task);
        }
    }

    @Override
    public List<FinancePaymentPayLineRespVO> listPayLines(Long paymentApplicationId) {
        return payLineMapper.selectByApplicationId(paymentApplicationId).stream()
                .map(line -> {
                    FinancePaymentPayLineRespVO vo = BeanUtils.toBean(line, FinancePaymentPayLineRespVO.class);
                    // 对外不回传完整账号快照
                    return vo;
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<FinancePaymentSalaryLineRespVO> listSalaryLines(Long paymentApplicationId) {
        return BeanUtils.toBean(salaryLineMapper.selectByApplicationId(paymentApplicationId),
                FinancePaymentSalaryLineRespVO.class);
    }

    @Override
    public List<FinancePaymentTaxLineRespVO> listTaxLines(Long paymentApplicationId) {
        return BeanUtils.toBean(taxLineMapper.selectByApplicationId(paymentApplicationId),
                FinancePaymentTaxLineRespVO.class);
    }

    @Override
    public BigDecimal sumPayLines(Long paymentApplicationId) {
        BigDecimal sum = payLineMapper.sumPayAmountByApplicationId(paymentApplicationId);
        return sum != null ? sum : ZERO;
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

    private void assertCashierEvidencePresent(FinancePaymentApplicationDO application) {
        if (application.getActualPayDate() == null || StrUtil.isBlank(application.getPayVoucherUrl())) {
            throw exception(PAYMENT_APPLICATION_CASHIER_FIELDS_REQUIRED);
        }
        // EXP-87：支付合计须等于批准金额
        if (application.getApplyAmount() != null) {
            BigDecimal paid = sumPayLines(application.getId());
            // 历史单可能无 pay_line，仅头字段有证据时放行
            if (paid.compareTo(ZERO) > 0 && paid.compareTo(application.getApplyAmount()) != 0) {
                throw exception(PAYMENT_APPLICATION_PAY_AMOUNT_INCOMPLETE);
            }
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
        // EXP-87：普通付款新单禁止 SALARY/TAX，请走独立入口
        if (FinancePaymentReasonEnum.SALARY.getCode().equals(reqVO.getPaymentReason())
                || FinancePaymentReasonEnum.TAX.getCode().equals(reqVO.getPaymentReason())) {
            throw exception(PAYMENT_APPLICATION_REASON_SALARY_TAX_FORBIDDEN);
        }
        BigDecimal applyAmount = normalizeApplyAmount(reqVO.getApplyAmount());
        if (CollUtil.isEmpty(reqVO.getEvidenceFileUrls())
                || reqVO.getEvidenceFileUrls().stream().noneMatch(StrUtil::isNotBlank)) {
            throw exception(PAYMENT_APPLICATION_EVIDENCE_REQUIRED);
        }
        validateEvidenceUrls(reqVO.getEvidenceFileUrls());
        if (StrUtil.isBlank(reqVO.getBusinessSettlementTerm())) {
            throw exception(PAYMENT_APPLICATION_FIELD_REQUIRED);
        }
        String payMethod = StrUtil.trimToNull(reqVO.getPayMethod());
        if (payMethod != null) {
            validateDictValue(DICT_PAY_METHOD, payMethod);
        }
        String reason = reqVO.getPaymentReason();
        boolean business = FinancePaymentReasonEnum.BUSINESS.getCode().equals(reason);
        String costProject = null;
        if (business) {
            if (StrUtil.isBlank(reqVO.getCostProject())) {
                throw exception(PAYMENT_APPLICATION_FIELD_REQUIRED);
            }
            costProject = reqVO.getCostProject().trim();
            validateDictValue(DICT_COST_PROJECT, costProject);
        }
        FinanceCustomerCompanyDO payee = customerCompanyService.getEnabledSupplierCompany(reqVO.getPayeeCompanyId());
        String bankName = StrUtil.blankToDefault(trimToNull(reqVO.getPayeeBankName()), payee.getBankName());
        String bankAccount = StrUtil.blankToDefault(trimToNull(reqVO.getPayeeBankAccount()), payee.getBankAccount());
        if (StrUtil.isBlank(bankName) || StrUtil.isBlank(bankAccount)) {
            throw exception(CUSTOMER_COMPANY_SUPPLIER_BANK_REQUIRED);
        }

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
        // 业务付款必须关联已通过的付款业务合同；LEASE 结算只来自租赁前置
        if (FinancePaymentReasonEnum.BUSINESS.getCode().equals(reason) && relatedContractId == null) {
            throw exception(PAYMENT_RELATED_CONTRACT_REQUIRED);
        }
        if (relatedContractId != null) {
            if (!FinancePaymentReasonEnum.BUSINESS.getCode().equals(reason)) {
                throw exception(PAYMENT_RELATED_CONTRACT_REASON_INVALID);
            }
            FinanceContractApplicationDO related = contractApplicationMapper.selectById(relatedContractId);
            if (related == null
                    || Boolean.TRUE.equals(related.getVoided())
                    || !FinanceContractApprovalStatusEnum.APPROVED.getStatus().equals(related.getApprovalStatus())
                    || !"付款业务合同".equals(StrUtil.trim(related.getFileType()))) {
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

        // EXP-73：主体公司必填；名称快照仅服务端生成
        FinanceEntityCompanyResolver.ResolvedCompany entityCompany =
                entityCompanyResolver.requireByDeptId(reqVO.getEntityCompanyDeptId());
        String currency = normalizeCurrency(reqVO.getCurrency());
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
                .entityCompanyDeptId(entityCompany.deptId())
                .entityCompanyName(entityCompany.name())
                .businessSettlementTerm(reqVO.getBusinessSettlementTerm().trim())
                .contractSettlementMethod(settlement)
                .payMethod(payMethod)
                .costProject(costProject)
                .evidenceFileUrls(evidenceJson)
                .specialNote(trimToNull(reqVO.getSpecialNote()))
                .processTitle(title)
                .applicantUserId(applicantUserId)
                .businessStaffUserId(businessStaffSupport.resolve(applicantUserId, reqVO.getBusinessStaffUserId()))
                .applicantDeptId(deptId)
                .build();
    }

    private String startProcess(Long userId, FinancePaymentApplicationDO application,
                                Map<String, List<Long>> startUserSelectAssignees,
                                String processDefinitionKey) {
        return startProcess(userId, application, startUserSelectAssignees, processDefinitionKey, null, null);
    }

    private String startProcess(Long userId, FinancePaymentApplicationDO application,
                                Map<String, List<Long>> startUserSelectAssignees,
                                String processDefinitionKey, Long startCompanyDeptId) {
        return startProcess(userId, application, startUserSelectAssignees, processDefinitionKey, startCompanyDeptId, null);
    }

    private String startProcess(Long userId, FinancePaymentApplicationDO application,
                                Map<String, List<Long>> startUserSelectAssignees,
                                String processDefinitionKey, Long startCompanyDeptId, Long startDeptId) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("paymentApplicationId", application.getId());
        variables.put("applicationNo", application.getApplicationNo());
        variables.put(BpmProcessVariableConstants.BILL_CODE, application.getApplicationNo());
        variables.put("applyAmount", application.getApplyAmount());
        variables.put("payeeName", application.getPayeeName());
        variables.put("paymentReason", application.getPaymentReason());
        variables.put("applicationKind", application.getApplicationKind());
        variables.put("applicantUserId", application.getApplicantUserId());
        variables.put("paymentTiming", application.getPaymentTiming());
        variables.put("currency", application.getCurrency());
        // EXP-73 BPM-1：待办公司列展示业务主体，而非仅任职公司
        if (startCompanyDeptId != null) {
            variables.put("startCompanyDeptId", startCompanyDeptId);
        }
        if (startDeptId != null) {
            variables.put("startDeptId", startDeptId);
        }
        if (application.getEntityCompanyDeptId() != null) {
            variables.put(BpmProcessVariableConstants.COMPANY_ID, application.getEntityCompanyDeptId());
        }
        if (StrUtil.isNotBlank(application.getEntityCompanyName())) {
            variables.put(BpmProcessVariableConstants.COMPANY_NAME, application.getEntityCompanyName());
        }
        // EXP-87 G1：Finance 走可信业务通道 API（服务端派生信任，DTO 无可信标志）
        return processInstanceApi.createProcessInstanceByBusiness(userId,
                        new BpmProcessInstanceCreateReqDTO()
                                .setProcessDefinitionKey(processDefinitionKey)
                                .setBusinessKey(String.valueOf(application.getId()))
                                .setVariables(variables)
                                .setStartUserSelectAssignees(startUserSelectAssignees))
                .getCheckedData();
    }

    /** 交易币种强制 CNY/USD/HKD（无草稿：提交前可改，提交后不可改）。 */
    static String normalizeCurrency(String raw) {
        if (StrUtil.isBlank(raw)) {
            throw exception(PAYMENT_APPLICATION_CURRENCY_INVALID);
        }
        String currency = raw.trim().toUpperCase(java.util.Locale.ROOT);
        if (!SUPPORTED_CURRENCIES.contains(currency)) {
            throw exception(PAYMENT_APPLICATION_CURRENCY_INVALID);
        }
        return currency;
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

    private static void applyCurrentTenantId(FinancePaymentPayLineDO line) {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId != null) {
            line.setTenantId(tenantId);
        }
    }

    private void maybeCompleteCashier(FinancePaymentApplicationDO application, Task task,
                                      FinancePaymentRecordPayReqVO reqVO) {
        ensureHeaderEvidenceIfFullyPaid(application, reqVO);
        BigDecimal sum = sumPayLines(application.getId());
        if (application.getApplyAmount() != null && sum.compareTo(application.getApplyAmount()) == 0) {
            boolean complete = reqVO.getCompleteWhenFullyPaid() == null
                    || Boolean.TRUE.equals(reqVO.getCompleteWhenFullyPaid());
            if (complete && task != null) {
                completeCashierTask(task);
            }
        }
    }

    /**
     * EXP-87 G3：同 idempotencyKey 命中已有 pay_line 时，比对规范化指纹。
     * 指纹：账户 / 金额 / 支付日 / 凭证 URL / ERP。
     * 缺省 payAmount 归一化为「排除本幂等行后的剩余未付金额」（契约语义），金额始终参与比对。
     */
    private void assertIdempotentPayLineMatches(FinancePaymentPayLineDO existing,
                                                FinancePaymentRecordPayReqVO reqVO,
                                                FinancePaymentApplicationDO application) {
        if (!Objects.equals(existing.getCompanyBankAccountId(), reqVO.getCompanyBankAccountId())) {
            throw exception(PAYMENT_APPLICATION_IDEMPOTENCY_CONFLICT);
        }
        if (!Objects.equals(existing.getActualPayDate(), reqVO.getActualPayDate())) {
            throw exception(PAYMENT_APPLICATION_IDEMPOTENCY_CONFLICT);
        }
        String reqVoucher = reqVO.getPayVoucherUrl() != null ? reqVO.getPayVoucherUrl().trim() : null;
        String existingVoucher = existing.getPayVoucherUrl() != null ? existing.getPayVoucherUrl().trim() : null;
        if (!Objects.equals(existingVoucher, reqVoucher)) {
            throw exception(PAYMENT_APPLICATION_IDEMPOTENCY_CONFLICT);
        }
        String reqErp = trimToNull(reqVO.getErpVoucherNo());
        String existingErp = trimToNull(existing.getErpVoucherNo());
        if (!Objects.equals(existingErp, reqErp)) {
            throw exception(PAYMENT_APPLICATION_IDEMPOTENCY_CONFLICT);
        }
        BigDecimal existingAmount = existing.getPayAmount() != null
                ? existing.getPayAmount().setScale(AMOUNT_SCALE, RoundingMode.HALF_UP)
                : null;
        BigDecimal resolvedReqAmount = resolveIdempotentPayAmount(reqVO, application, existing);
        if (existingAmount == null || resolvedReqAmount.compareTo(existingAmount) != 0) {
            throw exception(PAYMENT_APPLICATION_IDEMPOTENCY_CONFLICT);
        }
    }

    /**
     * 归一化幂等比对金额：显式 payAmount 用 normalize；缺省则 = 申请金额 −（全量已付 − 本行金额）。
     */
    private BigDecimal resolveIdempotentPayAmount(FinancePaymentRecordPayReqVO reqVO,
                                                  FinancePaymentApplicationDO application,
                                                  FinancePaymentPayLineDO existing) {
        if (reqVO.getPayAmount() != null) {
            return normalizePayAmount(reqVO.getPayAmount());
        }
        BigDecimal apply = application.getApplyAmount() != null
                ? application.getApplyAmount().setScale(AMOUNT_SCALE, RoundingMode.HALF_UP)
                : ZERO;
        BigDecimal totalPaid = sumPayLines(application.getId());
        BigDecimal existingAmt = existing.getPayAmount() != null
                ? existing.getPayAmount().setScale(AMOUNT_SCALE, RoundingMode.HALF_UP)
                : ZERO;
        BigDecimal otherPaid = totalPaid.subtract(existingAmt);
        if (otherPaid.compareTo(ZERO) < 0) {
            otherPaid = ZERO;
        }
        BigDecimal remainingIfThisAbsent = apply.subtract(otherPaid);
        if (remainingIfThisAbsent.compareTo(ZERO) < 0) {
            remainingIfThisAbsent = ZERO;
        }
        return remainingIfThisAbsent.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
    }

    /** 足额时补写头证据；幂等重试不依赖 task 是否仍 active。 */
    private void ensureHeaderEvidenceIfFullyPaid(FinancePaymentApplicationDO application,
                                                 FinancePaymentRecordPayReqVO reqVO) {
        BigDecimal sum = sumPayLines(application.getId());
        if (application.getApplyAmount() == null || sum.compareTo(application.getApplyAmount()) != 0) {
            return;
        }
        if (application.getActualPayDate() == null || StrUtil.isBlank(application.getPayVoucherUrl())) {
            applicationMapper.update(null, new UpdateWrapper<FinancePaymentApplicationDO>()
                    .eq("id", application.getId())
                    .set("actual_pay_date", reqVO.getActualPayDate())
                    .set("pay_voucher_url", reqVO.getPayVoucherUrl().trim())
                    .set("erp_voucher_no", StrUtil.blankToDefault(trimToNull(reqVO.getErpVoucherNo()), null))
                    .set("materials_status", Boolean.FALSE.equals(reqVO.getMaterialsComplete()) ? "WAIT_INVOICE" : "COMPLETE"));
        }
    }

    private static void assertAccountCurrencyMatchesApplication(FinancePaymentApplicationDO application,
                                                                FinanceCompanyBankAccountDO account) {
        if (application.getCurrency() == null || account.getCurrency() == null) {
            return;
        }
        if (!application.getCurrency().trim().equalsIgnoreCase(account.getCurrency().trim())) {
            throw exception(PAYMENT_ACCOUNT_CURRENCY_MISMATCH);
        }
    }

    private static void assertOrdinaryKind(FinancePaymentApplicationDO application) {
        String kind = StrUtil.blankToDefault(application.getApplicationKind(),
                FinancePaymentApplicationKindEnum.ORDINARY.getCode());
        if (!FinancePaymentApplicationKindEnum.ORDINARY.getCode().equals(kind)) {
            throw exception(PAYMENT_APPLICATION_KIND_INVALID);
        }
    }

    private void assertNoPayLines(Long paymentApplicationId) {
        if (hasPayLines(paymentApplicationId)) {
            throw exception(PAYMENT_APPLICATION_HAS_PAY_LINES);
        }
    }

    private boolean hasPayLines(Long paymentApplicationId) {
        List<FinancePaymentPayLineDO> lines = payLineMapper.selectByApplicationId(paymentApplicationId);
        return CollUtil.isNotEmpty(lines);
    }

    private void completeCashierTask(Task task) {
        TaskService taskService = taskServiceProvider.getIfAvailable();
        if (taskService == null) {
            throw exception(PAYMENT_APPLICATION_TASK_INVALID);
        }
        taskService.complete(task.getId());
    }

    /**
     * 解析本笔支付使用的主体公司：
     * - 普通付款：必须与申请单头主体一致；
     * - 薪资/税金：账户所属主体须出现在对应明细中。
     */
    private Long resolvePayEntityCompanyDeptId(FinancePaymentApplicationDO application, Long companyBankAccountId) {
        FinanceCompanyBankAccountDO account = companyBankAccountService.get(companyBankAccountId);
        if (account == null) {
            throw exception(COMPANY_BANK_ACCOUNT_NOT_EXISTS);
        }
        String kind = StrUtil.blankToDefault(application.getApplicationKind(),
                FinancePaymentApplicationKindEnum.ORDINARY.getCode());
        if (FinancePaymentApplicationKindEnum.ORDINARY.getCode().equals(kind)) {
            return application.getEntityCompanyDeptId();
        }
        if (FinancePaymentApplicationKindEnum.SALARY.getCode().equals(kind)) {
            boolean hit = salaryLineMapper.selectByApplicationId(application.getId()).stream()
                    .anyMatch(l -> Objects.equals(l.getEntityCompanyDeptId(), account.getEntityCompanyDeptId()));
            if (!hit) {
                throw exception(COMPANY_BANK_ACCOUNT_ENTITY_MISMATCH);
            }
            return account.getEntityCompanyDeptId();
        }
        if (FinancePaymentApplicationKindEnum.TAX.getCode().equals(kind)) {
            boolean hit = taxLineMapper.selectByApplicationId(application.getId()).stream()
                    .anyMatch(l -> Objects.equals(l.getEntityCompanyDeptId(), account.getEntityCompanyDeptId()));
            if (!hit) {
                throw exception(COMPANY_BANK_ACCOUNT_ENTITY_MISMATCH);
            }
            return account.getEntityCompanyDeptId();
        }
        return application.getEntityCompanyDeptId();
    }

    private static BigDecimal normalizePayAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(ZERO) <= 0) {
            throw exception(PAYMENT_APPLICATION_PAY_AMOUNT_INVALID);
        }
        if (amount.scale() > AMOUNT_SCALE) {
            throw exception(PAYMENT_APPLICATION_PAY_AMOUNT_INVALID);
        }
        return amount.setScale(AMOUNT_SCALE, RoundingMode.UNNECESSARY);
    }

    private static String requirePeriod(String periodLabel) {
        if (StrUtil.isBlank(periodLabel)) {
            throw exception(PAYMENT_APPLICATION_FIELD_REQUIRED);
        }
        return periodLabel.trim();
    }

    private static BigDecimal nonNeg(BigDecimal amount) {
        if (amount == null) {
            return ZERO;
        }
        if (amount.compareTo(ZERO) < 0) {
            throw exception(PAYMENT_APPLICATION_LINES_INVALID);
        }
        if (amount.scale() > AMOUNT_SCALE) {
            throw exception(PAYMENT_APPLICATION_LINES_INVALID);
        }
        return amount.setScale(AMOUNT_SCALE, RoundingMode.UNNECESSARY);
    }

    private PreparedLines prepareSalaryLines(List<FinancePaymentSalaryLineReqVO> lines, String currency) {
        if (CollUtil.isEmpty(lines)) {
            throw exception(PAYMENT_APPLICATION_LINES_INVALID);
        }
        List<FinancePaymentSalaryLineDO> result = new java.util.ArrayList<>();
        BigDecimal total = ZERO;
        Long primaryDeptId = null;
        String primaryName = null;
        int sort = 0;
        for (FinancePaymentSalaryLineReqVO line : lines) {
            FinanceEntityCompanyResolver.ResolvedCompany company =
                    entityCompanyResolver.requireByDeptId(line.getEntityCompanyDeptId());
            FinanceCompanyBankAccountDO account = companyBankAccountService
                    .requireEnabledForEntityCompany(line.getCompanyBankAccountId(), company.deptId());
            BigDecimal net = nonNeg(line.getNetSalaryAmount());
            BigDecimal tax = nonNeg(line.getPersonalTaxAmount());
            BigDecimal social = nonNeg(line.getSocialInsuranceAmount());
            BigDecimal housing = nonNeg(line.getHousingFundAmount());
            BigDecimal lineTotal = net.add(tax).add(social).add(housing);
            if (lineTotal.compareTo(ZERO) <= 0) {
                throw exception(PAYMENT_APPLICATION_LINES_INVALID);
            }
            result.add(FinancePaymentSalaryLineDO.builder()
                    .entityCompanyDeptId(company.deptId())
                    .entityCompanyName(company.name())
                    .companyBankAccountId(account.getId())
                    .accountNameSnapshot(account.getAccountName())
                    .bankNameSnapshot(account.getBankName())
                    .accountNoMaskedSnapshot(FinanceCompanyBankAccountService.maskAccountNo(account.getAccountNo()))
                    .netSalaryAmount(net)
                    .personalTaxAmount(tax)
                    .socialInsuranceAmount(social)
                    .housingFundAmount(housing)
                    .currency(currency)
                    .lineTotal(lineTotal)
                    .sort(sort++)
                    .build());
            total = total.add(lineTotal);
            if (primaryDeptId == null) {
                primaryDeptId = company.deptId();
                primaryName = company.name();
            }
        }
        return new PreparedLines(total, primaryDeptId, primaryName, result, null);
    }

    private PreparedLines prepareTaxLines(List<FinancePaymentTaxLineReqVO> lines, String currency) {
        if (CollUtil.isEmpty(lines)) {
            throw exception(PAYMENT_APPLICATION_LINES_INVALID);
        }
        List<FinancePaymentTaxLineDO> result = new java.util.ArrayList<>();
        BigDecimal total = ZERO;
        Long primaryDeptId = null;
        String primaryName = null;
        int sort = 0;
        for (FinancePaymentTaxLineReqVO line : lines) {
            FinanceEntityCompanyResolver.ResolvedCompany company =
                    entityCompanyResolver.requireByDeptId(line.getEntityCompanyDeptId());
            FinanceCompanyBankAccountDO account = companyBankAccountService
                    .requireEnabledForEntityCompany(line.getCompanyBankAccountId(), company.deptId());
            BigDecimal vat = nonNeg(line.getVatAmount());
            BigDecimal surcharge = nonNeg(line.getSurchargeAmount());
            BigDecimal stamp = nonNeg(line.getStampTaxAmount());
            BigDecimal cit = nonNeg(line.getCitAmount());
            BigDecimal lineTotal = vat.add(surcharge).add(stamp).add(cit);
            if (lineTotal.compareTo(ZERO) <= 0) {
                throw exception(PAYMENT_APPLICATION_LINES_INVALID);
            }
            result.add(FinancePaymentTaxLineDO.builder()
                    .entityCompanyDeptId(company.deptId())
                    .entityCompanyName(company.name())
                    .companyBankAccountId(account.getId())
                    .accountNameSnapshot(account.getAccountName())
                    .bankNameSnapshot(account.getBankName())
                    .accountNoMaskedSnapshot(FinanceCompanyBankAccountService.maskAccountNo(account.getAccountNo()))
                    .vatAmount(vat)
                    .surchargeAmount(surcharge)
                    .stampTaxAmount(stamp)
                    .citAmount(cit)
                    .currency(currency)
                    .lineTotal(lineTotal)
                    .sort(sort++)
                    .build());
            total = total.add(lineTotal);
            if (primaryDeptId == null) {
                primaryDeptId = company.deptId();
                primaryName = company.name();
            }
        }
        return new PreparedLines(total, primaryDeptId, primaryName, null, result);
    }

    private void insertSalaryLines(Long appId, List<FinancePaymentSalaryLineDO> lines) {
        if (lines == null) {
            return;
        }
        for (FinancePaymentSalaryLineDO line : lines) {
            line.setPaymentApplicationId(appId);
            Long tenantId = TenantContextHolder.getTenantId();
            if (tenantId != null) {
                line.setTenantId(tenantId);
            }
            salaryLineMapper.insert(line);
        }
    }

    private void insertTaxLines(Long appId, List<FinancePaymentTaxLineDO> lines) {
        if (lines == null) {
            return;
        }
        for (FinancePaymentTaxLineDO line : lines) {
            line.setPaymentApplicationId(appId);
            Long tenantId = TenantContextHolder.getTenantId();
            if (tenantId != null) {
                line.setTenantId(tenantId);
            }
            taxLineMapper.insert(line);
        }
    }

    private record PreparedLines(BigDecimal applyAmount,
                                 Long primaryEntityDeptId,
                                 String primaryEntityName,
                                 List<FinancePaymentSalaryLineDO> salaryLines,
                                 List<FinancePaymentTaxLineDO> taxLines) {
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
        Long entityCompanyDeptId;
        String entityCompanyName;
        String businessSettlementTerm;
        String contractSettlementMethod;
        String payMethod;
        String costProject;
        String evidenceFileUrls;
        String specialNote;
        String processTitle;
        Long applicantUserId;
        Long businessStaffUserId;
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
                    .entityCompanyDeptId(entityCompanyDeptId)
                    .entityCompanyName(entityCompanyName)
                    .businessSettlementTerm(businessSettlementTerm)
                    .contractSettlementMethod(contractSettlementMethod)
                    .payMethod(payMethod)
                    .costProject(costProject)
                    .evidenceFileUrls(evidenceFileUrls)
                    .specialNote(specialNote)
                    .processTitle(processTitle)
                    .applicantUserId(applicantUserId)
                    .businessStaffUserId(businessStaffUserId)
                    .applicantDeptId(applicantDeptId);
        }
    }

}
