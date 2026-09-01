package cn.iocoder.yudao.module.finance.service.expense;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.yudao.module.finance.controller.admin.expense.vo.FinanceExpenseApproveReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.expense.vo.FinanceExpenseRecordPayReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.expense.vo.FinanceExpenseReimbursementCreateReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.expense.vo.FinanceExpenseReimbursementLineReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.expense.vo.FinanceExpenseReimbursementPageReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.expense.vo.FinanceExpenseReimbursementRespVO;
import cn.iocoder.yudao.module.bpm.enums.BpmProcessVariableConstants;
import cn.iocoder.yudao.module.finance.dal.dataobject.expense.FinanceExpenseReimbursementDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.expense.FinanceExpenseReimbursementLineDO;
import cn.iocoder.yudao.module.finance.dal.mysql.expense.FinanceExpenseReimbursementLineMapper;
import cn.iocoder.yudao.module.finance.dal.mysql.expense.FinanceExpenseReimbursementMapper;
import cn.iocoder.yudao.module.finance.dal.redis.no.FinanceExpenseReimbursementNoRedisDAO;
import cn.iocoder.yudao.module.finance.framework.rpc.FinanceBpmProcessInstanceApi;
import cn.iocoder.yudao.module.finance.framework.security.FinanceProcessParticipantSupport;
import jakarta.annotation.Resource;
import cn.iocoder.yudao.module.finance.service.companyaccount.FinanceCompanyBankAccountService;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import org.flowable.engine.HistoryService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.EXPENSE_REIMBURSEMENT_ACCESS_DENIED;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.EXPENSE_REIMBURSEMENT_APPROVED_AMOUNT_INVALID;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.EXPENSE_REIMBURSEMENT_ATTACHMENT_URL_INVALID;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.EXPENSE_REIMBURSEMENT_CASHIER_FIELDS_REQUIRED;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.EXPENSE_REIMBURSEMENT_PAY_ACCOUNT_REQUIRED;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.EXPENSE_REIMBURSEMENT_STATUS_INVALID;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.EXPENSE_REIMBURSEMENT_AMOUNT_INVALID;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.EXPENSE_REIMBURSEMENT_DEPT_REQUIRED;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.EXPENSE_REIMBURSEMENT_FIELD_REQUIRED;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.EXPENSE_REIMBURSEMENT_LINES_EMPTY;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.EXPENSE_REIMBURSEMENT_LINE_KIND_MISMATCH;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.EXPENSE_REIMBURSEMENT_NOT_EXISTS;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.EXPENSE_REIMBURSEMENT_PERIOD_INVALID;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.EXPENSE_REIMBURSEMENT_INVOICE_FORBIDDEN;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.EXPENSE_REIMBURSEMENT_INVOICE_REQUIRED;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.EXPENSE_REIMBURSEMENT_PREDOC_FORBIDDEN;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.EXPENSE_REIMBURSEMENT_PREDOC_INVALID;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.EXPENSE_REIMBURSEMENT_PREDOC_OCCUPIED;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.EXPENSE_REIMBURSEMENT_PREDOC_REQUIRED;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.EXPENSE_REIMBURSEMENT_INVOICE_USED;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.EXPENSE_REIMBURSEMENT_EXTRA_ATTACHMENTS_EXCEED;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.EXPENSE_REIMBURSEMENT_OVER_LIMIT_REASON_REQUIRED;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.EXPENSE_REIMBURSEMENT_STAY_TIER_REQUIRED;

@Service
@Validated
public class FinanceExpenseReimbursementServiceImpl implements FinanceExpenseReimbursementService {

    private final FinanceExpenseReimbursementMapper mapper;
    private final FinanceExpenseReimbursementLineMapper lineMapper;
    private final AdminUserApi adminUserApi;
    private final FinanceBpmProcessInstanceApi processInstanceApi;
    private final FinanceCompanyBankAccountService companyBankAccountService;
    private final FinanceExpensePredocService predocService;
    private final DeptApi deptApi;
    private final ObjectProvider<TaskService> taskServiceProvider;
    @Resource
    private FinanceProcessParticipantSupport processParticipantSupport;
    private final FinanceExpenseReimbursementNoRedisDAO applicationNoRedisDAO;
    @Resource
    private ObjectProvider<HistoryService> historyServiceProvider;

    public FinanceExpenseReimbursementServiceImpl(FinanceExpenseReimbursementMapper mapper,
                                                  FinanceExpenseReimbursementLineMapper lineMapper,
                                                  AdminUserApi adminUserApi,
                                                  FinanceBpmProcessInstanceApi processInstanceApi,
                                                  FinanceCompanyBankAccountService companyBankAccountService,
                                                  FinanceExpensePredocService predocService,
                                                  DeptApi deptApi,
                                                  ObjectProvider<TaskService> taskServiceProvider,
                                                  FinanceExpenseReimbursementNoRedisDAO applicationNoRedisDAO) {
        this.mapper = mapper;
        this.lineMapper = lineMapper;
        this.adminUserApi = adminUserApi;
        this.processInstanceApi = processInstanceApi;
        this.companyBankAccountService = companyBankAccountService;
        this.predocService = predocService;
        this.deptApi = deptApi;
        this.taskServiceProvider = taskServiceProvider;
        this.applicationNoRedisDAO = applicationNoRedisDAO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(FinanceExpenseReimbursementCreateReqVO reqVO, Long userId) {
        return createInternal(reqVO, userId,
                FinanceExpenseReimbursementDO.MODE_WITH_INVOICE, PROCESS_KEY);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createNoInvoice(FinanceExpenseReimbursementCreateReqVO reqVO, Long userId) {
        return createInternal(reqVO, userId,
                FinanceExpenseReimbursementDO.MODE_NO_INVOICE, PROCESS_KEY_NO_INVOICE);
    }

    private Long createInternal(FinanceExpenseReimbursementCreateReqVO reqVO, Long userId,
                                String invoiceMode, String processKey) {
        if (reqVO.getLines() == null || reqVO.getLines().isEmpty()) {
            throw exception(EXPENSE_REIMBURSEMENT_LINES_EMPTY);
        }
        if (StrUtil.isBlank(reqVO.getPeriodLabel()) || !reqVO.getPeriodLabel().trim().matches("\\d{4}-\\d{2}")) {
            throw exception(EXPENSE_REIMBURSEMENT_PERIOD_INVALID);
        }
        if (StrUtil.isBlank(reqVO.getPayeeAccountName())
                || StrUtil.isBlank(reqVO.getPayeeBankName())
                || StrUtil.isBlank(reqVO.getPayeeAccountNo())) {
            throw exception(EXPENSE_REIMBURSEMENT_FIELD_REQUIRED);
        }
        Long actualUserId = reqVO.getActualUserId() != null ? reqVO.getActualUserId() : userId;
        boolean proxy = Boolean.TRUE.equals(reqVO.getProxyTicket());
        String expectKind = proxy
                ? FinanceExpenseReimbursementLineDO.KIND_PROXY
                : FinanceExpenseReimbursementLineDO.KIND_NORMAL;
        BigDecimal apply = BigDecimal.ZERO;
        int i = 0;
        for (FinanceExpenseReimbursementLineReqVO line : reqVO.getLines()) {
            if (!expectKind.equalsIgnoreCase(line.getLineKind())) {
                throw exception(EXPENSE_REIMBURSEMENT_LINE_KIND_MISMATCH);
            }
            if (line.getAmount() == null || line.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw exception(EXPENSE_REIMBURSEMENT_AMOUNT_INVALID);
            }
            validateInvoiceAndPredoc(line, proxy, invoiceMode, actualUserId);
            validateStayStandard(line, actualUserId);
            if (StrUtil.isNotBlank(line.getInvoiceNo()) && invoiceNoUsed(line.getInvoiceNo())) {
                throw exception(EXPENSE_REIMBURSEMENT_INVOICE_USED);
            }
            apply = apply.add(line.getAmount());
            i++;
        }
        assertPredocsNotOccupied(reqVO.getLines());
        if (apply.compareTo(BigDecimal.ZERO) <= 0) {
            throw exception(EXPENSE_REIMBURSEMENT_AMOUNT_INVALID);
        }
        apply = apply.setScale(2, RoundingMode.HALF_UP);
        List<String> extraAttachments = normalizeExtraAttachments(reqVO.getExtraAttachments());
        AdminUserRespDTO user = requireUser(actualUserId);
        if (user.getDeptId() == null) {
            throw exception(EXPENSE_REIMBURSEMENT_DEPT_REQUIRED);
        }
        DeptRespDTO company = resolveCompany(user.getDeptId());
        String nickname = StrUtil.blankToDefault(user.getNickname(), String.valueOf(actualUserId));
        String title = "【报销】-" + nickname + "-" + reqVO.getPeriodLabel().trim() + "-" + apply.toPlainString();

        String applicationNo = applicationNoRedisDAO.generate(LocalDate.now());
        FinanceExpenseReimbursementDO header = FinanceExpenseReimbursementDO.builder()
                .applicationNo(applicationNo)
                .processTitle(title)
                .periodLabel(reqVO.getPeriodLabel().trim())
                .payeeAccountName(reqVO.getPayeeAccountName().trim())
                .payeeBankName(reqVO.getPayeeBankName().trim())
                .payeeAccountNo(reqVO.getPayeeAccountNo().trim())
                .applyAmount(apply)
                .proxyTicket(proxy)
                .invoiceMode(invoiceMode)
                .processKey(processKey)
                .status(FinanceExpenseReimbursementDO.STATUS_PENDING)
                .applicantUserId(userId)
                .actualUserId(actualUserId)
                .applicantDeptId(user.getDeptId())
                .entityCompanyDeptId(company == null ? null : company.getId())
                .entityCompanyName(company == null ? null : company.getName())
                .applyDate(LocalDate.now())
                .extraAttachments(extraAttachments.isEmpty() ? null : String.join(",", extraAttachments))
                .build();
        mapper.insert(header);

        int sort = 0;
        for (FinanceExpenseReimbursementLineReqVO line : reqVO.getLines()) {
            lineMapper.insert(FinanceExpenseReimbursementLineDO.builder()
                    .reimbursementId(header.getId())
                    .lineKind(expectKind)
                    .category(line.getCategory().trim())
                    .invoiceType(line.getInvoiceType())
                    .subItem(line.getSubItem())
                    .feeDate(line.getFeeDate())
                    .amount(line.getAmount().setScale(2, RoundingMode.HALF_UP))
                    .taxAmount(line.getTaxAmount() == null ? null
                            : line.getTaxAmount().setScale(2, RoundingMode.HALF_UP))
                    .attachments(line.getAttachments() == null ? null : String.join(",", line.getAttachments()))
                    .invoiceFileUrl(line.getInvoiceFileUrl())
                    .invoiceNo(line.getInvoiceNo())
                    .predocType(line.getPredocType())
                    .predocProcessInstanceId(line.getPredocProcessInstanceId())
                    .remark(line.getRemark())
                    .stayCityTier(line.getStayCityTier())
                    .overLimitReason(line.getOverLimitReason())
                    .sort(sort++)
                    .build());
        }

        Map<String, Object> vars = new HashMap<>();
        vars.put(BpmProcessVariableConstants.BILL_CODE, applicationNo);
        vars.put("applicationNo", applicationNo);
        // 网关条件 ${applyAmount > 300} 需要数字类型；BigDecimal 在 JUEL 里比较会失败
        vars.put("applyAmount", apply.doubleValue());
        vars.put("periodLabel", header.getPeriodLabel());
        if (reqVO.getStartCompanyDeptId() != null) {
            vars.put("startCompanyDeptId", reqVO.getStartCompanyDeptId());
        }
        if (reqVO.getStartDeptId() != null) {
            vars.put("startDeptId", reqVO.getStartDeptId());
        }
        String processInstanceId = processInstanceApi.createProcessInstance(userId,
                        new BpmProcessInstanceCreateReqDTO()
                                .setProcessDefinitionKey(processKey)
                                .setVariables(vars)
                                .setBusinessKey(String.valueOf(header.getId())))
                .getCheckedData();
        mapper.updateById(FinanceExpenseReimbursementDO.builder()
                .id(header.getId())
                .processInstanceId(processInstanceId)
                .build());
        return header.getId();
    }

    @Override
    public FinanceExpenseReimbursementRespVO get(Long id, Long userId, boolean canQueryAll) {
        FinanceExpenseReimbursementDO header = mapper.selectById(id);
        if (header == null) {
            throw exception(EXPENSE_REIMBURSEMENT_NOT_EXISTS);
        }
        assertCanRead(header, userId, canQueryAll);
        return toResp(header, canQueryAll);
    }

    @Override
    public PageResult<FinanceExpenseReimbursementRespVO> getPage(FinanceExpenseReimbursementPageReqVO reqVO,
                                                                 Long userId, boolean canQueryAll) {
        PageResult<FinanceExpenseReimbursementDO> page = mapper.selectPage(reqVO, (Long) null);
        Set<String> ended = listEndedProcessInstanceIds(page.getList().stream()
                .map(FinanceExpenseReimbursementDO::getProcessInstanceId)
                .filter(StrUtil::isNotBlank)
                .collect(java.util.stream.Collectors.toSet()));
        List<FinanceExpenseReimbursementRespVO> list = new ArrayList<>();
        for (FinanceExpenseReimbursementDO header : page.getList()) {
            FinanceExpenseReimbursementRespVO vo = toResp(header, canQueryAll);
            vo.setProcessEnded(isProcessEnded(header.getProcessInstanceId(), ended));
            list.add(vo);
        }
        return new PageResult<>(list, page.getTotal());
    }

    private FinanceExpenseReimbursementRespVO toResp(FinanceExpenseReimbursementDO header, boolean revealAccount) {
        FinanceExpenseReimbursementRespVO vo = new FinanceExpenseReimbursementRespVO();
        vo.setId(header.getId());
        vo.setApplicationNo(header.getApplicationNo());
        vo.setProcessTitle(header.getProcessTitle());
        vo.setPeriodLabel(header.getPeriodLabel());
        vo.setPayeeAccountName(header.getPayeeAccountName());
        vo.setPayeeBankName(header.getPayeeBankName());
        vo.setPayeeAccountNo(revealAccount
                ? header.getPayeeAccountNo()
                : FinanceCompanyBankAccountService.maskAccountNo(header.getPayeeAccountNo()));
        vo.setApplyAmount(header.getApplyAmount());
        vo.setApprovedAmount(header.getApprovedAmount());
        vo.setProxyTicket(header.getProxyTicket());
        vo.setStatus(header.getStatus());
        vo.setProcessInstanceId(header.getProcessInstanceId());
        vo.setProcessEnded(isProcessEnded(header.getProcessInstanceId()));
        vo.setApplicantUserId(header.getApplicantUserId());
        vo.setActualUserId(header.getActualUserId());
        vo.setApplicantDeptId(header.getApplicantDeptId());
        vo.setEntityCompanyDeptId(header.getEntityCompanyDeptId());
        vo.setEntityCompanyName(header.getEntityCompanyName());
        vo.setApplyDate(header.getApplyDate());
        vo.setFinanceComment(header.getFinanceComment());
        vo.setActualPayDate(header.getActualPayDate());
        vo.setCompanyBankAccountId(header.getCompanyBankAccountId());
        vo.setPayVoucherUrl(header.getPayVoucherUrl());
        vo.setExtraAttachments(splitCsv(header.getExtraAttachments()));
        List<FinanceExpenseReimbursementLineReqVO> lines = new ArrayList<>();
        for (FinanceExpenseReimbursementLineDO line : lineMapper.selectByReimbursementId(header.getId())) {
            FinanceExpenseReimbursementLineReqVO lv = new FinanceExpenseReimbursementLineReqVO();
            lv.setLineKind(line.getLineKind());
            lv.setCategory(line.getCategory());
            lv.setFeeDate(line.getFeeDate());
            lv.setAmount(line.getAmount());
            lv.setTaxAmount(line.getTaxAmount());
            lv.setInvoiceType(line.getInvoiceType());
            lv.setInvoiceNo(line.getInvoiceNo());
            lv.setSubItem(line.getSubItem());
            lv.setRemark(line.getRemark());
            lv.setStayCityTier(line.getStayCityTier());
            lv.setOverLimitReason(line.getOverLimitReason());
            lv.setInvoiceFileUrl(line.getInvoiceFileUrl());
            lv.setPredocType(line.getPredocType());
            lv.setPredocProcessInstanceId(line.getPredocProcessInstanceId());
            lv.setPredocBillId(predocService.resolveBillPk(line.getPredocType(), line.getPredocProcessInstanceId()));
            lines.add(lv);
        }
        vo.setLines(lines);
        return vo;
    }

    private void assertCanRead(FinanceExpenseReimbursementDO header, Long userId, boolean canQueryAll) {
        if (canQueryAll || processParticipantSupport.canReadBill(
                userId, header.getApplicantUserId(), header.getProcessInstanceId())) {
            return;
        }
        if (predocService.isProcessAssignee(header.getProcessInstanceId(), userId)) {
            return;
        }
        throw exception(EXPENSE_REIMBURSEMENT_ACCESS_DENIED);
    }

    private boolean isActiveTaskCandidateOrAssignee(FinanceExpenseReimbursementDO header, Long userId) {
        if (userId == null || StrUtil.isBlank(header.getProcessInstanceId())) {
            return false;
        }
        TaskService taskService = taskServiceProvider.getIfAvailable();
        if (taskService == null) {
            return false;
        }
        return taskService.createTaskQuery()
                .processInstanceId(header.getProcessInstanceId())
                .taskCandidateOrAssigned(String.valueOf(userId))
                .count() > 0;
    }

    private boolean isProcessEnded(String processInstanceId) {
        if (StrUtil.isBlank(processInstanceId)) {
            return true;
        }
        return listEndedProcessInstanceIds(List.of(processInstanceId)).contains(processInstanceId);
    }

    private boolean isProcessEnded(String processInstanceId, Set<String> ended) {
        if (StrUtil.isBlank(processInstanceId)) {
            return true;
        }
        return ended.contains(processInstanceId);
    }

    private Set<String> listEndedProcessInstanceIds(Collection<String> processInstanceIds) {
        if (processInstanceIds == null || processInstanceIds.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> ids = new HashSet<>();
        for (String id : processInstanceIds) {
            if (StrUtil.isNotBlank(id)) {
                ids.add(id);
            }
        }
        if (ids.isEmpty()) {
            return Collections.emptySet();
        }
        HistoryService historyService = historyServiceProvider.getIfAvailable();
        if (historyService == null) {
            return Collections.emptySet();
        }
        List<HistoricProcessInstance> list = historyService.createHistoricProcessInstanceQuery()
                .processInstanceIds(ids)
                .finished()
                .list();
        if (list == null || list.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> ended = new HashSet<>();
        for (HistoricProcessInstance hi : list) {
            if (hi != null && StrUtil.isNotBlank(hi.getId()) && hi.getEndTime() != null) {
                ended.add(hi.getId());
            }
        }
        return ended;
    }

    private void completeLeftoverCashierTask(FinanceExpenseReimbursementDO header) {
        if (StrUtil.isBlank(header.getProcessInstanceId())) {
            return;
        }
        TaskService taskService = taskServiceProvider.getIfAvailable();
        if (taskService == null) {
            return;
        }
        org.flowable.task.api.Task task = taskService.createTaskQuery()
                .processInstanceId(header.getProcessInstanceId())
                .taskDefinitionKey("taskCashier")
                .singleResult();
        if (task != null) {
            taskService.complete(task.getId());
        }
    }

    @Override
    public void approve(FinanceExpenseApproveReqVO reqVO, Long userId) {
        FinanceExpenseReimbursementDO header = mapper.selectById(reqVO.getId());
        if (header == null) {
            throw exception(EXPENSE_REIMBURSEMENT_NOT_EXISTS);
        }
        if (!FinanceExpenseReimbursementDO.STATUS_PENDING.equals(header.getStatus())) {
            throw exception(EXPENSE_REIMBURSEMENT_STATUS_INVALID);
        }
        if (reqVO.getApprovedAmount() == null
                || reqVO.getApprovedAmount().compareTo(BigDecimal.ZERO) <= 0
                || reqVO.getApprovedAmount().compareTo(header.getApplyAmount()) > 0) {
            throw exception(EXPENSE_REIMBURSEMENT_APPROVED_AMOUNT_INVALID);
        }
        mapper.updateById(FinanceExpenseReimbursementDO.builder()
                .id(header.getId())
                .approvedAmount(reqVO.getApprovedAmount().setScale(2, RoundingMode.HALF_UP))
                .financeComment(reqVO.getFinanceComment())
                .status(FinanceExpenseReimbursementDO.STATUS_WAIT_PAY)
                .build());
    }

    @Override
    public void recordPay(FinanceExpenseRecordPayReqVO reqVO, Long userId) {
        if (reqVO.getCompanyBankAccountId() == null) {
            throw exception(EXPENSE_REIMBURSEMENT_PAY_ACCOUNT_REQUIRED);
        }
        if (reqVO.getActualPayDate() == null) {
            throw exception(EXPENSE_REIMBURSEMENT_CASHIER_FIELDS_REQUIRED);
        }
        List<String> urls = new ArrayList<>();
        if (reqVO.getPayVoucherUrls() != null) {
            for (String raw : reqVO.getPayVoucherUrls()) {
                if (StrUtil.isNotBlank(raw)) {
                    urls.add(raw.trim());
                }
            }
        }
        if (urls.isEmpty() && StrUtil.isNotBlank(reqVO.getPayVoucherUrl())) {
            for (String part : reqVO.getPayVoucherUrl().split(",")) {
                if (StrUtil.isNotBlank(part)) {
                    urls.add(part.trim());
                }
            }
        }
        for (String url : urls) {
            if (!(url.startsWith("http://") || url.startsWith("https://")
                    || url.startsWith("/") || url.contains("/admin-api/infra/file/"))) {
                throw exception(EXPENSE_REIMBURSEMENT_ATTACHMENT_URL_INVALID);
            }
        }
        FinanceExpenseReimbursementDO header = mapper.selectById(reqVO.getId());
        if (header == null) {
            throw exception(EXPENSE_REIMBURSEMENT_NOT_EXISTS);
        }
        if (!FinanceExpenseReimbursementDO.STATUS_WAIT_PAY.equals(header.getStatus())
                || !isProcessEnded(header.getProcessInstanceId())) {
            throw exception(EXPENSE_REIMBURSEMENT_STATUS_INVALID);
        }
        companyBankAccountService.get(reqVO.getCompanyBankAccountId());
        mapper.updateById(FinanceExpenseReimbursementDO.builder()
                .id(header.getId())
                .companyBankAccountId(reqVO.getCompanyBankAccountId())
                .actualPayDate(reqVO.getActualPayDate())
                .payVoucherUrl(urls.isEmpty() ? null : String.join(",", urls))
                .status(FinanceExpenseReimbursementDO.STATUS_PAID)
                .build());
        completeLeftoverCashierTask(header);
    }

    private void validateInvoiceAndPredoc(FinanceExpenseReimbursementLineReqVO line,
                                         boolean proxy, String invoiceMode, Long userId) {
        boolean noInvoice = FinanceExpenseReimbursementDO.MODE_NO_INVOICE.equals(invoiceMode);
        boolean hasInvoice = StrUtil.isNotBlank(line.getInvoiceFileUrl());
        if (noInvoice) {
            if (hasInvoice) {
                throw exception(EXPENSE_REIMBURSEMENT_INVOICE_FORBIDDEN);
            }
        } else if (!proxy) {
            if (!hasInvoice || !isAcceptableFileUrl(line.getInvoiceFileUrl().trim())) {
                throw exception(EXPENSE_REIMBURSEMENT_INVOICE_REQUIRED);
            }
        } else if (hasInvoice && !isAcceptableFileUrl(line.getInvoiceFileUrl().trim())) {
            throw exception(EXPENSE_REIMBURSEMENT_ATTACHMENT_URL_INVALID);
        }
        String cat = line.getCategory() == null ? "" : line.getCategory().trim();
        String predocType = StrUtil.trimToNull(line.getPredocType());
        String predocId = StrUtil.trimToNull(line.getPredocProcessInstanceId());
        if (FinanceExpensePredocService.CAT_TRAVEL.equals(cat)) {
            if (!FinanceExpensePredocService.TYPE_TRIP.equals(predocType) || predocId == null) {
                throw exception(EXPENSE_REIMBURSEMENT_PREDOC_REQUIRED);
            }
            if (!predocService.isApprovedTrip(userId, predocId)) {
                throw exception(EXPENSE_REIMBURSEMENT_PREDOC_INVALID);
            }
            return;
        }
        if (FinanceExpensePredocService.CAT_TRANSPORT.equals(cat)) {
            if (predocId == null || (!FinanceExpensePredocService.TYPE_TRIP.equals(predocType)
                    && !FinanceExpensePredocService.TYPE_OUTING.equals(predocType))) {
                throw exception(EXPENSE_REIMBURSEMENT_PREDOC_REQUIRED);
            }
            boolean ok = FinanceExpensePredocService.TYPE_TRIP.equals(predocType)
                    ? predocService.isApprovedTrip(userId, predocId)
                    : predocService.isApprovedOuting(userId, predocId);
            if (!ok) {
                throw exception(EXPENSE_REIMBURSEMENT_PREDOC_INVALID);
            }
            return;
        }
        if (predocType != null || predocId != null) {
            throw exception(EXPENSE_REIMBURSEMENT_PREDOC_FORBIDDEN);
        }
    }

    @Override
    public boolean invoiceNoUsed(String invoiceNo) {
        return lineMapper.existsInvoiceNo(invoiceNo);
    }

    @Override
    public List<String> listOccupiedPredocProcessInstanceIds() {
        return lineMapper.selectOccupiedPredocProcessInstanceIds();
    }

    private void assertPredocsNotOccupied(List<FinanceExpenseReimbursementLineReqVO> lines) {
        Set<String> ids = new LinkedHashSet<>();
        for (FinanceExpenseReimbursementLineReqVO line : lines) {
            String id = StrUtil.trimToNull(line.getPredocProcessInstanceId());
            if (id != null) {
                ids.add(id);
            }
        }
        for (String id : ids) {
            if (lineMapper.existsOccupiedPredoc(id)) {
                throw exception(EXPENSE_REIMBURSEMENT_PREDOC_OCCUPIED);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onApprovalOutcome(Long id, String outcome, String processInstanceId) {
        if (id == null || StrUtil.isBlank(outcome)) {
            throw exception(EXPENSE_REIMBURSEMENT_STATUS_INVALID);
        }
        String normalized = outcome.trim().toUpperCase();
        if (!FinanceExpenseReimbursementDO.STATUS_REJECTED.equals(normalized)
                && !FinanceExpenseReimbursementDO.STATUS_CANCELLED.equals(normalized)) {
            throw exception(EXPENSE_REIMBURSEMENT_STATUS_INVALID);
        }
        FinanceExpenseReimbursementDO current = mapper.selectById(id);
        if (current == null) {
            throw exception(EXPENSE_REIMBURSEMENT_NOT_EXISTS);
        }
        if (StrUtil.isNotBlank(processInstanceId)
                && StrUtil.isNotBlank(current.getProcessInstanceId())
                && !Objects.equals(processInstanceId, current.getProcessInstanceId())) {
            return;
        }
        if (normalized.equals(current.getStatus())) {
            return;
        }
        if (FinanceExpenseReimbursementDO.STATUS_PAID.equals(current.getStatus())
                || FinanceExpenseReimbursementDO.STATUS_REJECTED.equals(current.getStatus())
                || FinanceExpenseReimbursementDO.STATUS_CANCELLED.equals(current.getStatus())) {
            return;
        }
        mapper.updateById(FinanceExpenseReimbursementDO.builder()
                .id(id)
                .status(normalized)
                .build());
    }

    /** 差旅住宿标准：房间数×城市标准×天数，只做超标说明，不卡控金额。 */
    void validateStayStandard(FinanceExpenseReimbursementLineReqVO line, Long userId) {
        String cat = line.getCategory() == null ? "" : line.getCategory().trim();
        if (!FinanceExpensePredocService.CAT_TRAVEL.equals(cat)) {
            return;
        }
        FinanceExpensePredocService.StayStay stay = predocService.resolveStay(
                userId, line.getPredocType(), line.getPredocProcessInstanceId());
        if (stay == null) {
            applyStayStandard(line, null);
            return;
        }
        java.util.LinkedHashSet<Long> people = new java.util.LinkedHashSet<>();
        if (stay.applicantId() != null) {
            people.add(stay.applicantId());
        }
        if (stay.companionIds() != null) {
            people.addAll(stay.companionIds());
        }
        java.util.List<Integer> sexes = new java.util.ArrayList<>();
        if (!people.isEmpty()) {
            CommonResult<java.util.List<AdminUserRespDTO>> users = adminUserApi.getUserList(people);
            java.util.Map<Long, AdminUserRespDTO> map = new java.util.HashMap<>();
            if (users != null && users.getData() != null) {
                for (AdminUserRespDTO u : users.getData()) {
                    if (u != null && u.getId() != null) {
                        map.put(u.getId(), u);
                    }
                }
            }
            for (Long id : people) {
                AdminUserRespDTO u = map.get(id);
                sexes.add(u == null ? null : u.getSex());
            }
        }
        int rooms = Math.max(1, FinanceStayCityCaps.rooms(sexes));
        int nights = FinanceStayCityCaps.nights(stay.start(), stay.end());
        applyStayStandard(line, stay.city(), rooms, nights);
    }

    static void applyStayStandard(FinanceExpenseReimbursementLineReqVO line, String city) {
        applyStayStandard(line, city, 1, 1);
    }

    static void applyStayStandard(FinanceExpenseReimbursementLineReqVO line, String city, int rooms, int nights) {
        String cat = line.getCategory() == null ? "" : line.getCategory().trim();
        if (!FinanceExpensePredocService.CAT_TRAVEL.equals(cat)) {
            return;
        }
        String tier = FinanceStayCityCaps.tier(city);
        if (tier == null) {
            throw exception(EXPENSE_REIMBURSEMENT_STAY_TIER_REQUIRED);
        }
        line.setStayCityTier(tier);
        java.math.BigDecimal cap = FinanceStayCityCaps.stayCap(tier, rooms, nights);
        if (line.getAmount() != null && line.getAmount().compareTo(cap) > 0
                && StrUtil.isBlank(line.getOverLimitReason())) {
            throw exception(EXPENSE_REIMBURSEMENT_OVER_LIMIT_REASON_REQUIRED);
        }
    }

    private static boolean isAcceptableFileUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        String t = url.trim();
        return t.startsWith("http://") || t.startsWith("https://")
                || t.startsWith("/") || t.contains("/admin-api/infra/file/");
    }

    private DeptRespDTO resolveCompany(Long deptId) {
        Long id = deptId;
        for (int i = 0; i < 16 && id != null && id != 0L; i++) {
            CommonResult<DeptRespDTO> r = deptApi.getDept(id);
            DeptRespDTO d = r == null ? null : r.getCheckedData();
            if (d == null) {
                return null;
            }
            if ("1".equals(String.valueOf(d.getOrgType()))) {
                return d;
            }
            Long parent = d.getParentId();
            if (parent == null || parent.equals(id)) {
                return null;
            }
            id = parent;
        }
        return null;
    }

    static List<String> normalizeExtraAttachments(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<String> urls = new ArrayList<>();
        for (String item : raw) {
            if (StrUtil.isBlank(item)) {
                continue;
            }
            for (String part : item.split("[,，]")) {
                if (StrUtil.isNotBlank(part)) {
                    urls.add(part.trim());
                }
            }
        }
        if (urls.size() > 30) {
            throw exception(EXPENSE_REIMBURSEMENT_EXTRA_ATTACHMENTS_EXCEED);
        }
        return urls;
    }

    private static List<String> splitCsv(String raw) {
        if (StrUtil.isBlank(raw)) {
            return List.of();
        }
        List<String> urls = new ArrayList<>();
        for (String part : raw.split("[,，]")) {
            if (StrUtil.isNotBlank(part)) {
                urls.add(part.trim());
            }
        }
        return urls;
    }

    private AdminUserRespDTO requireUser(Long userId) {
        CommonResult<AdminUserRespDTO> result = adminUserApi.getUser(userId);
        AdminUserRespDTO user = result == null ? null : result.getCheckedData();
        if (user == null) {
            throw exception(EXPENSE_REIMBURSEMENT_FIELD_REQUIRED);
        }
        return user;
    }
}
