package cn.iocoder.yudao.module.finance.service.receipt;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.finance.controller.admin.receipt.vo.FinanceReceiptImportExcelVO;
import cn.iocoder.yudao.module.finance.controller.admin.receipt.vo.FinanceReceiptImportRespVO;
import cn.iocoder.yudao.module.finance.controller.admin.receipt.vo.FinanceReceiptPageReqVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.receipt.FinanceReceiptDO;
import cn.iocoder.yudao.module.finance.dal.mysql.receipt.FinanceReceiptMapper;
import cn.iocoder.yudao.module.finance.dal.redis.no.FinanceReceiptNoRedisDAO;
import cn.iocoder.yudao.module.finance.enums.FinanceReceiptClaimStatusEnum;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
public class FinanceReceiptServiceImpl implements FinanceReceiptService {

    private final FinanceReceiptMapper receiptMapper;
    private final FinanceReceiptNoRedisDAO receiptNoRedisDAO;

    public FinanceReceiptServiceImpl(FinanceReceiptMapper receiptMapper, FinanceReceiptNoRedisDAO receiptNoRedisDAO) {
        this.receiptMapper = receiptMapper;
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
