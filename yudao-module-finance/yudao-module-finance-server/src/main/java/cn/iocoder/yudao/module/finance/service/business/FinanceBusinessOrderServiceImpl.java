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
import cn.iocoder.yudao.module.finance.service.common.FinanceCurrencySupport;
import cn.iocoder.yudao.module.finance.service.common.FinanceEntityCompanyResolver;
import cn.iocoder.yudao.module.finance.service.common.FinanceRelatedProcessAccess;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.BUSINESS_ORDER_ACTIVE_INVOICE_BLOCKS_CONTRACT_CHANGE;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.BUSINESS_ORDER_CONTRACT_CHANGE_FORBIDDEN;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.BUSINESS_ORDER_CONTRACT_CLEAR_FORBIDDEN;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.BUSINESS_ORDER_CONTRACT_INVALID;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.BUSINESS_ORDER_CONTRACT_PRODUCT_MISSING;
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
    private final FinanceRelatedProcessAccess relatedProcessAccess;

    public FinanceBusinessOrderServiceImpl(FinanceBusinessOrderMapper businessOrderMapper,
                                           FinanceBusinessOrderNoRedisDAO businessOrderNoRedisDAO,
                                           FinanceContractApplicationMapper contractApplicationMapper,
                                           FinanceEntityCompanyResolver entityCompanyResolver,
                                           FinanceRelatedProcessAccess relatedProcessAccess) {
        this.businessOrderMapper = businessOrderMapper;
        this.businessOrderNoRedisDAO = businessOrderNoRedisDAO;
        this.contractApplicationMapper = contractApplicationMapper;
        this.entityCompanyResolver = entityCompanyResolver;
        this.relatedProcessAccess = relatedProcessAccess;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createBusinessOrder(FinanceBusinessOrderSaveReqVO createReqVO, Long importerId) {
        FinanceBusinessOrderImportSupport.NormalizedAmounts amounts =
                FinanceBusinessOrderImportSupport.normalizeAmounts(
                        createReqVO.getSignedExecutionAmount(), createReqVO.getDiscountRate());
        validateBusinessOrderSave(createReqVO, amounts);
        FinanceEntityCompanyResolver.ResolvedCompany company =
                entityCompanyResolver.requireByDeptId(createReqVO.getEntityCompanyDeptId());
        // 新数据硬强制：必须关联已通过且本人申请的合同（C4/C18）
        FinanceContractApplicationDO contract =
                requireSelectableContract(createReqVO.getContractApplicationId(), importerId);
        String productType = requireContractProductType(contract);
        // EXP-73：交易币种 + 与合同同币种约束
        String currency = FinanceCurrencySupport.requireSupported(createReqVO.getCurrency());
        FinanceCurrencySupport.assertSameIfBothPresent(contract.getCurrency(), currency);
        LocalDate importDate = LocalDate.now();
        // 客户端 productName 不可信：服务端仅从合同派生
        FinanceBusinessOrderDO businessOrder = buildBusinessOrderWithProduct(
                createReqVO, amounts, company, productType, currency);
        businessOrder.setContractApplicationId(contract.getId());
        businessOrder.setOrderNo(businessOrderNoRedisDAO.generate(importDate));
        businessOrder.setImportDate(importDate);
        businessOrder.setImporterId(importerId);
        businessOrder.setConfirmedClaimedAmount(ZERO);
        businessOrderMapper.insert(businessOrder);
        return businessOrder.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
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

        Long currentContractId = currentOrder.getContractApplicationId();
        ContractResolution resolution = resolveContractOnUpdate(currentOrder, updateReqVO,
                currentOrder.getImporterId());
        // EXP-73：交易币种 + 与合同同币种约束
        String currency = FinanceCurrencySupport.requireSupported(updateReqVO.getCurrency());
        if (resolution.contractId() != null) {
            FinanceContractApplicationDO contract =
                    contractApplicationMapper.selectById(resolution.contractId());
            if (contract != null) {
                FinanceCurrencySupport.assertSameIfBothPresent(contract.getCurrency(), currency);
            }
        }

        // EXP-70 #1/#2：合同+产品快照仅 CAS 写入；普通 updateById 永不写权威产品字段
        if (currentContractId != null && resolution.contractId() != null
                && !Objects.equals(currentContractId, resolution.contractId())) {
            // 换合同：CAS 前校验生效开票（同事务）
            assertNoActiveInvoiceBlockingChange(currentOrder.getId());
            int changed = businessOrderMapper.casChangeContractApplicationId(
                    currentOrder.getId(), currentContractId, resolution.contractId(),
                    resolution.casProductType());
            if (changed == 0) {
                throw exception(BUSINESS_ORDER_CONTRACT_CHANGE_FORBIDDEN);
            }
        } else if (currentContractId == null && resolution.contractId() != null) {
            // P2 #9：首次绑定同样阻断活动开票
            assertNoActiveInvoiceBlockingChange(currentOrder.getId());
            int changed = businessOrderMapper.casSetContractApplicationIdWhenEmpty(
                    currentOrder.getId(), resolution.contractId(), resolution.casProductType());
            if (changed == 0) {
                throw exception(BUSINESS_ORDER_CONTRACT_CHANGE_FORBIDDEN);
            }
        } else if (resolution.fillEmptySnapshot()) {
            // #6 受控补齐：同合同且快照仍空时一次性补齐，不覆盖已有快照
            int filled = businessOrderMapper.casFillProductSnapshotWhenEmpty(
                    currentOrder.getId(), resolution.contractId(), resolution.casProductType());
            if (filled == 0 && StrUtil.isBlank(currentOrder.getProductTypeSnapshot())
                    && StrUtil.isBlank(currentOrder.getProductName())) {
                throw exception(BUSINESS_ORDER_CONTRACT_PRODUCT_MISSING);
            }
        }

        // 非权威字段更新：禁止 productName / productTypeSnapshot / contractApplicationId
        FinanceBusinessOrderDO updateObj = buildBusinessOrderNonAuthoritative(
                updateReqVO, amounts, company, currency);
        updateObj.setId(currentOrder.getId());
        updateObj.setOrderNo(currentOrder.getOrderNo());
        updateObj.setImportDate(currentOrder.getImportDate());
        updateObj.setImporterId(currentOrder.getImporterId());
        updateObj.setConfirmedClaimedAmount(confirmedClaimedAmount);
        updateObj.setSourceRowHash(currentOrder.getSourceRowHash());
        updateObj.setContractProcessId(currentOrder.getContractProcessId());
        updateObj.setContractApplicationId(null);
        updateObj.setProductName(null);
        updateObj.setProductTypeSnapshot(null);
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
    @Transactional(rollbackFor = Exception.class)
    public FinanceBusinessOrderImportRespVO importBusinessOrderList(List<FinanceBusinessOrderImportExcelVO> importRows,
                                                                    Long importerId) {
        if (CollUtil.isEmpty(importRows)) {
            throw new IllegalArgumentException("导入商务签单数据不能为空");
        }
        FinanceBusinessOrderImportRespVO response = FinanceBusinessOrderImportRespVO.builder()
                .orderNos(new ArrayList<>()).failureRows(new LinkedHashMap<>()).skippedRows(new ArrayList<>()).build();
        Set<String> sourceRowHashes = new HashSet<>();
        var enabledCompanies = entityCompanyResolver.loadEnabledCompanies();
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
                    row.getEntityCompanyName(), companyOut, enabledCompanies);
            if (companyError != null) {
                response.getFailureRows().put(rowNumber, companyError);
                continue;
            }
            FinanceEntityCompanyResolver.ResolvedCompany company = companyOut[0];
            FinanceContractApplicationDO contract;
            try {
                contract = resolveContractByApplicationNo(row.getContractApplicationNo(), importerId);
            } catch (Exception ex) {
                response.getFailureRows().put(rowNumber, "合同业务单号无效：未找到已通过且本人申请的合同");
                continue;
            }
            String productType;
            try {
                productType = requireContractProductType(contract);
            } catch (Exception ex) {
                response.getFailureRows().put(rowNumber, "合同产品类型为空，无法导入");
                continue;
            }
            if (StrUtil.isNotBlank(row.getProductName())
                    && !productType.equals(row.getProductName().trim())) {
                response.getFailureRows().put(rowNumber,
                        "产品列与合同产品类型不一致（合同=" + productType + "，表格=" + row.getProductName().trim() + "）");
                continue;
            }
            // #10：幂等键使用合同产品，不再依赖客户端产品列
            String sourceRowHash = FinanceBusinessOrderImportSupport.calculateSourceRowHash(
                    row, amounts, company.deptId(), productType);
            if (!sourceRowHashes.add(sourceRowHash)
                    || businessOrderMapper.selectBySourceRowHash(sourceRowHash) != null) {
                response.getSkippedRows().add(rowNumber);
                continue;
            }
            String orderNo = businessOrderNoRedisDAO.generate(LocalDate.now());
            businessOrderMapper.insert(FinanceBusinessOrderImportSupport.buildOrder(row, importerId,
                    company.deptId(), company.name(), amounts, sourceRowHash, orderNo,
                    contract.getId(), productType));
            response.getOrderNos().add(orderNo);
        }
        return response;
    }

    /**
     * 新建/导入：合同必须 APPROVED 且申请人=当前用户。
     */
    private FinanceContractApplicationDO requireSelectableContract(Long contractApplicationId, Long userId) {
        if (contractApplicationId == null) {
            throw exception(BUSINESS_ORDER_CONTRACT_REQUIRED);
        }
        FinanceContractApplicationDO contract = contractApplicationMapper.selectById(contractApplicationId);
        if (contract == null
                || !FinanceContractApprovalStatusEnum.APPROVED.getStatus().equals(contract.getApprovalStatus())
                || Boolean.TRUE.equals(contract.getVoided())
                || !relatedProcessAccess.canAccessContract(userId, contract)) {
            throw exception(BUSINESS_ORDER_CONTRACT_INVALID);
        }
        return contract;
    }

    private FinanceContractApplicationDO resolveContractByApplicationNo(String applicationNo, Long userId) {
        if (StrUtil.isBlank(applicationNo)) {
            throw exception(BUSINESS_ORDER_CONTRACT_REQUIRED);
        }
        FinanceContractApplicationDO contract =
                contractApplicationMapper.selectByApplicationNo(applicationNo.trim());
        if (contract == null
                || !FinanceContractApprovalStatusEnum.APPROVED.getStatus().equals(contract.getApprovalStatus())
                || Boolean.TRUE.equals(contract.getVoided())
                || !relatedProcessAccess.canAccessContract(userId, contract)) {
            throw exception(BUSINESS_ORDER_CONTRACT_INVALID);
        }
        return contract;
    }

    private static String requireContractProductType(FinanceContractApplicationDO contract) {
        if (contract == null || StrUtil.isBlank(contract.getProductType())) {
            throw exception(BUSINESS_ORDER_CONTRACT_PRODUCT_MISSING);
        }
        return contract.getProductType().trim();
    }

    private void assertNoActiveInvoiceBlockingChange(Long businessOrderId) {
        Long activeLines = businessOrderMapper.countActiveInvoiceLinesByBusinessOrderId(businessOrderId);
        if (activeLines != null && activeLines > 0) {
            throw exception(BUSINESS_ORDER_ACTIVE_INVOICE_BLOCKS_CONTRACT_CHANGE);
        }
    }

    /**
     * 更新：历史空可保持空；写入则校验；禁止清空已映射；有开票占用或生效开票禁止换合同。
     * <p>EXP-70 #3：永不读取请求 productName；#6：同合同保留已有快照。
     */
    private ContractResolution resolveContractOnUpdate(FinanceBusinessOrderDO current,
                                                       FinanceBusinessOrderSaveReqVO reqVO,
                                                       Long userId) {
        Long currentId = current.getContractApplicationId();
        Long requestedId = reqVO.getContractApplicationId();
        if (currentId == null) {
            if (requestedId == null) {
                // #3：仅保留库内 legacy，绝不读请求 productName；双空则业务错误码（P2 #6）
                String legacy = firstNonBlank(current.getProductTypeSnapshot(), current.getProductName());
                if (StrUtil.isBlank(legacy)) {
                    throw exception(BUSINESS_ORDER_CONTRACT_PRODUCT_MISSING);
                }
                return ContractResolution.keepLegacyNoContract();
            }
            FinanceContractApplicationDO contract = requireSelectableContract(requestedId, userId);
            return ContractResolution.firstMap(contract.getId(), requireContractProductType(contract));
        }
        if (requestedId == null) {
            throw exception(BUSINESS_ORDER_CONTRACT_CLEAR_FORBIDDEN);
        }
        if (Objects.equals(currentId, requestedId)) {
            // #6：保留已有快照；仅当快照与 name 皆空时受控补齐
            // 复审 #6：补齐路径复用完整 requireSelectableContract（APPROVED/未作废/主体一致）
            if (StrUtil.isNotBlank(current.getProductTypeSnapshot())
                    || StrUtil.isNotBlank(current.getProductName())) {
                return ContractResolution.sameContractKeepSnapshot(currentId);
            }
            FinanceContractApplicationDO contract = requireSelectableContract(currentId, userId);
            return ContractResolution.sameContractFillEmpty(currentId, requireContractProductType(contract));
        }
        BigDecimal occupied = current.getInvoicedOccupiedAmount() == null
                ? ZERO : current.getInvoicedOccupiedAmount();
        if (occupied.compareTo(ZERO) > 0) {
            throw exception(BUSINESS_ORDER_CONTRACT_CHANGE_FORBIDDEN);
        }
        FinanceContractApplicationDO contract = requireSelectableContract(requestedId, userId);
        return ContractResolution.changeContract(contract.getId(), requireContractProductType(contract));
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
        if (reqVO.getOrderDate() == null || StrUtil.isBlank(reqVO.getContactPerson())
                || reqVO.getExecutionStartDate() == null
                || reqVO.getExecutionEndDate() == null) {
            throw new IllegalArgumentException("下单日期、对接人和执行日期不能为空");
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

    /** 新建：带权威产品（合同派生）+ 交易币种 */
    private static FinanceBusinessOrderDO buildBusinessOrderWithProduct(
            FinanceBusinessOrderSaveReqVO reqVO,
            FinanceBusinessOrderImportSupport.NormalizedAmounts amounts,
            FinanceEntityCompanyResolver.ResolvedCompany company,
            String productTypeFromContract,
            String currency) {
        String product = productTypeFromContract.trim();
        return FinanceBusinessOrderDO.builder()
                .entityCompanyDeptId(company.deptId())
                .entityCompanyName(company.name())
                .contractProcessId(trimToNull(reqVO.getContractProcessId()))
                .orderDate(reqVO.getOrderDate())
                .productName(product)
                .productTypeSnapshot(product)
                .contactPerson(reqVO.getContactPerson().trim())
                .executionStartDate(reqVO.getExecutionStartDate())
                .executionEndDate(reqVO.getExecutionEndDate())
                .payerName(trimToNull(reqVO.getPayerName()))
                .signedExecutionAmount(amounts.signedExecutionAmount())
                .discountRate(amounts.discountRate())
                .settlementAmount(amounts.settlementAmount())
                .currency(currency)
                .remark(trimToNull(reqVO.getRemark()))
                .build();
    }

    /** 更新：非权威字段；产品/合同列保持 null 以跳过 updateById */
    private static FinanceBusinessOrderDO buildBusinessOrderNonAuthoritative(
            FinanceBusinessOrderSaveReqVO reqVO,
            FinanceBusinessOrderImportSupport.NormalizedAmounts amounts,
            FinanceEntityCompanyResolver.ResolvedCompany company,
            String currency) {
        return FinanceBusinessOrderDO.builder()
                .entityCompanyDeptId(company.deptId())
                .entityCompanyName(company.name())
                .orderDate(reqVO.getOrderDate())
                .contactPerson(reqVO.getContactPerson().trim())
                .executionStartDate(reqVO.getExecutionStartDate())
                .executionEndDate(reqVO.getExecutionEndDate())
                .payerName(trimToNull(reqVO.getPayerName()))
                .signedExecutionAmount(amounts.signedExecutionAmount())
                .discountRate(amounts.discountRate())
                .settlementAmount(amounts.settlementAmount())
                .currency(currency)
                .remark(trimToNull(reqVO.getRemark()))
                .build();
    }

    private static String trimToNull(String value) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        return value.trim();
    }

    private static String firstNonBlank(String first, String second) {
        if (StrUtil.isNotBlank(first)) {
            return first.trim();
        }
        if (StrUtil.isNotBlank(second)) {
            return second.trim();
        }
        return null;
    }

    /**
     * @param casProductType CAS 写入的产品；null 表示本路径不经 CAS 写产品
     * @param fillEmptySnapshot 同合同空快照受控补齐
     */
    private record ContractResolution(Long contractId, String casProductType, boolean fillEmptySnapshot) {
        static ContractResolution keepLegacyNoContract() {
            return new ContractResolution(null, null, false);
        }

        static ContractResolution firstMap(Long contractId, String productType) {
            return new ContractResolution(contractId, productType, false);
        }

        static ContractResolution changeContract(Long contractId, String productType) {
            return new ContractResolution(contractId, productType, false);
        }

        static ContractResolution sameContractKeepSnapshot(Long contractId) {
            return new ContractResolution(contractId, null, false);
        }

        static ContractResolution sameContractFillEmpty(Long contractId, String productType) {
            return new ContractResolution(contractId, productType, true);
        }
    }

}
