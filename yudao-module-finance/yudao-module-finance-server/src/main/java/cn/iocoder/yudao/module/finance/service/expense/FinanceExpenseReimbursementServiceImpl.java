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
import cn.iocoder.yudao.module.finance.dal.dataobject.expense.FinanceExpenseReimbursementDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.expense.FinanceExpenseReimbursementLineDO;
import cn.iocoder.yudao.module.finance.dal.mysql.expense.FinanceExpenseReimbursementLineMapper;
import cn.iocoder.yudao.module.finance.dal.mysql.expense.FinanceExpenseReimbursementMapper;
import cn.iocoder.yudao.module.finance.framework.rpc.FinanceBpmProcessInstanceApi;
import cn.iocoder.yudao.module.finance.framework.security.FinanceProcessParticipantSupport;
import jakarta.annotation.Resource;
import cn.iocoder.yudao.module.finance.service.companyaccount.FinanceCompanyBankAccountService;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import org.flowable.engine.TaskService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.EXPENSE_REIMBURSEMENT_PREDOC_REQUIRED;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.EXPENSE_REIMBURSEMENT_INVOICE_USED;
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

    public FinanceExpenseReimbursementServiceImpl(FinanceExpenseReimbursementMapper mapper,
                                                  FinanceExpenseReimbursementLineMapper lineMapper,
                                                  AdminUserApi adminUserApi,
                                                  FinanceBpmProcessInstanceApi processInstanceApi,
                                                  FinanceCompanyBankAccountService companyBankAccountService,
                                                  FinanceExpensePredocService predocService,
                                                  DeptApi deptApi,
                                                  ObjectProvider<TaskService> taskServiceProvider) {
        this.mapper = mapper;
        this.lineMapper = lineMapper;
        this.adminUserApi = adminUserApi;
        this.processInstanceApi = processInstanceApi;
        this.companyBankAccountService = companyBankAccountService;
        this.predocService = predocService;
        this.deptApi = deptApi;
        this.taskServiceProvider = taskServiceProvider;
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
        if (apply.compareTo(BigDecimal.ZERO) <= 0) {
            throw exception(EXPENSE_REIMBURSEMENT_AMOUNT_INVALID);
        }
        apply = apply.setScale(2, RoundingMode.HALF_UP);
        AdminUserRespDTO user = requireUser(actualUserId);
        if (user.getDeptId() == null) {
            throw exception(EXPENSE_REIMBURSEMENT_DEPT_REQUIRED);
        }
        DeptRespDTO company = resolveCompany(user.getDeptId());
        String nickname = StrUtil.blankToDefault(user.getNickname(), String.valueOf(actualUserId));
        String title = "【报销】-" + nickname + "-" + reqVO.getPeriodLabel().trim() + "-" + apply.toPlainString();

        FinanceExpenseReimbursementDO header = FinanceExpenseReimbursementDO.builder()
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
        List<FinanceExpenseReimbursementRespVO> list = new ArrayList<>();
        for (FinanceExpenseReimbursementDO header : page.getList()) {
            list.add(toResp(header, canQueryAll));
        }
        return new PageResult<>(list, page.getTotal());
    }

    private FinanceExpenseReimbursementRespVO toResp(FinanceExpenseReimbursementDO header, boolean revealAccount) {
        FinanceExpenseReimbursementRespVO vo = new FinanceExpenseReimbursementRespVO();
        vo.setId(header.getId());
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
        vo.setApplicantUserId(header.getApplicantUserId());
        vo.setActualUserId(header.getActualUserId());
        vo.setApplicantDeptId(header.getApplicantDeptId());
        vo.setEntityCompanyDeptId(header.getEntityCompanyDeptId());
        vo.setEntityCompanyName(header.getEntityCompanyName());
        vo.setApplyDate(header.getApplyDate());
        vo.setFinanceComment(header.getFinanceComment());
        vo.setActualPayDate(header.getActualPayDate());
        vo.setCompanyBankAccountId(header.getCompanyBankAccountId());
        List<FinanceExpenseReimbursementLineReqVO> lines = new ArrayList<>();
        for (FinanceExpenseReimbursementLineDO line : lineMapper.selectByReimbursementId(header.getId())) {
            FinanceExpenseReimbursementLineReqVO lv = new FinanceExpenseReimbursementLineReqVO();
            lv.setLineKind(line.getLineKind());
            lv.setCategory(line.getCategory());
            lv.setFeeDate(line.getFeeDate());
            lv.setAmount(line.getAmount());
            lv.setRemark(line.getRemark());
            lv.setStayCityTier(line.getStayCityTier());
            lv.setOverLimitReason(line.getOverLimitReason());
            lv.setInvoiceFileUrl(line.getInvoiceFileUrl());
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
        if (reqVO.getActualPayDate() == null || StrUtil.isBlank(reqVO.getPayVoucherUrl())) {
            throw exception(EXPENSE_REIMBURSEMENT_CASHIER_FIELDS_REQUIRED);
        }
        String url = reqVO.getPayVoucherUrl().trim();
        if (!(url.startsWith("http://") || url.startsWith("https://")
                || url.startsWith("/") || url.contains("/admin-api/infra/file/"))) {
            throw exception(EXPENSE_REIMBURSEMENT_ATTACHMENT_URL_INVALID);
        }
        FinanceExpenseReimbursementDO header = mapper.selectById(reqVO.getId());
        if (header == null) {
            throw exception(EXPENSE_REIMBURSEMENT_NOT_EXISTS);
        }
        if (!FinanceExpenseReimbursementDO.STATUS_WAIT_PAY.equals(header.getStatus())) {
            throw exception(EXPENSE_REIMBURSEMENT_STATUS_INVALID);
        }
        companyBankAccountService.get(reqVO.getCompanyBankAccountId());
        mapper.updateById(FinanceExpenseReimbursementDO.builder()
                .id(header.getId())
                .companyBankAccountId(reqVO.getCompanyBankAccountId())
                .actualPayDate(reqVO.getActualPayDate())
                .payVoucherUrl(url)
                .status(FinanceExpenseReimbursementDO.STATUS_PAID)
                .build());
    }

    private void validateInvoiceAndPredoc(FinanceExpenseReimbursementLineReqVO line,
                                         boolean proxy, String invoiceMode, Long userId) {
        boolean noInvoice = FinanceExpenseReimbursementDO.MODE_NO_INVOICE.equals(invoiceMode);
        boolean hasInvoice = StrUtil.isNotBlank(line.getInvoiceFileUrl());
        if (noInvoice || proxy) {
            if (hasInvoice) {
                throw exception(EXPENSE_REIMBURSEMENT_INVOICE_FORBIDDEN);
            }
        } else {
            if (!hasInvoice || !isAcceptableFileUrl(line.getInvoiceFileUrl().trim())) {
                throw exception(EXPENSE_REIMBURSEMENT_INVOICE_REQUIRED);
            }
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

    private AdminUserRespDTO requireUser(Long userId) {
        CommonResult<AdminUserRespDTO> result = adminUserApi.getUser(userId);
        AdminUserRespDTO user = result == null ? null : result.getCheckedData();
        if (user == null) {
            throw exception(EXPENSE_REIMBURSEMENT_FIELD_REQUIRED);
        }
        return user;
    }
}
