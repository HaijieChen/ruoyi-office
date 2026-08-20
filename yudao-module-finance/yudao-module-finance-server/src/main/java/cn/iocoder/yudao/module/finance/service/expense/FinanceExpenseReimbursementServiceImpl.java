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
import cn.iocoder.yudao.module.finance.service.companyaccount.FinanceCompanyBankAccountService;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
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

    public FinanceExpenseReimbursementServiceImpl(FinanceExpenseReimbursementMapper mapper,
                                                  FinanceExpenseReimbursementLineMapper lineMapper,
                                                  AdminUserApi adminUserApi,
                                                  FinanceBpmProcessInstanceApi processInstanceApi,
                                                  FinanceCompanyBankAccountService companyBankAccountService,
                                                  FinanceExpensePredocService predocService) {
        this.mapper = mapper;
        this.lineMapper = lineMapper;
        this.adminUserApi = adminUserApi;
        this.processInstanceApi = processInstanceApi;
        this.companyBankAccountService = companyBankAccountService;
        this.predocService = predocService;
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
        if (StrUtil.isBlank(reqVO.getPayeeAccountName()) || StrUtil.isBlank(reqVO.getPayeeAccountNo())) {
            throw exception(EXPENSE_REIMBURSEMENT_FIELD_REQUIRED);
        }
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
            validateInvoiceAndPredoc(line, proxy, invoiceMode, userId);
            validateStayStandard(line, userId);
            apply = apply.add(line.getAmount());
            i++;
        }
        if (apply.compareTo(BigDecimal.ZERO) <= 0) {
            throw exception(EXPENSE_REIMBURSEMENT_AMOUNT_INVALID);
        }
        apply = apply.setScale(2, RoundingMode.HALF_UP);
        AdminUserRespDTO user = requireUser(userId);
        if (user.getDeptId() == null) {
            throw exception(EXPENSE_REIMBURSEMENT_DEPT_REQUIRED);
        }
        String nickname = StrUtil.blankToDefault(user.getNickname(), String.valueOf(userId));
        String title = "【报销】-" + nickname + "-" + reqVO.getPeriodLabel().trim() + "-" + apply.toPlainString();

        FinanceExpenseReimbursementDO header = FinanceExpenseReimbursementDO.builder()
                .processTitle(title)
                .periodLabel(reqVO.getPeriodLabel().trim())
                .payeeAccountName(reqVO.getPayeeAccountName().trim())
                .payeeAccountNo(reqVO.getPayeeAccountNo().trim())
                .applyAmount(apply)
                .proxyTicket(proxy)
                .invoiceMode(invoiceMode)
                .processKey(processKey)
                .status(FinanceExpenseReimbursementDO.STATUS_PENDING)
                .applicantUserId(userId)
                .applicantDeptId(user.getDeptId())
                .applyDate(LocalDate.now())
                .build();
        mapper.insert(header);

        int sort = 0;
        for (FinanceExpenseReimbursementLineReqVO line : reqVO.getLines()) {
            lineMapper.insert(FinanceExpenseReimbursementLineDO.builder()
                    .reimbursementId(header.getId())
                    .lineKind(expectKind)
                    .category(line.getCategory().trim())
                    .feeDate(line.getFeeDate())
                    .amount(line.getAmount().setScale(2, RoundingMode.HALF_UP))
                    .attachments(line.getAttachments() == null ? null : String.join(",", line.getAttachments()))
                    .invoiceFileUrl(line.getInvoiceFileUrl())
                    .predocType(line.getPredocType())
                    .predocProcessInstanceId(line.getPredocProcessInstanceId())
                    .remark(line.getRemark())
                    .stayCityTier(line.getStayCityTier())
                    .overLimitReason(line.getOverLimitReason())
                    .sort(sort++)
                    .build());
        }

        Map<String, Object> vars = new HashMap<>();
        vars.put("applyAmount", apply);
        vars.put("periodLabel", header.getPeriodLabel());
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
        Long forceApplicant = canQueryAll ? null : userId;
        PageResult<FinanceExpenseReimbursementDO> page = mapper.selectPage(reqVO, forceApplicant);
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
        vo.setPayeeAccountNo(revealAccount
                ? header.getPayeeAccountNo()
                : FinanceCompanyBankAccountService.maskAccountNo(header.getPayeeAccountNo()));
        vo.setApplyAmount(header.getApplyAmount());
        vo.setApprovedAmount(header.getApprovedAmount());
        vo.setProxyTicket(header.getProxyTicket());
        vo.setStatus(header.getStatus());
        vo.setProcessInstanceId(header.getProcessInstanceId());
        vo.setApplicantUserId(header.getApplicantUserId());
        vo.setApplicantDeptId(header.getApplicantDeptId());
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
        if (canQueryAll || Objects.equals(header.getApplicantUserId(), userId)) {
            return;
        }
        throw exception(EXPENSE_REIMBURSEMENT_ACCESS_DENIED);
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

    /** 差旅住宿标准按出差/外出城市裁定，只做超标说明，不卡控金额。 */
    void validateStayStandard(FinanceExpenseReimbursementLineReqVO line, Long userId) {
        String cat = line.getCategory() == null ? "" : line.getCategory().trim();
        if (!FinanceExpensePredocService.CAT_TRAVEL.equals(cat)) {
            return;
        }
        String city = predocService.resolveCity(userId, line.getPredocType(), line.getPredocProcessInstanceId());
        applyStayStandard(line, city);
    }

    static void applyStayStandard(FinanceExpenseReimbursementLineReqVO line, String city) {
        String cat = line.getCategory() == null ? "" : line.getCategory().trim();
        if (!FinanceExpensePredocService.CAT_TRAVEL.equals(cat)) {
            return;
        }
        String tier = FinanceStayCityCaps.tier(city);
        if (tier == null) {
            throw exception(EXPENSE_REIMBURSEMENT_STAY_TIER_REQUIRED);
        }
        line.setStayCityTier(tier);
        java.math.BigDecimal cap = FinanceStayCityCaps.cap(tier);
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

    private AdminUserRespDTO requireUser(Long userId) {
        CommonResult<AdminUserRespDTO> result = adminUserApi.getUser(userId);
        AdminUserRespDTO user = result == null ? null : result.getCheckedData();
        if (user == null) {
            throw exception(EXPENSE_REIMBURSEMENT_FIELD_REQUIRED);
        }
        return user;
    }
}
