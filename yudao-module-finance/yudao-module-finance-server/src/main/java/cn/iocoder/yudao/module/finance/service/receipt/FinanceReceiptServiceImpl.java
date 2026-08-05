package cn.iocoder.yudao.module.finance.service.receipt;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.finance.controller.admin.receipt.vo.FinanceReceiptImportExcelVO;
import cn.iocoder.yudao.module.finance.controller.admin.receipt.vo.FinanceReceiptImportRespVO;
import cn.iocoder.yudao.module.finance.controller.admin.receipt.vo.FinanceReceiptPageReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.receipt.vo.FinanceReceiptSaveReqVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.receipt.FinanceReceiptDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.receipt.FinanceReceiptLifecycleAuditDO;
import cn.iocoder.yudao.module.finance.dal.mysql.receipt.FinanceBankReceiptMapper;
import cn.iocoder.yudao.module.finance.dal.mysql.receipt.FinanceReceiptLifecycleAuditMapper;
import cn.iocoder.yudao.module.finance.dal.redis.no.FinanceReceiptNoRedisDAO;
import cn.iocoder.yudao.module.finance.enums.FinanceReceiptClaimStatusEnum;
import cn.iocoder.yudao.module.finance.enums.FinanceReceiptLifecycleActionEnum;
import cn.iocoder.yudao.module.finance.service.common.FinanceEntityCompanyResolver;
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
    private final FinanceEntityCompanyResolver entityCompanyResolver;

    public FinanceReceiptServiceImpl(FinanceBankReceiptMapper receiptMapper,
                                     FinanceReceiptLifecycleAuditMapper lifecycleAuditMapper,
                                     FinanceReceiptNoRedisDAO receiptNoRedisDAO,
                                     FinanceEntityCompanyResolver entityCompanyResolver) {
        this.receiptMapper = receiptMapper;
        this.lifecycleAuditMapper = lifecycleAuditMapper;
        this.receiptNoRedisDAO = receiptNoRedisDAO;
        this.entityCompanyResolver = entityCompanyResolver;
    }

    @Override
    public Long createReceipt(FinanceReceiptSaveReqVO createReqVO, Long importerId) {
        validateWritableReceipt(createReqVO, null);
        FinanceEntityCompanyResolver.ResolvedCompany company =
                entityCompanyResolver.requireByDeptId(createReqVO.getEntityCompanyDeptId());
        String receiptNo = receiptNoRedisDAO.generate(LocalDate.now());
        FinanceReceiptDO receipt = buildReceiptFromSave(createReqVO, importerId, receiptNo, company);
        receiptMapper.insert(receipt);
        return receipt.getId();
    }

    @Override
    public void updateReceipt(FinanceReceiptSaveReqVO updateReqVO) {
        FinanceReceiptDO current = getRequiredReceipt(updateReqVO.getId());
        validateUnclaimedEditable(current, false);
        validateWritableReceipt(updateReqVO, current.getId());
        FinanceEntityCompanyResolver.ResolvedCompany company =
                entityCompanyResolver.requireByDeptId(updateReqVO.getEntityCompanyDeptId());
        FinanceReceiptDO update = buildReceiptFromSave(updateReqVO, current.getImporterId(), current.getReceiptNo(), company);
        update.setId(current.getId());
        update.setImportDate(current.getImportDate());
        update.setClaimStatus(FinanceReceiptClaimStatusEnum.UNCLAIMED.getStatus());
        update.setClaimedAmount(BigDecimal.ZERO);
        update.setUnclaimedAmount(updateReqVO.getTransactionAmount());
        receiptMapper.updateById(update);
    }

    @Override
    public void deleteReceipt(List<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return;
        }
        for (Long id : ids) {
            FinanceReceiptDO receipt = getRequiredReceipt(id);
            validateUnclaimedEditable(receipt, true);
            receiptMapper.deleteById(id);
        }
    }

    @Override
    public FinanceReceiptDO getReceipt(Long id) {
        return getRequiredReceipt(id);
    }

    @Override
    public PageResult<FinanceReceiptDO> getReceiptPage(FinanceReceiptPageReqVO pageReqVO) {
        return receiptMapper.selectReceiptPage(pageReqVO);
    }

    @Override
    public FinanceReceiptImportRespVO importReceiptList(List<FinanceReceiptImportExcelVO> importReceipts, Long importerId) {
        if (CollUtil.isEmpty(importReceipts)) {
            throw new IllegalArgumentException("导入银行到款数据不能为空");
        }
        FinanceReceiptImportRespVO respVO = FinanceReceiptImportRespVO.builder()
                .receiptNos(new ArrayList<>()).failureRows(new LinkedHashMap<>()).build();
        Set<String> bankSerialNos = new HashSet<>();
        // 导入缓存公司列表，避免每行 RPC
        var enabledCompanies = entityCompanyResolver.loadEnabledCompanies();
        for (int i = 0; i < importReceipts.size(); i++) {
            int rowNumber = i + 2;
            FinanceReceiptImportExcelVO importReceipt = importReceipts.get(i);
            String failureReason = validateImportReceipt(importReceipt, bankSerialNos);
            if (failureReason != null) {
                respVO.getFailureRows().put(rowNumber, failureReason);
                continue;
            }
            FinanceEntityCompanyResolver.ResolvedCompany[] companyOut =
                    new FinanceEntityCompanyResolver.ResolvedCompany[1];
            String companyError = entityCompanyResolver.matchByNameOrError(
                    importReceipt.getEntityCompanyName(), companyOut, enabledCompanies);
            if (companyError != null) {
                respVO.getFailureRows().put(rowNumber, companyError);
                continue;
            }
            // validate 已保证日期可解析
            LocalDateTime transactionDate = FinanceReceiptImportDateParser.tryParse(importReceipt.getTransactionDate());
            if (receiptMapper.selectByBankSerialNo(importReceipt.getBankSerialNo()) != null) {
                respVO.getFailureRows().put(rowNumber, "银行流水号已存在");
                continue;
            }
            bankSerialNos.add(importReceipt.getBankSerialNo());
            String receiptNo = receiptNoRedisDAO.generate(LocalDate.now());
            receiptMapper.insert(buildReceipt(importReceipt, importerId, receiptNo, transactionDate, companyOut[0]));
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
    public void closeReceipt(Long id, Long operatorId, String operatorName, String reason) {
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
        appendLifecycleAudit(id, operatorId, operatorName, FinanceReceiptLifecycleActionEnum.CLOSE, reason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reopenReceipt(Long id, Long operatorId, String operatorName, String reason) {
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
        appendLifecycleAudit(id, operatorId, operatorName, FinanceReceiptLifecycleActionEnum.REOPEN, reason);
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

    private void validateUnclaimedEditable(FinanceReceiptDO receipt, boolean forDelete) {
        boolean unclaimed = FinanceReceiptClaimStatusEnum.UNCLAIMED.getStatus().equals(receipt.getClaimStatus());
        boolean zeroClaimed = receipt.getClaimedAmount() == null
                || receipt.getClaimedAmount().compareTo(BigDecimal.ZERO) == 0;
        if (!unclaimed || !zeroClaimed) {
            throw exception(forDelete ? RECEIPT_DELETE_STATUS_INVALID : RECEIPT_UPDATE_STATUS_INVALID);
        }
    }

    private void validateWritableReceipt(FinanceReceiptSaveReqVO reqVO, Long excludeId) {
        if (reqVO.getEntityCompanyDeptId() == null
                || StrUtil.isBlank(reqVO.getBankAccount()) || reqVO.getTransactionDate() == null
                || reqVO.getTransactionAmount() == null
                || reqVO.getTransactionAmount().compareTo(BigDecimal.ZERO) <= 0
                || StrUtil.isBlank(reqVO.getBankSerialNo())
                || reqVO.getBusinessFund() == null) {
            throw new IllegalArgumentException(
                    "主体公司、银行账户、交易日期、交易金额、银行流水号和是否业务款不能为空，且金额须大于 0");
        }
        // 业务款必须填付款方；非业务款可选
        if (Boolean.TRUE.equals(reqVO.getBusinessFund()) && StrUtil.isBlank(reqVO.getPayerName())) {
            throw new IllegalArgumentException("业务款时付款方名称不能为空");
        }
        if (reqVO.getFundTypeRemark() != null && reqVO.getFundTypeRemark().length() > 255) {
            throw new IllegalArgumentException("款项类型备注长度不能超过 255");
        }
        FinanceReceiptDO exists = receiptMapper.selectByBankSerialNo(reqVO.getBankSerialNo().trim());
        if (exists != null && (excludeId == null || !exists.getId().equals(excludeId))) {
            throw exception(RECEIPT_BANK_SERIAL_NO_EXISTS);
        }
    }

    private static FinanceReceiptDO buildReceiptFromSave(FinanceReceiptSaveReqVO reqVO, Long importerId,
                                                         String receiptNo,
                                                         FinanceEntityCompanyResolver.ResolvedCompany company) {
        return FinanceReceiptDO.builder()
                .receiptNo(receiptNo)
                .importDate(LocalDate.now())
                .importerId(importerId)
                .bankAccount(reqVO.getBankAccount().trim())
                .entityCompanyDeptId(company.deptId())
                .entityCompanyName(company.name())
                .transactionDate(reqVO.getTransactionDate())
                .payerName(trimToNull(reqVO.getPayerName()))
                .payerAccount(trimToNull(reqVO.getPayerAccount()))
                .transactionAmount(reqVO.getTransactionAmount())
                .summary(trimToNull(reqVO.getSummary()))
                .bankSerialNo(reqVO.getBankSerialNo().trim())
                .businessFund(reqVO.getBusinessFund())
                .fundTypeRemark(trimToNull(reqVO.getFundTypeRemark()))
                .claimStatus(FinanceReceiptClaimStatusEnum.UNCLAIMED.getStatus())
                .claimedAmount(BigDecimal.ZERO)
                .unclaimedAmount(reqVO.getTransactionAmount())
                .build();
    }

    private static String trimToNull(String value) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        return value.trim();
    }

    private void appendLifecycleAudit(Long receiptId, Long operatorId, String operatorName,
                                      FinanceReceiptLifecycleActionEnum action, String reason) {
        FinanceReceiptLifecycleAuditDO audit = FinanceReceiptLifecycleAuditDO.builder()
                .receiptId(receiptId)
                .action(action.getAction())
                .operatorId(operatorId)
                .operatorName(StrUtil.blankToDefault(StrUtil.trim(operatorName), null))
                .actionTime(LocalDateTime.now())
                .reason(reason.trim())
                .build();
        if (lifecycleAuditMapper.insert(audit) != 1) {
            throw exception(RECEIPT_CONCURRENT_MODIFICATION);
        }
    }

    private static String validateImportReceipt(FinanceReceiptImportExcelVO importReceipt, Set<String> bankSerialNos) {
        if (StrUtil.isBlank(importReceipt.getEntityCompanyName())) {
            return "主体公司不能为空";
        }
        if (StrUtil.isBlank(importReceipt.getBankAccount())) {
            return "银行账户不能为空";
        }
        if (FinanceReceiptImportDateParser.isBlank(importReceipt.getTransactionDate())) {
            return "交易日期不能为空";
        }
        if (FinanceReceiptImportDateParser.tryParse(importReceipt.getTransactionDate()) == null) {
            return "交易日期格式错误";
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
        Boolean businessFund = parseImportBusinessFund(importReceipt.getBusinessFund());
        if (businessFund == null) {
            return "是否业务款格式错误（空=否；支持是/否、Y/N、true/false、1/0）";
        }
        // 业务款必须填付款方；非业务款可选
        if (Boolean.TRUE.equals(businessFund) && StrUtil.isBlank(importReceipt.getPayerName())) {
            return "业务款时付款方名称不能为空";
        }
        if (importReceipt.getFundTypeRemark() != null && importReceipt.getFundTypeRemark().length() > 255) {
            return "款项类型备注长度不能超过 255";
        }
        return null;
    }

    /**
     * 导入「是否业务款」：空/空白 → false；可识别真值/假值 → 对应布尔；无法识别 → null（校验失败）。
     */
    static Boolean parseImportBusinessFund(String raw) {
        if (StrUtil.isBlank(raw)) {
            return Boolean.FALSE;
        }
        String v = raw.trim();
        if ("是".equals(v) || "Y".equalsIgnoreCase(v) || "YES".equalsIgnoreCase(v)
                || "TRUE".equalsIgnoreCase(v) || "1".equals(v)) {
            return Boolean.TRUE;
        }
        if ("否".equals(v) || "N".equalsIgnoreCase(v) || "NO".equalsIgnoreCase(v)
                || "FALSE".equalsIgnoreCase(v) || "0".equals(v)) {
            return Boolean.FALSE;
        }
        return null;
    }

    private static FinanceReceiptDO buildReceipt(FinanceReceiptImportExcelVO importReceipt, Long importerId,
                                                 String receiptNo, LocalDateTime transactionDate,
                                                 FinanceEntityCompanyResolver.ResolvedCompany company) {
        Boolean businessFund = parseImportBusinessFund(importReceipt.getBusinessFund());
        // validateImportReceipt 已保证非 null
        if (businessFund == null) {
            businessFund = Boolean.FALSE;
        }
        return FinanceReceiptDO.builder()
                .receiptNo(receiptNo)
                .importDate(LocalDate.now())
                .importerId(importerId)
                .bankAccount(importReceipt.getBankAccount())
                .entityCompanyDeptId(company.deptId())
                .entityCompanyName(company.name())
                .transactionDate(transactionDate)
                .payerName(trimToNull(importReceipt.getPayerName()))
                .payerAccount(trimToNull(importReceipt.getPayerAccount()))
                .transactionAmount(importReceipt.getTransactionAmount())
                .summary(trimToNull(importReceipt.getSummary()))
                .bankSerialNo(importReceipt.getBankSerialNo().trim())
                .businessFund(businessFund)
                .fundTypeRemark(trimToNull(importReceipt.getFundTypeRemark()))
                .claimStatus(FinanceReceiptClaimStatusEnum.UNCLAIMED.getStatus())
                .claimedAmount(BigDecimal.ZERO)
                .unclaimedAmount(importReceipt.getTransactionAmount())
                .build();
    }

}
