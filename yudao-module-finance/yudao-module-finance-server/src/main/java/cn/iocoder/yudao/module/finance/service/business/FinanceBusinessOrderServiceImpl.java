package cn.iocoder.yudao.module.finance.service.business;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.finance.controller.admin.business.vo.FinanceBusinessOrderImportExcelVO;
import cn.iocoder.yudao.module.finance.controller.admin.business.vo.FinanceBusinessOrderImportRespVO;
import cn.iocoder.yudao.module.finance.controller.admin.business.vo.FinanceBusinessOrderPageReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.business.vo.FinanceBusinessOrderSaveReqVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.business.FinanceBusinessOrderDO;
import cn.iocoder.yudao.module.finance.dal.mysql.business.FinanceBusinessOrderMapper;
import cn.iocoder.yudao.module.finance.dal.redis.no.FinanceBusinessOrderNoRedisDAO;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.BUSINESS_ORDER_NOT_EXISTS;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.BUSINESS_ORDER_RECEIVABLE_BELOW_CONFIRMED;

@Service
@Validated
public class FinanceBusinessOrderServiceImpl implements FinanceBusinessOrderService {

    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private final FinanceBusinessOrderMapper businessOrderMapper;
    private final FinanceBusinessOrderNoRedisDAO businessOrderNoRedisDAO;

    public FinanceBusinessOrderServiceImpl(FinanceBusinessOrderMapper businessOrderMapper,
                                           FinanceBusinessOrderNoRedisDAO businessOrderNoRedisDAO) {
        this.businessOrderMapper = businessOrderMapper;
        this.businessOrderNoRedisDAO = businessOrderNoRedisDAO;
    }

    @Override
    public Long createBusinessOrder(FinanceBusinessOrderSaveReqVO createReqVO, Long importerId) {
        FinanceBusinessOrderImportSupport.NormalizedAmounts amounts =
                FinanceBusinessOrderImportSupport.normalizeAmounts(
                        createReqVO.getSignedExecutionAmount(), createReqVO.getDiscountRate());
        validateBusinessOrderSave(createReqVO, amounts);
        LocalDate importDate = LocalDate.now();
        FinanceBusinessOrderDO businessOrder = buildBusinessOrder(createReqVO, amounts);
        businessOrder.setOrderNo(businessOrderNoRedisDAO.generate(importDate));
        businessOrder.setImportDate(importDate);
        businessOrder.setImporterId(importerId);
        businessOrder.setConfirmedClaimedAmount(ZERO);
        businessOrderMapper.insert(businessOrder);
        return businessOrder.getId();
    }

    @Override
    public void updateBusinessOrder(FinanceBusinessOrderSaveReqVO updateReqVO) {
        FinanceBusinessOrderDO currentOrder = validateBusinessOrderExists(updateReqVO.getId());
        FinanceBusinessOrderImportSupport.NormalizedAmounts amounts =
                FinanceBusinessOrderImportSupport.normalizeAmounts(
                        updateReqVO.getSignedExecutionAmount(), updateReqVO.getDiscountRate());
        validateBusinessOrderSave(updateReqVO, amounts);
        BigDecimal confirmedClaimedAmount = currentOrder.getConfirmedClaimedAmount() == null
                ? ZERO : currentOrder.getConfirmedClaimedAmount();
        if (amounts.settlementAmount().compareTo(confirmedClaimedAmount) < 0) {
            throw exception(BUSINESS_ORDER_RECEIVABLE_BELOW_CONFIRMED);
        }
        FinanceBusinessOrderDO updateObj = buildBusinessOrder(updateReqVO, amounts);
        updateObj.setId(currentOrder.getId());
        updateObj.setOrderNo(currentOrder.getOrderNo());
        updateObj.setImportDate(currentOrder.getImportDate());
        updateObj.setImporterId(currentOrder.getImporterId());
        updateObj.setConfirmedClaimedAmount(confirmedClaimedAmount);
        updateObj.setSourceRowHash(currentOrder.getSourceRowHash());
        businessOrderMapper.updateById(updateObj);
    }

    @Override
    public void deleteBusinessOrder(List<Long> ids) {
        ids.forEach(this::validateBusinessOrderExists);
        businessOrderMapper.deleteByIds(ids);
    }

    @Override
    public FinanceBusinessOrderDO getBusinessOrder(Long id) {
        return businessOrderMapper.selectById(id);
    }

    @Override
    public PageResult<FinanceBusinessOrderDO> getBusinessOrderPage(FinanceBusinessOrderPageReqVO pageReqVO) {
        return businessOrderMapper.selectPage(pageReqVO);
    }

    @Override
    public PageResult<FinanceBusinessOrderDO> getClaimableBusinessOrderPage(FinanceBusinessOrderPageReqVO pageReqVO,
                                                                            Long importerId) {
        return businessOrderMapper.selectClaimablePage(pageReqVO, importerId);
    }

    @Override
    public FinanceBusinessOrderImportRespVO importBusinessOrderList(List<FinanceBusinessOrderImportExcelVO> importRows,
                                                                    Long importerId, String bankAccount) {
        if (CollUtil.isEmpty(importRows)) {
            throw new IllegalArgumentException("导入商务签单数据不能为空");
        }
        if (StrUtil.isBlank(bankAccount)) {
            throw new IllegalArgumentException("银行账户不能为空");
        }
        String normalizedBankAccount = bankAccount.trim();
        FinanceBusinessOrderImportRespVO response = FinanceBusinessOrderImportRespVO.builder()
                .orderNos(new ArrayList<>()).failureRows(new LinkedHashMap<>()).skippedRows(new ArrayList<>()).build();
        Set<String> sourceRowHashes = new HashSet<>();
        for (int index = 0; index < importRows.size(); index++) {
            int rowNumber = index + 2;
            FinanceBusinessOrderImportExcelVO row = importRows.get(index);
            FinanceBusinessOrderImportSupport.NormalizedAmounts amounts =
                    FinanceBusinessOrderImportSupport.normalizeAmounts(
                            row == null ? null : row.getSignedExecutionAmount(),
                            row == null ? null : row.getDiscountRate());
            String failureReason = FinanceBusinessOrderImportSupport.validateRow(row, amounts);
            if (failureReason != null) {
                response.getFailureRows().put(rowNumber, failureReason);
                continue;
            }
            String sourceRowHash = FinanceBusinessOrderImportSupport.calculateSourceRowHash(
                    row, amounts, normalizedBankAccount);
            if (!sourceRowHashes.add(sourceRowHash)
                    || businessOrderMapper.selectBySourceRowHash(sourceRowHash) != null) {
                response.getSkippedRows().add(rowNumber);
                continue;
            }
            String orderNo = businessOrderNoRedisDAO.generate(LocalDate.now());
            businessOrderMapper.insert(FinanceBusinessOrderImportSupport.buildOrder(row, importerId,
                    normalizedBankAccount, amounts, sourceRowHash, orderNo));
            response.getOrderNos().add(orderNo);
        }
        return response;
    }

    private FinanceBusinessOrderDO validateBusinessOrderExists(Long id) {
        FinanceBusinessOrderDO businessOrder = businessOrderMapper.selectById(id);
        if (businessOrder == null) {
            throw exception(BUSINESS_ORDER_NOT_EXISTS);
        }
        return businessOrder;
    }

    private static void validateBusinessOrderSave(FinanceBusinessOrderSaveReqVO reqVO,
                                                  FinanceBusinessOrderImportSupport.NormalizedAmounts amounts) {
        if (StrUtil.isBlank(reqVO.getBankAccount())) {
            throw new IllegalArgumentException("银行账户不能为空");
        }
        if (reqVO.getOrderDate() == null || StrUtil.isBlank(reqVO.getProductName())
                || StrUtil.isBlank(reqVO.getContactPerson()) || reqVO.getExecutionStartDate() == null
                || reqVO.getExecutionEndDate() == null) {
            throw new IllegalArgumentException("下单日期、产品名称、对接人和执行日期不能为空");
        }
        if (reqVO.getExecutionEndDate().isBefore(reqVO.getExecutionStartDate())) {
            throw new IllegalArgumentException("执行截止日不能早于执行开始日");
        }
        if (amounts.signedExecutionAmount() == null
                || amounts.signedExecutionAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("签单执行金额必须大于 0");
        }
        if (amounts.discountRate().compareTo(BigDecimal.ZERO) < 0
                || amounts.discountRate().compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("折扣率必须在 0 到 1 之间");
        }
    }

    private static FinanceBusinessOrderDO buildBusinessOrder(FinanceBusinessOrderSaveReqVO reqVO,
                                                               FinanceBusinessOrderImportSupport.NormalizedAmounts amounts) {
        return FinanceBusinessOrderDO.builder()
                .bankAccount(reqVO.getBankAccount().trim())
                .contractProcessId(trimToNull(reqVO.getContractProcessId()))
                .orderDate(reqVO.getOrderDate()).productName(reqVO.getProductName().trim())
                .contactPerson(reqVO.getContactPerson().trim()).executionStartDate(reqVO.getExecutionStartDate())
                .executionEndDate(reqVO.getExecutionEndDate()).payerName(trimToNull(reqVO.getPayerName()))
                .signedExecutionAmount(amounts.signedExecutionAmount()).discountRate(amounts.discountRate())
                .settlementAmount(amounts.settlementAmount()).remark(trimToNull(reqVO.getRemark())).build();
    }

    private static String trimToNull(String value) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        return value.trim();
    }

}
