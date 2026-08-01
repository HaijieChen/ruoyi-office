package cn.iocoder.yudao.module.finance.service.business;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.finance.controller.admin.business.vo.FinanceBusinessOrderImportExcelVO;
import cn.iocoder.yudao.module.finance.controller.admin.business.vo.FinanceBusinessOrderImportRespVO;
import cn.iocoder.yudao.module.finance.controller.admin.business.vo.FinanceBusinessOrderPageReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.business.vo.FinanceBusinessOrderSaveReqVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.business.FinanceBusinessOrderDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.contract.FinanceContractApplicationDO;
import cn.iocoder.yudao.module.finance.dal.mysql.business.FinanceBusinessOrderMapper;
import cn.iocoder.yudao.module.finance.dal.mysql.contract.FinanceContractApplicationMapper;
import cn.iocoder.yudao.module.finance.dal.redis.no.FinanceBusinessOrderNoRedisDAO;
import cn.iocoder.yudao.module.finance.enums.FinanceContractApprovalStatusEnum;
import cn.iocoder.yudao.module.finance.service.common.FinanceEntityCompanyResolver;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.BUSINESS_ORDER_CONTRACT_CHANGE_FORBIDDEN;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.BUSINESS_ORDER_CONTRACT_CLEAR_FORBIDDEN;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.BUSINESS_ORDER_CONTRACT_INVALID;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.BUSINESS_ORDER_CONTRACT_REQUIRED;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.BUSINESS_ORDER_DELETE_HAS_CLAIM;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.BUSINESS_ORDER_NOT_EXISTS;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.BUSINESS_ORDER_RECEIVABLE_BELOW_CONFIRMED;

@Service
@Validated
public class FinanceBusinessOrderServiceImpl implements FinanceBusinessOrderService {

    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private final FinanceBusinessOrderMapper businessOrderMapper;
    private final FinanceBusinessOrderNoRedisDAO businessOrderNoRedisDAO;
    private final FinanceContractApplicationMapper contractApplicationMapper;
    private final FinanceEntityCompanyResolver entityCompanyResolver;

    public FinanceBusinessOrderServiceImpl(FinanceBusinessOrderMapper businessOrderMapper,
                                           FinanceBusinessOrderNoRedisDAO businessOrderNoRedisDAO,
                                           FinanceContractApplicationMapper contractApplicationMapper,
                                           FinanceEntityCompanyResolver entityCompanyResolver) {
        this.businessOrderMapper = businessOrderMapper;
        this.businessOrderNoRedisDAO = businessOrderNoRedisDAO;
        this.contractApplicationMapper = contractApplicationMapper;
        this.entityCompanyResolver = entityCompanyResolver;
    }

    @Override
    public Long createBusinessOrder(FinanceBusinessOrderSaveReqVO createReqVO, Long importerId) {
        FinanceBusinessOrderImportSupport.NormalizedAmounts amounts =
                FinanceBusinessOrderImportSupport.normalizeAmounts(
                        createReqVO.getSignedExecutionAmount(), createReqVO.getDiscountRate());
        validateBusinessOrderSave(createReqVO, amounts);
        FinanceEntityCompanyResolver.ResolvedCompany company =
                entityCompanyResolver.requireByDeptId(createReqVO.getEntityCompanyDeptId());
        // 新数据硬强制：必须关联已通过且本人申请的合同（C4/C18）
        Long contractAppId = requireSelectableContract(createReqVO.getContractApplicationId(), importerId);
        LocalDate importDate = LocalDate.now();
        FinanceBusinessOrderDO businessOrder = buildBusinessOrder(createReqVO, amounts, company);
        businessOrder.setContractApplicationId(contractAppId);
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
        FinanceEntityCompanyResolver.ResolvedCompany company =
                entityCompanyResolver.requireByDeptId(updateReqVO.getEntityCompanyDeptId());
        BigDecimal confirmedClaimedAmount = currentOrder.getConfirmedClaimedAmount() == null
                ? ZERO : currentOrder.getConfirmedClaimedAmount();
        if (amounts.settlementAmount().compareTo(confirmedClaimedAmount) < 0) {
            throw exception(BUSINESS_ORDER_RECEIVABLE_BELOW_CONFIRMED);
        }
        Long resolvedContractId = resolveContractOnUpdate(currentOrder, updateReqVO, currentOrder.getImporterId());
        FinanceBusinessOrderDO updateObj = buildBusinessOrder(updateReqVO, amounts, company);
        updateObj.setId(currentOrder.getId());
        updateObj.setOrderNo(currentOrder.getOrderNo());
        updateObj.setImportDate(currentOrder.getImportDate());
        updateObj.setImporterId(currentOrder.getImporterId());
        updateObj.setConfirmedClaimedAmount(confirmedClaimedAmount);
        updateObj.setSourceRowHash(currentOrder.getSourceRowHash());
        updateObj.setContractApplicationId(resolvedContractId);
        // 保留 legacy 脏文本列（未关联展示）；正式关联只认 contractApplicationId
        if (resolvedContractId != null) {
            updateObj.setContractProcessId(currentOrder.getContractProcessId());
        } else {
            updateObj.setContractProcessId(currentOrder.getContractProcessId());
        }
        businessOrderMapper.updateById(updateObj);
    }

    @Override
    public void deleteBusinessOrder(List<Long> ids) {
        for (Long id : ids) {
            FinanceBusinessOrderDO order = validateBusinessOrderExists(id);
            BigDecimal confirmed = order.getConfirmedClaimedAmount() == null
                    ? ZERO : order.getConfirmedClaimedAmount();
            if (confirmed.compareTo(BigDecimal.ZERO) > 0) {
                throw exception(BUSINESS_ORDER_DELETE_HAS_CLAIM);
            }
        }
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
                                                                    Long importerId) {
        if (CollUtil.isEmpty(importRows)) {
            throw new IllegalArgumentException("导入商务签单数据不能为空");
        }
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
            FinanceEntityCompanyResolver.ResolvedCompany[] companyOut =
                    new FinanceEntityCompanyResolver.ResolvedCompany[1];
            String companyError = entityCompanyResolver.matchByNameOrError(
                    row.getEntityCompanyName(), companyOut);
            if (companyError != null) {
                response.getFailureRows().put(rowNumber, companyError);
                continue;
            }
            FinanceEntityCompanyResolver.ResolvedCompany company = companyOut[0];
            String sourceRowHash = FinanceBusinessOrderImportSupport.calculateSourceRowHash(
                    row, amounts, company.deptId());
            if (!sourceRowHashes.add(sourceRowHash)
                    || businessOrderMapper.selectBySourceRowHash(sourceRowHash) != null) {
                response.getSkippedRows().add(rowNumber);
                continue;
            }
            Long contractAppId;
            try {
                contractAppId = resolveContractByApplicationNo(row.getContractApplicationNo(), importerId);
            } catch (Exception ex) {
                response.getFailureRows().put(rowNumber, "合同申请业务单号无效：未找到已通过且本人申请的合同");
                continue;
            }
            String orderNo = businessOrderNoRedisDAO.generate(LocalDate.now());
            businessOrderMapper.insert(FinanceBusinessOrderImportSupport.buildOrder(row, importerId,
                    company.deptId(), company.name(), amounts, sourceRowHash, orderNo, contractAppId));
            response.getOrderNos().add(orderNo);
        }
        return response;
    }

    /**
     * 新建/导入：合同必须 APPROVED 且申请人=当前用户。
     */
    private Long requireSelectableContract(Long contractApplicationId, Long userId) {
        if (contractApplicationId == null) {
            throw exception(BUSINESS_ORDER_CONTRACT_REQUIRED);
        }
        FinanceContractApplicationDO contract = contractApplicationMapper.selectById(contractApplicationId);
        if (contract == null
                || !FinanceContractApprovalStatusEnum.APPROVED.getStatus().equals(contract.getApprovalStatus())
                || Boolean.TRUE.equals(contract.getVoided())
                || !Objects.equals(contract.getApplicantUserId(), userId)) {
            throw exception(BUSINESS_ORDER_CONTRACT_INVALID);
        }
        return contract.getId();
    }

    private Long resolveContractByApplicationNo(String applicationNo, Long userId) {
        if (StrUtil.isBlank(applicationNo)) {
            throw exception(BUSINESS_ORDER_CONTRACT_REQUIRED);
        }
        FinanceContractApplicationDO contract =
                contractApplicationMapper.selectByApplicationNo(applicationNo.trim());
        if (contract == null
                || !FinanceContractApprovalStatusEnum.APPROVED.getStatus().equals(contract.getApprovalStatus())
                || Boolean.TRUE.equals(contract.getVoided())
                || !Objects.equals(contract.getApplicantUserId(), userId)) {
            throw exception(BUSINESS_ORDER_CONTRACT_INVALID);
        }
        return contract.getId();
    }

    /**
     * 更新：历史空可保持空；写入则校验；禁止清空已映射；有开票占用禁止换合同。
     */
    private Long resolveContractOnUpdate(FinanceBusinessOrderDO current,
                                         FinanceBusinessOrderSaveReqVO reqVO,
                                         Long userId) {
        Long currentId = current.getContractApplicationId();
        Long requestedId = reqVO.getContractApplicationId();
        if (currentId == null) {
            // 历史空：未传合同可保存其它字段；传入则校验并写入
            if (requestedId == null) {
                return null;
            }
            return requireSelectableContract(requestedId, userId);
        }
        // 已映射：禁止清空
        if (requestedId == null) {
            throw exception(BUSINESS_ORDER_CONTRACT_CLEAR_FORBIDDEN);
        }
        if (Objects.equals(currentId, requestedId)) {
            return currentId;
        }
        // 换合同：开票占用 > 0 禁止
        BigDecimal occupied = current.getInvoicedOccupiedAmount() == null
                ? ZERO : current.getInvoicedOccupiedAmount();
        if (occupied.compareTo(ZERO) > 0) {
            throw exception(BUSINESS_ORDER_CONTRACT_CHANGE_FORBIDDEN);
        }
        return requireSelectableContract(requestedId, userId);
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
        if (reqVO.getEntityCompanyDeptId() == null) {
            throw new IllegalArgumentException("主体公司不能为空");
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
                                                               FinanceBusinessOrderImportSupport.NormalizedAmounts amounts,
                                                               FinanceEntityCompanyResolver.ResolvedCompany company) {
        return FinanceBusinessOrderDO.builder()
                .entityCompanyDeptId(company.deptId())
                .entityCompanyName(company.name())
                .contractProcessId(trimToNull(reqVO.getContractProcessId()))
                .contractApplicationId(reqVO.getContractApplicationId())
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
