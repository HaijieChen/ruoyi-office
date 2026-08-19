package cn.iocoder.yudao.module.finance.service.expense;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
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
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.EXPENSE_REIMBURSEMENT_AMOUNT_INVALID;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.EXPENSE_REIMBURSEMENT_DEPT_REQUIRED;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.EXPENSE_REIMBURSEMENT_FIELD_REQUIRED;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.EXPENSE_REIMBURSEMENT_LINES_EMPTY;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.EXPENSE_REIMBURSEMENT_LINE_KIND_MISMATCH;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.EXPENSE_REIMBURSEMENT_NOT_EXISTS;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.EXPENSE_REIMBURSEMENT_PERIOD_INVALID;

@Service
@Validated
public class FinanceExpenseReimbursementServiceImpl implements FinanceExpenseReimbursementService {

    private final FinanceExpenseReimbursementMapper mapper;
    private final FinanceExpenseReimbursementLineMapper lineMapper;
    private final AdminUserApi adminUserApi;
    private final FinanceBpmProcessInstanceApi processInstanceApi;

    public FinanceExpenseReimbursementServiceImpl(FinanceExpenseReimbursementMapper mapper,
                                                  FinanceExpenseReimbursementLineMapper lineMapper,
                                                  AdminUserApi adminUserApi,
                                                  FinanceBpmProcessInstanceApi processInstanceApi) {
        this.mapper = mapper;
        this.lineMapper = lineMapper;
        this.adminUserApi = adminUserApi;
        this.processInstanceApi = processInstanceApi;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(FinanceExpenseReimbursementCreateReqVO reqVO, Long userId) {
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
                    .remark(line.getRemark())
                    .sort(sort++)
                    .build());
        }

        Map<String, Object> vars = new HashMap<>();
        vars.put("applyAmount", apply);
        vars.put("periodLabel", header.getPeriodLabel());
        String processInstanceId = processInstanceApi.createProcessInstance(userId,
                        new BpmProcessInstanceCreateReqDTO()
                                .setProcessDefinitionKey(PROCESS_KEY)
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
        List<FinanceExpenseReimbursementLineReqVO> lines = new ArrayList<>();
        for (FinanceExpenseReimbursementLineDO line : lineMapper.selectByReimbursementId(header.getId())) {
            FinanceExpenseReimbursementLineReqVO lv = new FinanceExpenseReimbursementLineReqVO();
            lv.setLineKind(line.getLineKind());
            lv.setCategory(line.getCategory());
            lv.setFeeDate(line.getFeeDate());
            lv.setAmount(line.getAmount());
            lv.setRemark(line.getRemark());
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

    private AdminUserRespDTO requireUser(Long userId) {
        CommonResult<AdminUserRespDTO> result = adminUserApi.getUser(userId);
        AdminUserRespDTO user = result == null ? null : result.getCheckedData();
        if (user == null) {
            throw exception(EXPENSE_REIMBURSEMENT_FIELD_REQUIRED);
        }
        return user;
    }
}
