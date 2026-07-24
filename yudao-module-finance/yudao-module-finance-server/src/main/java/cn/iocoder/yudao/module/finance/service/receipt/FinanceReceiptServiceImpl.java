package cn.iocoder.yudao.module.finance.service.receipt;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.finance.controller.admin.receipt.vo.FinanceReceiptImportExcelVO;
import cn.iocoder.yudao.module.finance.controller.admin.receipt.vo.FinanceReceiptImportRespVO;
import cn.iocoder.yudao.module.finance.controller.admin.receipt.vo.FinanceReceiptPageReqVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.receipt.FinanceReceiptDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.receipt.FinanceReceiptLifecycleAuditDO;
import cn.iocoder.yudao.module.finance.dal.mysql.receipt.FinanceBankReceiptMapper;
import cn.iocoder.yudao.module.finance.dal.mysql.receipt.FinanceReceiptLifecycleAuditMapper;
import cn.iocoder.yudao.module.finance.dal.redis.no.FinanceReceiptNoRedisDAO;
import cn.iocoder.yudao.module.finance.enums.FinanceReceiptClaimStatusEnum;
import cn.iocoder.yudao.module.finance.enums.FinanceReceiptLifecycleActionEnum;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.*;

@Service
public class FinanceReceiptServiceImpl implements FinanceReceiptService {

    private final FinanceBankReceiptMapper receiptMapper;
    private final FinanceReceiptLifecycleAuditMapper lifecycleAuditMapper;
    private final FinanceReceiptNoRedisDAO receiptNoRedisDAO;

    public FinanceReceiptServiceImpl(FinanceBankReceiptMapper receiptMapper,
                                     FinanceReceiptLifecycleAuditMapper lifecycleAuditMapper,
                                     FinanceReceiptNoRedisDAO receiptNoRedisDAO) {
        this.receiptMapper = receiptMapper;
        this.lifecycleAuditMapper = lifecycleAuditMapper;
        this.receiptNoRedisDAO = receiptNoRedisDAO;
    }

    @Override
    public FinanceReceiptImportRespVO importReceiptList(List<FinanceReceiptImportExcelVO> importReceipts, Long importerId) {
        if (CollUtil.isEmpty(importReceipts)) {
            throw new IllegalArgumentException("导入银行到款数据不能为空");
        }
        FinanceReceiptImportRespVO respVO = FinanceReceiptImportRespVO.builder()
                .receiptNos(new ArrayList<>()).failureRows(new LinkedHashMap<>()).build();
        Set<String> bankSerialNos = new HashSet<>();
        for (int i = 0; i < importReceipts.size(); i++) {
            int rowNumber = i + 2;
            FinanceReceiptImportExcelVO importReceipt = importReceipts.get(i);
            String failureReason = validateImportReceipt(importReceipt, bankSerialNos);
            if (failureReason != null) {
                respVO.getFailureRows().put(rowNumber, failureReason);
                continue;
            }
            if (receiptMapper.selectByBankSerialNo(importReceipt.getBankSerialNo()) != null) {
                respVO.getFailureRows().put(rowNumber, "银行流水号已存在");
                continue;
            }
            bankSerialNos.add(importReceipt.getBankSerialNo());
            String receiptNo = receiptNoRedisDAO.generate(LocalDate.now());
            receiptMapper.insert(buildReceipt(importReceipt, importerId, receiptNo));
            respVO.getReceiptNos().add(receiptNo);
        }
        return respVO;
    }

    @Override
    public PageResult<FinanceReceiptDO> getUnclaimedReceiptPage(FinanceReceiptPageReqVO pageReqVO) {
        return receiptMapper.selectUnclaimedPage(pageReqVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void closeReceipt(Long id, Long operatorId, String reason) {
        if (StrUtil.isBlank(reason)) {
            throw exception(RECEIPT_CLOSE_REASON_REQUIRED);
        }
        FinanceReceiptDO receipt = getRequiredReceipt(id);
        boolean claimableStatus = FinanceReceiptClaimStatusEnum.UNCLAIMED.getStatus().equals(receipt.getClaimStatus())
                || FinanceReceiptClaimStatusEnum.PARTIALLY_CLAIMED.getStatus().equals(receipt.getClaimStatus());
        if (!claimableStatus || receipt.getUnclaimedAmount() == null
                || receipt.getUnclaimedAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw exception(RECEIPT_CLOSE_STATUS_INVALID);
        }
        if (receiptMapper.closeIfStatus(id, receipt.getClaimStatus()) != 1) {
            throw exception(RECEIPT_CONCURRENT_MODIFICATION);
        }
        appendLifecycleAudit(id, operatorId, FinanceReceiptLifecycleActionEnum.CLOSE, reason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reopenReceipt(Long id, Long operatorId, String reason) {
        if (StrUtil.isBlank(reason)) {
            throw exception(RECEIPT_REOPEN_REASON_REQUIRED);
        }
        FinanceReceiptDO receipt = getRequiredReceipt(id);
        if (!FinanceReceiptClaimStatusEnum.CLOSED.getStatus().equals(receipt.getClaimStatus())) {
            throw exception(RECEIPT_REOPEN_STATUS_INVALID);
        }
        if (receiptMapper.reopenIfClosed(id) != 1) {
            throw exception(RECEIPT_CONCURRENT_MODIFICATION);
        }
        appendLifecycleAudit(id, operatorId, FinanceReceiptLifecycleActionEnum.REOPEN, reason);
    }

    @Override
    public List<FinanceReceiptLifecycleAuditDO> getLifecycleAuditList(Long receiptId) {
        return lifecycleAuditMapper.selectListByReceiptId(receiptId);
    }

    private FinanceReceiptDO getRequiredReceipt(Long id) {
        FinanceReceiptDO receipt = receiptMapper.selectById(id);
        if (receipt == null) {
            throw exception(RECEIPT_NOT_EXISTS);
        }
        return receipt;
    }

    private void appendLifecycleAudit(Long receiptId, Long operatorId,
                                      FinanceReceiptLifecycleActionEnum action, String reason) {
        FinanceReceiptLifecycleAuditDO audit = FinanceReceiptLifecycleAuditDO.builder()
                .receiptId(receiptId)
                .action(action.getAction())
                .operatorId(operatorId)
                .actionTime(LocalDateTime.now())
                .reason(reason.trim())
                .build();
        if (lifecycleAuditMapper.insert(audit) != 1) {
            throw exception(RECEIPT_CONCURRENT_MODIFICATION);
        }
    }

    private static String validateImportReceipt(FinanceReceiptImportExcelVO importReceipt, Set<String> bankSerialNos) {
        if (StrUtil.isBlank(importReceipt.getBankAccount())) {
            return "银行账户不能为空";
        }
        if (importReceipt.getTransactionDate() == null) {
            return "交易日期不能为空";
        }
        if (StrUtil.isBlank(importReceipt.getPayerName())) {
            return "付款方名称不能为空";
        }
        if (importReceipt.getTransactionAmount() == null || importReceipt.getTransactionAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return "交易金额必须大于 0";
        }
        if (StrUtil.isBlank(importReceipt.getBankSerialNo())) {
            return "银行流水号不能为空";
        }
        if (bankSerialNos.contains(importReceipt.getBankSerialNo())) {
            return "银行流水号在文件内重复";
        }
        return null;
    }

    private static FinanceReceiptDO buildReceipt(FinanceReceiptImportExcelVO importReceipt, Long importerId, String receiptNo) {
        return FinanceReceiptDO.builder()
                .receiptNo(receiptNo)
                .importDate(LocalDate.now())
                .importerId(importerId)
                .bankAccount(importReceipt.getBankAccount())
                .transactionDate(importReceipt.getTransactionDate())
                .payerName(importReceipt.getPayerName())
                .payerAccount(importReceipt.getPayerAccount())
                .transactionAmount(importReceipt.getTransactionAmount())
                .summary(importReceipt.getSummary())
                .bankSerialNo(importReceipt.getBankSerialNo())
                .claimStatus(FinanceReceiptClaimStatusEnum.UNCLAIMED.getStatus())
                .claimedAmount(BigDecimal.ZERO)
                .unclaimedAmount(importReceipt.getTransactionAmount())
                .build();
    }

}
