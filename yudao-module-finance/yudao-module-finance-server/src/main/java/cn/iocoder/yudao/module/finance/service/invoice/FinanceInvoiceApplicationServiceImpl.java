package cn.iocoder.yudao.module.finance.service.invoice;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.yudao.module.finance.controller.admin.invoice.vo.FinanceInvoiceApplicationCreateAndStartReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.invoice.vo.FinanceInvoiceApplicationPageReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.invoice.vo.FinanceInvoiceApplicationResubmitReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.invoice.vo.FinanceInvoiceApplicationUpdateIssueProgressReqVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.business.FinanceBusinessOrderDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.invoice.FinanceInvoiceApplicationDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.invoice.FinanceInvoiceApplicationLineDO;
import cn.iocoder.yudao.module.finance.dal.mysql.business.FinanceBusinessOrderMapper;
import cn.iocoder.yudao.module.finance.dal.mysql.invoice.FinanceInvoiceApplicationLineMapper;
import cn.iocoder.yudao.module.finance.dal.mysql.invoice.FinanceInvoiceApplicationMapper;
import cn.iocoder.yudao.module.finance.dal.redis.no.FinanceInvoiceApplicationNoRedisDAO;
import cn.iocoder.yudao.module.finance.enums.FinanceInvoiceApprovalStatusEnum;
import cn.iocoder.yudao.module.finance.enums.FinanceInvoiceIssueStatusEnum;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.*;

/**
 * 开票申请 Service 实现。
 * <p>占用写路径：createAndStart / resubmit 增占；onApprovalOutcome(REJECTED|CANCELLED) / resubmit 释占。
 * <p>issue 写路径：仅 {@link #updateIssueProgress}。
 */
@Service
@Validated
public class FinanceInvoiceApplicationServiceImpl implements FinanceInvoiceApplicationService {

    /**
     * 开票申请流程定义 KEY（固定）
     */
    public static final String PROCESS_KEY = "finance_invoice_apply";

    /**
     * 明细行「已开票」状态值（一期一行一票；与表头 PARTIAL=1 数值相同，语义不同）
     */
    public static final int LINE_ISSUE_STATUS_ISSUED = 1;

    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private final FinanceInvoiceApplicationMapper applicationMapper;
    private final FinanceInvoiceApplicationLineMapper lineMapper;
    private final FinanceBusinessOrderMapper businessOrderMapper;
    private final FinanceInvoiceApplicationNoRedisDAO applicationNoRedisDAO;
    private final BpmProcessInstanceApi processInstanceApi;

    public FinanceInvoiceApplicationServiceImpl(FinanceInvoiceApplicationMapper applicationMapper,
                                                FinanceInvoiceApplicationLineMapper lineMapper,
                                                FinanceBusinessOrderMapper businessOrderMapper,
                                                FinanceInvoiceApplicationNoRedisDAO applicationNoRedisDAO,
                                                BpmProcessInstanceApi processInstanceApi) {
        this.applicationMapper = applicationMapper;
        this.lineMapper = lineMapper;
        this.businessOrderMapper = businessOrderMapper;
        this.applicationNoRedisDAO = applicationNoRedisDAO;
        this.processInstanceApi = processInstanceApi;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createAndStart(FinanceInvoiceApplicationCreateAndStartReqVO reqVO, Long applicantUserId) {
        List<FinanceInvoiceApplicationCreateAndStartReqVO.Line> lines = reqVO.getLines();
        if (CollUtil.isEmpty(lines)) {
            throw exception(INVOICE_APPLICATION_LINES_EMPTY);
        }

        // 1. 校验明细金额 + 汇总占用 + 校验商务单存在
        Map<Long, BigDecimal> occupyByBo = new LinkedHashMap<>();
        BigDecimal totalAmount = ZERO;
        for (FinanceInvoiceApplicationCreateAndStartReqVO.Line line : lines) {
            if (line.getAmount() == null || line.getAmount().compareTo(ZERO) <= 0) {
                throw exception(INVOICE_APPLICATION_AMOUNT_INVALID);
            }
            if (line.getBusinessOrderId() == null) {
                throw exception(INVOICE_APPLICATION_BUSINESS_ORDER_NOT_EXISTS);
            }
            totalAmount = totalAmount.add(line.getAmount());
            occupyByBo.merge(line.getBusinessOrderId(), line.getAmount(), BigDecimal::add);
        }

        Map<Long, FinanceBusinessOrderDO> orderMap = loadBusinessOrders(occupyByBo.keySet());
        for (Map.Entry<Long, BigDecimal> entry : occupyByBo.entrySet()) {
            FinanceBusinessOrderDO order = orderMap.get(entry.getKey());
            if (order == null) {
                throw exception(INVOICE_APPLICATION_BUSINESS_ORDER_NOT_EXISTS);
            }
            BigDecimal occupied = defaultZero(order.getInvoicedOccupiedAmount());
            BigDecimal settlement = defaultZero(order.getSettlementAmount());
            if (settlement.subtract(occupied).compareTo(entry.getValue()) < 0) {
                throw exception(INVOICE_APPLICATION_OCCUPY_EXCEED);
            }
        }

        // 2. 写主表
        String applicationNo = applicationNoRedisDAO.generate(LocalDate.now());
        FinanceInvoiceApplicationDO application = FinanceInvoiceApplicationDO.builder()
                .applicationNo(applicationNo)
                .approvalStatus(FinanceInvoiceApprovalStatusEnum.PENDING.getStatus())
                .issueStatus(FinanceInvoiceIssueStatusEnum.NONE.getStatus())
                .totalAmount(totalAmount)
                .confirmedClaimedAmount(ZERO)
                .pendingClaimedAmount(ZERO)
                .applicantUserId(applicantUserId)
                .expectedInvoiceDate(reqVO.getExpectedInvoiceDate())
                .invoiceCompany(reqVO.getInvoiceCompany())
                .invoiceCompanyDeptId(reqVO.getInvoiceCompanyDeptId())
                .invoiceType(reqVO.getInvoiceType())
                .buyerName(reqVO.getBuyerName())
                .buyerTaxNo(reqVO.getBuyerTaxNo())
                .buyerAddressPhone(reqVO.getBuyerAddressPhone())
                .buyerBankAccount(reqVO.getBuyerBankAccount())
                .specialInvoiceRequirement(reqVO.getSpecialInvoiceRequirement())
                .taxContent(reqVO.getTaxContent())
                .taxRate(reqVO.getTaxRate())
                .amountExcludingTax(reqVO.getAmountExcludingTax())
                .taxAmount(reqVO.getTaxAmount())
                .evidenceFileUrl(reqVO.getEvidenceFileUrl())
                .remark(reqVO.getRemark())
                .voided(Boolean.FALSE)
                .build();
        applicationMapper.insert(application);

        // 3. 写明细（提交快照）
        int sort = 0;
        for (FinanceInvoiceApplicationCreateAndStartReqVO.Line line : lines) {
            int lineSort = line.getSort() != null ? line.getSort() : sort;
            FinanceInvoiceApplicationLineDO lineDO = FinanceInvoiceApplicationLineDO.builder()
                    .applicationId(application.getId())
                    .businessOrderId(line.getBusinessOrderId())
                    .amount(line.getAmount())
                    .invoiceCompany(line.getInvoiceCompany() != null ? line.getInvoiceCompany() : reqVO.getInvoiceCompany())
                    .invoiceType(line.getInvoiceType() != null ? line.getInvoiceType() : reqVO.getInvoiceType())
                    .billingPeriod(line.getBillingPeriod())
                    .issueStatus(FinanceInvoiceIssueStatusEnum.NONE.getStatus())
                    .sort(lineSort)
                    .build();
            lineMapper.insert(lineDO);
            sort++;
        }

        // 4. 按 BO 汇总 CAS 占用
        increaseOccupy(occupyByBo);

        // 5. 启动 BPM；失败则整单回滚（含占用）
        Map<String, Object> variables = buildProcessVariables(application);
        String processInstanceId = processInstanceApi.createProcessInstance(applicantUserId,
                new BpmProcessInstanceCreateReqDTO()
                        .setProcessDefinitionKey(PROCESS_KEY)
                        .setBusinessKey(String.valueOf(application.getId()))
                        .setVariables(variables)
                        .setStartUserSelectAssignees(reqVO.getStartUserSelectAssignees()))
                .getCheckedData();

        FinanceInvoiceApplicationDO processUpdate = new FinanceInvoiceApplicationDO();
        processUpdate.setId(application.getId());
        processUpdate.setProcessInstanceId(processInstanceId);
        applicationMapper.updateById(processUpdate);
        return application.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onApprovalOutcome(Long appId, String outcome) {
        if (appId == null || StrUtil.isBlank(outcome)) {
            throw exception(INVOICE_APPLICATION_APPROVAL_OUTCOME_INVALID);
        }
        String normalized = outcome.trim().toUpperCase();
        if (!isTerminalOutcome(normalized)) {
            throw exception(INVOICE_APPLICATION_APPROVAL_OUTCOME_INVALID);
        }

        FinanceInvoiceApplicationDO application = getApplication(appId);
        String current = application.getApprovalStatus();

        // 同 outcome 幂等（含 CANCELLED 已 voided）
        if (Objects.equals(current, normalized)) {
            if (FinanceInvoiceApprovalStatusEnum.CANCELLED.getStatus().equals(normalized)
                    && !Boolean.TRUE.equals(application.getVoided())) {
                FinanceInvoiceApplicationDO voidUpdate = new FinanceInvoiceApplicationDO();
                voidUpdate.setId(appId);
                voidUpdate.setVoided(Boolean.TRUE);
                applicationMapper.updateById(voidUpdate);
            }
            return;
        }

        // 非法迁移：终态之间互转 / 已 voided 再改
        if (Boolean.TRUE.equals(application.getVoided())) {
            throw exception(INVOICE_APPLICATION_APPROVAL_OUTCOME_INVALID);
        }
        if (FinanceInvoiceApprovalStatusEnum.APPROVED.getStatus().equals(current)
                || FinanceInvoiceApprovalStatusEnum.REJECTED.getStatus().equals(current)
                || FinanceInvoiceApprovalStatusEnum.CANCELLED.getStatus().equals(current)) {
            throw exception(INVOICE_APPLICATION_APPROVAL_OUTCOME_INVALID);
        }
        // 仅允许 PENDING -> APPROVED|REJECTED|CANCELLED
        if (!FinanceInvoiceApprovalStatusEnum.PENDING.getStatus().equals(current)) {
            throw exception(INVOICE_APPLICATION_APPROVAL_OUTCOME_INVALID);
        }

        FinanceInvoiceApplicationDO update = new FinanceInvoiceApplicationDO();
        update.setId(appId);
        update.setApprovalStatus(normalized);

        if (FinanceInvoiceApprovalStatusEnum.APPROVED.getStatus().equals(normalized)) {
            // 保持 BO 占用；claimAllowed 由 status 投影
            applicationMapper.updateById(update);
            return;
        }

        // REJECTED / CANCELLED：释放本单全部 BO 占用
        releaseAllOccupyForApplication(appId);
        if (FinanceInvoiceApprovalStatusEnum.CANCELLED.getStatus().equals(normalized)) {
            update.setVoided(Boolean.TRUE);
        }
        applicationMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resubmit(Long appId, FinanceInvoiceApplicationResubmitReqVO reqVO, Long userId) {
        FinanceInvoiceApplicationDO application = getApplication(appId);
        if (!FinanceInvoiceApprovalStatusEnum.REJECTED.getStatus().equals(application.getApprovalStatus())
                || Boolean.TRUE.equals(application.getVoided())) {
            throw exception(INVOICE_APPLICATION_STATUS_INVALID);
        }

        List<FinanceInvoiceApplicationResubmitReqVO.Line> lines = reqVO.getLines();
        if (CollUtil.isEmpty(lines)) {
            throw exception(INVOICE_APPLICATION_LINES_EMPTY);
        }

        Map<Long, BigDecimal> newOccupyByBo = new LinkedHashMap<>();
        BigDecimal totalAmount = ZERO;
        for (FinanceInvoiceApplicationResubmitReqVO.Line line : lines) {
            if (line.getAmount() == null || line.getAmount().compareTo(ZERO) <= 0) {
                throw exception(INVOICE_APPLICATION_AMOUNT_INVALID);
            }
            if (line.getBusinessOrderId() == null) {
                throw exception(INVOICE_APPLICATION_BUSINESS_ORDER_NOT_EXISTS);
            }
            totalAmount = totalAmount.add(line.getAmount());
            newOccupyByBo.merge(line.getBusinessOrderId(), line.getAmount(), BigDecimal::add);
        }

        // 1. 释放当前明细占用（驳回时 onApprovalOutcome 已释占；此处再调用对 0 占用幂等安全：按当前 lines 汇总 CAS）
        // 驳回后占用应为 0；为防漏回调，按现有 lines 再尝试释占（若已 0 则 CAS 失败需区分）
        releaseAllOccupyForApplicationIfAny(appId);

        // 2. 校验新占用
        Map<Long, FinanceBusinessOrderDO> orderMap = loadBusinessOrders(newOccupyByBo.keySet());
        for (Map.Entry<Long, BigDecimal> entry : newOccupyByBo.entrySet()) {
            FinanceBusinessOrderDO order = orderMap.get(entry.getKey());
            if (order == null) {
                throw exception(INVOICE_APPLICATION_BUSINESS_ORDER_NOT_EXISTS);
            }
            // 重新读占用（可能刚释放）
            order = businessOrderMapper.selectById(entry.getKey());
            BigDecimal occupied = defaultZero(order.getInvoicedOccupiedAmount());
            BigDecimal settlement = defaultZero(order.getSettlementAmount());
            if (settlement.subtract(occupied).compareTo(entry.getValue()) < 0) {
                throw exception(INVOICE_APPLICATION_OCCUPY_EXCEED);
            }
        }

        // 3. 替换明细
        lineMapper.deleteByApplicationId(appId);
        int sort = 0;
        for (FinanceInvoiceApplicationResubmitReqVO.Line line : lines) {
            int lineSort = line.getSort() != null ? line.getSort() : sort;
            FinanceInvoiceApplicationLineDO lineDO = FinanceInvoiceApplicationLineDO.builder()
                    .applicationId(appId)
                    .businessOrderId(line.getBusinessOrderId())
                    .amount(line.getAmount())
                    .invoiceCompany(line.getInvoiceCompany() != null ? line.getInvoiceCompany() : reqVO.getInvoiceCompany())
                    .invoiceType(line.getInvoiceType() != null ? line.getInvoiceType() : reqVO.getInvoiceType())
                    .billingPeriod(line.getBillingPeriod())
                    .issueStatus(FinanceInvoiceIssueStatusEnum.NONE.getStatus())
                    .sort(lineSort)
                    .build();
            lineMapper.insert(lineDO);
            sort++;
        }

        // 4. 更新表头快照 + 回到 PENDING
        FinanceInvoiceApplicationDO headerUpdate = new FinanceInvoiceApplicationDO();
        headerUpdate.setId(appId);
        headerUpdate.setApprovalStatus(FinanceInvoiceApprovalStatusEnum.PENDING.getStatus());
        headerUpdate.setIssueStatus(FinanceInvoiceIssueStatusEnum.NONE.getStatus());
        headerUpdate.setTotalAmount(totalAmount);
        headerUpdate.setExpectedInvoiceDate(reqVO.getExpectedInvoiceDate());
        headerUpdate.setInvoiceCompany(reqVO.getInvoiceCompany());
        headerUpdate.setInvoiceCompanyDeptId(reqVO.getInvoiceCompanyDeptId());
        headerUpdate.setInvoiceType(reqVO.getInvoiceType());
        headerUpdate.setBuyerName(reqVO.getBuyerName());
        headerUpdate.setBuyerTaxNo(reqVO.getBuyerTaxNo());
        headerUpdate.setBuyerAddressPhone(reqVO.getBuyerAddressPhone());
        headerUpdate.setBuyerBankAccount(reqVO.getBuyerBankAccount());
        headerUpdate.setSpecialInvoiceRequirement(reqVO.getSpecialInvoiceRequirement());
        headerUpdate.setTaxContent(reqVO.getTaxContent());
        headerUpdate.setTaxRate(reqVO.getTaxRate());
        headerUpdate.setAmountExcludingTax(reqVO.getAmountExcludingTax());
        headerUpdate.setTaxAmount(reqVO.getTaxAmount());
        headerUpdate.setEvidenceFileUrl(reqVO.getEvidenceFileUrl());
        headerUpdate.setRemark(reqVO.getRemark());
        headerUpdate.setVoided(Boolean.FALSE);
        applicationMapper.updateById(headerUpdate);

        // 5. 再占
        increaseOccupy(newOccupyByBo);

        // 6. 新流程实例（保留旧 processInstanceId 历史审计于 BPM；仅覆盖最新 id）
        FinanceInvoiceApplicationDO refreshed = getApplication(appId);
        Map<String, Object> variables = buildProcessVariables(refreshed);
        String processInstanceId = processInstanceApi.createProcessInstance(userId,
                new BpmProcessInstanceCreateReqDTO()
                        .setProcessDefinitionKey(PROCESS_KEY)
                        .setBusinessKey(String.valueOf(appId))
                        .setVariables(variables)
                        .setStartUserSelectAssignees(reqVO.getStartUserSelectAssignees()))
                .getCheckedData();

        FinanceInvoiceApplicationDO processUpdate = new FinanceInvoiceApplicationDO();
        processUpdate.setId(appId);
        processUpdate.setProcessInstanceId(processInstanceId);
        applicationMapper.updateById(processUpdate);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateIssueProgress(FinanceInvoiceApplicationUpdateIssueProgressReqVO reqVO) {
        FinanceInvoiceApplicationDO application = getApplication(reqVO.getApplicationId());
        if (!FinanceInvoiceApprovalStatusEnum.APPROVED.getStatus().equals(application.getApprovalStatus())
                || Boolean.TRUE.equals(application.getVoided())) {
            throw exception(INVOICE_APPLICATION_ISSUE_NOT_ALLOWED);
        }

        FinanceInvoiceApplicationLineDO line = lineMapper.selectById(reqVO.getLineId());
        if (line == null || !Objects.equals(line.getApplicationId(), reqVO.getApplicationId())) {
            throw exception(INVOICE_APPLICATION_LINE_NOT_EXISTS);
        }
        if (line.getIssueStatus() != null && line.getIssueStatus() == LINE_ISSUE_STATUS_ISSUED) {
            throw exception(INVOICE_APPLICATION_LINE_ALREADY_ISSUED);
        }

        LocalDateTime issuedAt = reqVO.getIssuedAt() != null ? reqVO.getIssuedAt() : LocalDateTime.now();
        FinanceInvoiceApplicationLineDO lineUpdate = new FinanceInvoiceApplicationLineDO();
        lineUpdate.setId(line.getId());
        lineUpdate.setIssueStatus(LINE_ISSUE_STATUS_ISSUED);
        lineUpdate.setInvoiceNo(reqVO.getInvoiceNo());
        lineUpdate.setFileUrl(reqVO.getFileUrl());
        lineUpdate.setIssuedAt(issuedAt);
        lineMapper.updateById(lineUpdate);

        // 派生 app issue_status：0 无 / 1 部分 / 2 全部；不改 claimAllowed
        List<FinanceInvoiceApplicationLineDO> allLines = lineMapper.selectListByApplicationId(reqVO.getApplicationId());
        int total = allLines.size();
        int issued = 0;
        for (FinanceInvoiceApplicationLineDO item : allLines) {
            int status = item.getId().equals(line.getId())
                    ? LINE_ISSUE_STATUS_ISSUED
                    : (item.getIssueStatus() == null ? 0 : item.getIssueStatus());
            if (status == LINE_ISSUE_STATUS_ISSUED) {
                issued++;
            }
        }
        Integer appIssueStatus;
        if (issued <= 0) {
            appIssueStatus = FinanceInvoiceIssueStatusEnum.NONE.getStatus();
        } else if (issued >= total) {
            appIssueStatus = FinanceInvoiceIssueStatusEnum.FULL.getStatus();
        } else {
            appIssueStatus = FinanceInvoiceIssueStatusEnum.PARTIAL.getStatus();
        }
        FinanceInvoiceApplicationDO appUpdate = new FinanceInvoiceApplicationDO();
        appUpdate.setId(reqVO.getApplicationId());
        appUpdate.setIssueStatus(appIssueStatus);
        applicationMapper.updateById(appUpdate);
    }

    @Override
    public FinanceInvoiceApplicationDO getApplication(Long id) {
        FinanceInvoiceApplicationDO application = applicationMapper.selectById(id);
        if (application == null) {
            throw exception(INVOICE_APPLICATION_NOT_EXISTS);
        }
        return application;
    }

    @Override
    public List<FinanceInvoiceApplicationLineDO> getApplicationLines(Long applicationId) {
        return lineMapper.selectListByApplicationId(applicationId);
    }

    @Override
    public PageResult<FinanceInvoiceApplicationDO> getApplicationPage(FinanceInvoiceApplicationPageReqVO pageReqVO) {
        return applicationMapper.selectPage(pageReqVO);
    }

    /**
     * 按申请当前明细汇总释放 BO 占用（用于 REJECTED/CANCELLED）。
     */
    private void releaseAllOccupyForApplication(Long appId) {
        Map<Long, BigDecimal> occupyByBo = aggregateOccupyByLines(appId);
        for (Map.Entry<Long, BigDecimal> entry : occupyByBo.entrySet()) {
            if (entry.getValue().compareTo(ZERO) <= 0) {
                continue;
            }
            int updated = businessOrderMapper.decreaseInvoicedOccupiedAmount(entry.getKey(), entry.getValue());
            if (updated != 1) {
                throw exception(INVOICE_APPLICATION_RELEASE_OCCUPY_FAILED);
            }
        }
    }

    /**
     * resubmit 前释占：若驳回回调已释完，CAS 可能返回 0；按「占用不足则跳过」处理，避免双放失败。
     */
    private void releaseAllOccupyForApplicationIfAny(Long appId) {
        Map<Long, BigDecimal> occupyByBo = aggregateOccupyByLines(appId);
        for (Map.Entry<Long, BigDecimal> entry : occupyByBo.entrySet()) {
            if (entry.getValue().compareTo(ZERO) <= 0) {
                continue;
            }
            FinanceBusinessOrderDO order = businessOrderMapper.selectById(entry.getKey());
            if (order == null) {
                continue;
            }
            BigDecimal current = defaultZero(order.getInvoicedOccupiedAmount());
            if (current.compareTo(ZERO) <= 0) {
                continue;
            }
            BigDecimal release = entry.getValue().min(current);
            int updated = businessOrderMapper.decreaseInvoicedOccupiedAmount(entry.getKey(), release);
            if (updated != 1) {
                throw exception(INVOICE_APPLICATION_RELEASE_OCCUPY_FAILED);
            }
        }
    }

    private Map<Long, BigDecimal> aggregateOccupyByLines(Long appId) {
        List<FinanceInvoiceApplicationLineDO> lines = lineMapper.selectListByApplicationId(appId);
        Map<Long, BigDecimal> occupyByBo = new LinkedHashMap<>();
        if (CollUtil.isEmpty(lines)) {
            return occupyByBo;
        }
        for (FinanceInvoiceApplicationLineDO line : lines) {
            if (line.getBusinessOrderId() == null || line.getAmount() == null) {
                continue;
            }
            occupyByBo.merge(line.getBusinessOrderId(), line.getAmount(), BigDecimal::add);
        }
        return occupyByBo;
    }

    private void increaseOccupy(Map<Long, BigDecimal> occupyByBo) {
        for (Map.Entry<Long, BigDecimal> entry : occupyByBo.entrySet()) {
            int updated = businessOrderMapper.increaseInvoicedOccupiedAmount(entry.getKey(), entry.getValue());
            if (updated != 1) {
                throw exception(INVOICE_APPLICATION_OCCUPY_CONCURRENT);
            }
        }
    }

    private Map<Long, FinanceBusinessOrderDO> loadBusinessOrders(java.util.Collection<Long> ids) {
        List<FinanceBusinessOrderDO> orders = businessOrderMapper.selectListByIds(ids);
        return orders.stream().collect(Collectors.toMap(FinanceBusinessOrderDO::getId, Function.identity(),
                (a, b) -> a, LinkedHashMap::new));
    }

    private Map<String, Object> buildProcessVariables(FinanceInvoiceApplicationDO application) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("invoiceApplicationId", application.getId());
        variables.put("applicationNo", application.getApplicationNo());
        variables.put("totalAmount", application.getTotalAmount());
        variables.put("buyerName", application.getBuyerName());
        variables.put("buyerTaxNo", application.getBuyerTaxNo());
        variables.put("buyerAddressPhone", application.getBuyerAddressPhone());
        variables.put("buyerBankAccount", application.getBuyerBankAccount());
        variables.put("applicantUserId", application.getApplicantUserId());
        variables.put("approvalStatus", application.getApprovalStatus());
        variables.put("issueStatus", application.getIssueStatus());
        variables.put("expectedInvoiceDate", application.getExpectedInvoiceDate());
        variables.put("invoiceCompany", application.getInvoiceCompany());
        variables.put("invoiceCompanyDeptId", application.getInvoiceCompanyDeptId());
        variables.put("invoiceType", application.getInvoiceType());
        variables.put("taxContent", application.getTaxContent());
        variables.put("taxRate", application.getTaxRate());
        variables.put("voided", application.getVoided());
        return variables;
    }

    private static boolean isTerminalOutcome(String outcome) {
        return FinanceInvoiceApprovalStatusEnum.APPROVED.getStatus().equals(outcome)
                || FinanceInvoiceApprovalStatusEnum.REJECTED.getStatus().equals(outcome)
                || FinanceInvoiceApprovalStatusEnum.CANCELLED.getStatus().equals(outcome);
    }

    private static BigDecimal defaultZero(BigDecimal value) {
        return value == null ? ZERO : value;
    }

}
