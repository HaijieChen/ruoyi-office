package cn.iocoder.yudao.module.finance.service.invoice;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.yudao.module.finance.controller.admin.invoice.vo.FinanceInvoiceApplicationCompleteIssueReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.invoice.vo.FinanceInvoiceRedflushCreateAndStartReqVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.invoice.FinanceInvoiceApplicationDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.invoice.FinanceInvoiceRedflushDO;
import cn.iocoder.yudao.module.finance.dal.mysql.invoice.FinanceInvoiceApplicationMapper;
import cn.iocoder.yudao.module.finance.dal.mysql.invoice.FinanceInvoiceRedflushMapper;
import cn.iocoder.yudao.module.finance.dal.redis.no.FinanceInvoiceRedflushNoRedisDAO;
import cn.iocoder.yudao.module.finance.enums.FinanceInvoiceApprovalStatusEnum;
import cn.iocoder.yudao.module.finance.enums.FinanceInvoiceIssueStatusEnum;
import cn.iocoder.yudao.module.finance.framework.rpc.FinanceBpmProcessInstanceApi;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.*;

@Service
@Validated
public class FinanceInvoiceRedflushServiceImpl implements FinanceInvoiceRedflushService {

    private final FinanceInvoiceRedflushMapper redflushMapper;
    private final FinanceInvoiceApplicationMapper applicationMapper;
    private final FinanceInvoiceRedflushNoRedisDAO noRedisDAO;
    private final FinanceBpmProcessInstanceApi processInstanceApi;
    private final FinanceInvoiceApplicationService invoiceApplicationService;

    public FinanceInvoiceRedflushServiceImpl(FinanceInvoiceRedflushMapper redflushMapper,
                                             FinanceInvoiceApplicationMapper applicationMapper,
                                             FinanceInvoiceRedflushNoRedisDAO noRedisDAO,
                                             FinanceBpmProcessInstanceApi processInstanceApi,
                                             FinanceInvoiceApplicationService invoiceApplicationService) {
        this.redflushMapper = redflushMapper;
        this.applicationMapper = applicationMapper;
        this.noRedisDAO = noRedisDAO;
        this.processInstanceApi = processInstanceApi;
        this.invoiceApplicationService = invoiceApplicationService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createAndStart(FinanceInvoiceRedflushCreateAndStartReqVO reqVO, Long applicantUserId) {
        FinanceInvoiceApplicationDO predecessor = requireSelectablePredecessor(reqVO);
        FinanceInvoiceRedflushDO row = FinanceInvoiceRedflushDO.builder()
                .applicationNo(noRedisDAO.generate(LocalDate.now()))
                .predecessorApplicationId(predecessor.getId())
                .approvalStatus(FinanceInvoiceApprovalStatusEnum.PENDING.getStatus())
                .issueStatus(FinanceInvoiceIssueStatusEnum.NONE.getStatus())
                .reason(reqVO.getReason().trim())
                .specialNote(StrUtil.blankToDefault(reqVO.getSpecialNote(), null))
                .totalAmount(predecessor.getTotalAmount())
                .currency(predecessor.getCurrency())
                .applicantUserId(applicantUserId)
                .voided(Boolean.FALSE)
                .build();
        redflushMapper.insert(row);
        if (applicationMapper.tryLockForRedFlush(predecessor.getId(), row.getId()) != 1) {
            throw exception(INVOICE_REDFUSH_LOCK_FAILED);
        }
        startProcess(row, applicantUserId, reqVO);
        return row.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resubmit(Long id, FinanceInvoiceRedflushCreateAndStartReqVO reqVO, Long userId) {
        FinanceInvoiceRedflushDO current = redflushMapper.selectById(id);
        if (current == null) {
            throw exception(INVOICE_REDFUSH_NOT_EXISTS);
        }
        if (!FinanceInvoiceApprovalStatusEnum.REJECTED.getStatus().equals(current.getApprovalStatus())
                || Boolean.TRUE.equals(current.getVoided())) {
            throw exception(INVOICE_REDFUSH_STATUS_INVALID);
        }
        applicationMapper.unlockRedFlush(current.getPredecessorApplicationId(), id);
        FinanceInvoiceApplicationDO predecessor = requireSelectablePredecessor(reqVO);
        FinanceInvoiceRedflushDO update = new FinanceInvoiceRedflushDO();
        update.setId(id);
        update.setPredecessorApplicationId(predecessor.getId());
        update.setReason(reqVO.getReason().trim());
        update.setSpecialNote(StrUtil.blankToDefault(reqVO.getSpecialNote(), null));
        update.setTotalAmount(predecessor.getTotalAmount());
        update.setCurrency(predecessor.getCurrency());
        update.setApprovalStatus(FinanceInvoiceApprovalStatusEnum.PENDING.getStatus());
        update.setIssueStatus(FinanceInvoiceIssueStatusEnum.NONE.getStatus());
        update.setVoided(Boolean.FALSE);
        redflushMapper.updateById(update);
        if (applicationMapper.tryLockForRedFlush(predecessor.getId(), id) != 1) {
            throw exception(INVOICE_REDFUSH_LOCK_FAILED);
        }
        current.setPredecessorApplicationId(predecessor.getId());
        current.setReason(reqVO.getReason().trim());
        current.setTotalAmount(predecessor.getTotalAmount());
        startProcess(current, userId, reqVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onApprovalOutcome(Long redflushId, String outcome) {
        FinanceInvoiceRedflushDO row = redflushMapper.selectById(redflushId);
        if (row == null) {
            throw exception(INVOICE_REDFUSH_NOT_EXISTS);
        }
        String normalized = outcome == null ? "" : outcome.trim().toUpperCase();
        if (FinanceInvoiceApprovalStatusEnum.APPROVED.getStatus().equals(normalized)) {
            if (FinanceInvoiceApprovalStatusEnum.PENDING.getStatus().equals(row.getApprovalStatus())) {
                FinanceInvoiceRedflushDO update = new FinanceInvoiceRedflushDO();
                update.setId(redflushId);
                update.setApprovalStatus(FinanceInvoiceApprovalStatusEnum.APPROVED.getStatus());
                redflushMapper.updateById(update);
            }
            return;
        }
        if (FinanceInvoiceApprovalStatusEnum.REJECTED.getStatus().equals(normalized)
                || FinanceInvoiceApprovalStatusEnum.CANCELLED.getStatus().equals(normalized)) {
            FinanceInvoiceRedflushDO update = new FinanceInvoiceRedflushDO();
            update.setId(redflushId);
            update.setApprovalStatus(normalized);
            if (FinanceInvoiceApprovalStatusEnum.CANCELLED.getStatus().equals(normalized)) {
                update.setVoided(Boolean.TRUE);
            }
            redflushMapper.updateById(update);
            applicationMapper.unlockRedFlush(row.getPredecessorApplicationId(), redflushId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completeIssue(Long redflushId, FinanceInvoiceApplicationCompleteIssueReqVO reqVO) {
        FinanceInvoiceRedflushDO row = redflushMapper.selectById(redflushId);
        if (row == null) {
            throw exception(INVOICE_REDFUSH_NOT_EXISTS);
        }
        if (!FinanceInvoiceApprovalStatusEnum.APPROVED.getStatus().equals(row.getApprovalStatus())
                || Boolean.TRUE.equals(row.getVoided())) {
            throw exception(INVOICE_REDFUSH_STATUS_INVALID);
        }
        if (reqVO == null || reqVO.getFiles() == null || reqVO.getFiles().isEmpty()) {
            throw exception(INVOICE_APPLICATION_COMPLETE_ISSUE_FILES_EMPTY);
        }
        if (Integer.valueOf(FinanceInvoiceIssueStatusEnum.FULL.getStatus()).equals(row.getIssueStatus())) {
            return;
        }
        invoiceApplicationService.releaseOccupyForRedFlush(row.getPredecessorApplicationId());
        FinanceInvoiceRedflushDO update = new FinanceInvoiceRedflushDO();
        update.setId(redflushId);
        update.setIssueStatus(FinanceInvoiceIssueStatusEnum.FULL.getStatus());
        redflushMapper.updateById(update);
    }

    @Override
    public FinanceInvoiceRedflushDO get(Long id) {
        FinanceInvoiceRedflushDO row = redflushMapper.selectById(id);
        if (row == null) {
            throw exception(INVOICE_REDFUSH_NOT_EXISTS);
        }
        return row;
    }

    private FinanceInvoiceApplicationDO requireSelectablePredecessor(FinanceInvoiceRedflushCreateAndStartReqVO reqVO) {
        if (StrUtil.isBlank(reqVO.getReason())) {
            throw exception(INVOICE_REDFUSH_REASON_REQUIRED);
        }
        FinanceInvoiceApplicationDO predecessor = applicationMapper.selectById(reqVO.getPredecessorApplicationId());
        if (!FinanceInvoiceRedflushEligibility.isSelectablePredecessor(predecessor)) {
            throw exception(INVOICE_REDFUSH_PREDECESSOR_INVALID);
        }
        if (reqVO.getTotalAmount() != null
                && predecessor.getTotalAmount() != null
                && reqVO.getTotalAmount().compareTo(predecessor.getTotalAmount()) != 0) {
            throw exception(INVOICE_REDFUSH_AMOUNT_MISMATCH);
        }
        return predecessor;
    }

    private void startProcess(FinanceInvoiceRedflushDO row, Long userId,
                              FinanceInvoiceRedflushCreateAndStartReqVO reqVO) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("totalAmount", row.getTotalAmount());
        if (reqVO.getStartCompanyDeptId() != null) {
            variables.put("startCompanyDeptId", reqVO.getStartCompanyDeptId());
        }
        if (reqVO.getStartDeptId() != null) {
            variables.put("startDeptId", reqVO.getStartDeptId());
        }
        BpmProcessInstanceCreateReqDTO createReqDTO = new BpmProcessInstanceCreateReqDTO();
        createReqDTO.setProcessDefinitionKey(PROCESS_KEY);
        createReqDTO.setBusinessKey(String.valueOf(row.getId()));
        createReqDTO.setVariables(variables);
        createReqDTO.setStartUserSelectAssignees(reqVO.getStartUserSelectAssignees());
        String processInstanceId = processInstanceApi.createProcessInstance(userId, createReqDTO)
                .getCheckedData();
        FinanceInvoiceRedflushDO processUpdate = new FinanceInvoiceRedflushDO();
        processUpdate.setId(row.getId());
        processUpdate.setProcessInstanceId(processInstanceId);
        redflushMapper.updateById(processUpdate);
    }
}
