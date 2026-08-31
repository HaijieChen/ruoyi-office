package cn.iocoder.yudao.module.finance.service.invoice;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.finance.framework.rpc.FinanceBpmProcessInstanceApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.yudao.module.finance.controller.admin.invoice.vo.FinanceInvoiceApplicationCompleteIssueReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.invoice.vo.FinanceInvoiceApplicationCreateAndStartReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.invoice.vo.FinanceInvoiceApplicationPageReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.invoice.vo.FinanceInvoiceApplicationResubmitReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.invoice.vo.FinanceInvoiceApplicationUpdateIssueProgressReqVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.business.FinanceBusinessOrderDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.contract.FinanceContractApplicationDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.customer.FinanceCustomerCompanyDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.invoice.FinanceInvoiceApplicationDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.invoice.FinanceInvoiceApplicationFileDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.invoice.FinanceInvoiceApplicationLineDO;
import cn.iocoder.yudao.module.finance.dal.mysql.business.FinanceBusinessOrderMapper;
import cn.iocoder.yudao.module.finance.dal.mysql.contract.FinanceContractApplicationMapper;
import cn.iocoder.yudao.module.finance.dal.mysql.invoice.FinanceInvoiceApplicationFileMapper;
import cn.iocoder.yudao.module.finance.dal.mysql.invoice.FinanceInvoiceApplicationLineMapper;
import cn.iocoder.yudao.module.finance.dal.mysql.invoice.FinanceInvoiceApplicationMapper;
import cn.iocoder.yudao.module.finance.dal.redis.no.FinanceInvoiceApplicationNoRedisDAO;
import cn.iocoder.yudao.module.finance.enums.FinanceInvoiceApprovalStatusEnum;
import cn.iocoder.yudao.module.finance.enums.FinanceInvoiceIssueStatusEnum;
import cn.iocoder.yudao.module.finance.service.common.FinanceBusinessStaffSupport;
import cn.iocoder.yudao.module.finance.service.common.FinanceCurrencySupport;
import cn.iocoder.yudao.module.finance.service.common.FinanceEntityCompanyResolver;
import cn.iocoder.yudao.module.finance.service.customer.FinanceCustomerCompanyService;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.*;

/**
 * 开票申请 Service 实现。
 * <p>占用写路径：createAndStart / resubmit 增占；onApprovalOutcome(REJECTED|CANCELLED) / resubmit 释占。
 * <p>issue 写路径：主路径 {@link #completeIssue}；兼容 {@link #updateIssueProgress}。
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
    /** 明细选商务单：品牌商务 / 游戏联运 / 渠道产品推广 / 硬件业务 */
    public static final Set<String> BUSINESS_ORDER_PRODUCT_TYPES = Set.of(
            "ppsw", "yxly", "qdcp", "yjcp");
    private static final String SALES_FILE_TYPE = "销售合同";
    private static final String DICT_PRODUCT_TYPE = "finance_product_type";
    private static final Set<String> ALLOWED_INVOICE_TYPES = Set.of("专票", "普票");

    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private final FinanceInvoiceApplicationMapper applicationMapper;
    private final FinanceInvoiceApplicationLineMapper lineMapper;
    private final FinanceInvoiceApplicationFileMapper fileMapper;
    private final FinanceBusinessOrderMapper businessOrderMapper;
    private final FinanceInvoiceApplicationNoRedisDAO applicationNoRedisDAO;
    private final BpmProcessInstanceApi processInstanceApi;
    private final FinanceCustomerCompanyService customerCompanyService;
    private final FinanceEntityCompanyResolver entityCompanyResolver;
    private final DictDataApi dictDataApi;
    @Resource
    private FinanceBusinessStaffSupport businessStaffSupport;
    @Resource
    private FinanceContractApplicationMapper contractApplicationMapper;

    public FinanceInvoiceApplicationServiceImpl(FinanceInvoiceApplicationMapper applicationMapper,
                                                FinanceInvoiceApplicationLineMapper lineMapper,
                                                FinanceInvoiceApplicationFileMapper fileMapper,
                                                FinanceBusinessOrderMapper businessOrderMapper,
                                                FinanceInvoiceApplicationNoRedisDAO applicationNoRedisDAO,
                                                // EXP-87 F4：必须注入带 identity interceptor 的 Finance 专用 BPM 客户端
                                                FinanceBpmProcessInstanceApi processInstanceApi,
                                                FinanceCustomerCompanyService customerCompanyService,
                                                FinanceEntityCompanyResolver entityCompanyResolver,
                                                DictDataApi dictDataApi) {
        this.applicationMapper = applicationMapper;
        this.lineMapper = lineMapper;
        this.fileMapper = fileMapper;
        this.businessOrderMapper = businessOrderMapper;
        this.applicationNoRedisDAO = applicationNoRedisDAO;
        this.processInstanceApi = processInstanceApi;
        this.customerCompanyService = customerCompanyService;
        this.entityCompanyResolver = entityCompanyResolver;
        this.dictDataApi = dictDataApi;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createAndStart(FinanceInvoiceApplicationCreateAndStartReqVO reqVO, Long applicantUserId) {
        List<FinanceInvoiceApplicationCreateAndStartReqVO.Line> lines = reqVO.getLines();
        if (CollUtil.isEmpty(lines)) {
            throw exception(INVOICE_APPLICATION_LINES_EMPTY);
        }
        String productType = requireProductType(reqVO.getTaxContent());
        requireInvoiceType(reqVO.getInvoiceType());
        boolean brandCommerce = usesBusinessOrder(productType);

        Map<Long, BigDecimal> occupyByBo = new LinkedHashMap<>();
        BigDecimal totalAmount = ZERO;
        for (FinanceInvoiceApplicationCreateAndStartReqVO.Line line : lines) {
            if (line.getAmount() == null || line.getAmount().compareTo(ZERO) <= 0) {
                throw exception(INVOICE_APPLICATION_AMOUNT_INVALID);
            }
            totalAmount = totalAmount.add(line.getAmount());
            if (brandCommerce) {
                if (line.getBusinessOrderId() == null) {
                    throw exception(INVOICE_APPLICATION_BUSINESS_ORDER_NOT_EXISTS);
                }
                occupyByBo.merge(line.getBusinessOrderId(), line.getAmount(), BigDecimal::add);
            } else if (line.getSourceContractApplicationId() == null) {
                throw exception(INVOICE_APPLICATION_CONTRACT_REQUIRED);
            }
        }

        String currency = FinanceCurrencySupport.requireSupported(reqVO.getCurrency());
        Map<Long, FinanceBusinessOrderDO> orderMap = Map.of();
        if (brandCommerce) {
            orderMap = loadBusinessOrders(occupyByBo.keySet());
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
                FinanceCurrencySupport.assertSameIfBothPresent(order.getCurrency(), currency);
                String boProduct = resolveBusinessOrderProductType(order);
                if (!productType.equals(boProduct)) {
                    throw exception(INVOICE_APPLICATION_PRODUCT_MIXED);
                }
            }
        } else {
            requireSalesContracts(lines, productType, null);
        }
        String commonProductType = productType;

        // 1b. 客户公司：服务端权威快照
        BuyerSnapshot buyerSnapshot = resolveBuyerSnapshot(reqVO.getCustomerCompanyId());
        // 1c. 开票公司：名称快照仅服务端
        FinanceEntityCompanyResolver.ResolvedCompany invoiceCo =
                entityCompanyResolver.requireByDeptId(reqVO.getInvoiceCompanyDeptId());

        // 2. 写主表
        String applicationNo = applicationNoRedisDAO.generate(LocalDate.now());
        FinanceInvoiceApplicationDO application = FinanceInvoiceApplicationDO.builder()
                .applicationNo(applicationNo)
                .approvalStatus(FinanceInvoiceApprovalStatusEnum.PENDING.getStatus())
                .issueStatus(FinanceInvoiceIssueStatusEnum.NONE.getStatus())
                .totalAmount(totalAmount)
                .currency(currency)
                .confirmedClaimedAmount(ZERO)
                .pendingClaimedAmount(ZERO)
                .applicantUserId(applicantUserId)
                .businessStaffUserId(businessStaffSupport.resolve(applicantUserId, reqVO.getBusinessStaffUserId()))
                .expectedInvoiceDate(reqVO.getExpectedInvoiceDate())
                .invoiceCompany(invoiceCo.name())
                .invoiceCompanyDeptId(invoiceCo.deptId())
                .invoiceType(reqVO.getInvoiceType())
                .buyerName(buyerSnapshot.buyerName())
                .buyerTaxNo(buyerSnapshot.buyerTaxNo())
                .buyerAddressPhone(buyerSnapshot.buyerAddressPhone())
                .buyerBankAccount(buyerSnapshot.buyerBankAccount())
                .customerCompanyId(buyerSnapshot.customerCompanyId())
                .specialInvoiceRequirement(reqVO.getSpecialInvoiceRequirement())
                .taxContent(commonProductType)
                .taxRate(reqVO.getTaxRate())
                .amountExcludingTax(reqVO.getAmountExcludingTax())
                .taxAmount(reqVO.getTaxAmount())
                .evidenceFileUrl(reqVO.getEvidenceFileUrl())
                .remark(reqVO.getRemark())
                .voided(Boolean.FALSE)
                .build();
        applicationMapper.insert(application);

        int sort = 0;
        for (FinanceInvoiceApplicationCreateAndStartReqVO.Line line : lines) {
            int lineSort = line.getSort() != null ? line.getSort() : sort;
            Long sourceContractId;
            Long businessOrderId = null;
            if (brandCommerce) {
                FinanceBusinessOrderDO order = orderMap.get(line.getBusinessOrderId());
                businessOrderId = line.getBusinessOrderId();
                sourceContractId = order.getContractApplicationId();
            } else {
                sourceContractId = line.getSourceContractApplicationId();
            }
            FinanceInvoiceApplicationLineDO lineDO = FinanceInvoiceApplicationLineDO.builder()
                    .applicationId(application.getId())
                    .businessOrderId(businessOrderId)
                    .sourceContractApplicationId(sourceContractId)
                    .productTypeSnapshot(commonProductType)
                    .amount(line.getAmount())
                    .invoiceCompany(invoiceCo.name())
                    .invoiceType(line.getInvoiceType() != null ? line.getInvoiceType() : reqVO.getInvoiceType())
                    .billingPeriod(line.getBillingPeriod())
                    .remark(line.getRemark())
                    .issueStatus(FinanceInvoiceIssueStatusEnum.NONE.getStatus())
                    .sort(lineSort)
                    .build();
            lineMapper.insert(lineDO);
            sort++;
        }

        if (brandCommerce) {
            increaseOccupy(occupyByBo, orderMap);
        }

        // 5. 启动 BPM；失败则整单回滚（含占用）
        Map<String, Object> variables = buildProcessVariables(application);
        if (reqVO.getStartCompanyDeptId() != null) {
            variables.put("startCompanyDeptId", reqVO.getStartCompanyDeptId());
        }
        if (reqVO.getStartDeptId() != null) {
            variables.put("startDeptId", reqVO.getStartDeptId());
        }
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
        String productType = requireProductType(reqVO.getTaxContent());
        requireInvoiceType(reqVO.getInvoiceType());
        boolean brandCommerce = usesBusinessOrder(productType);

        Map<Long, BigDecimal> newOccupyByBo = new LinkedHashMap<>();
        BigDecimal totalAmount = ZERO;
        for (FinanceInvoiceApplicationResubmitReqVO.Line line : lines) {
            if (line.getAmount() == null || line.getAmount().compareTo(ZERO) <= 0) {
                throw exception(INVOICE_APPLICATION_AMOUNT_INVALID);
            }
            totalAmount = totalAmount.add(line.getAmount());
            if (brandCommerce) {
                if (line.getBusinessOrderId() == null) {
                    throw exception(INVOICE_APPLICATION_BUSINESS_ORDER_NOT_EXISTS);
                }
                newOccupyByBo.merge(line.getBusinessOrderId(), line.getAmount(), BigDecimal::add);
            } else if (line.getSourceContractApplicationId() == null) {
                throw exception(INVOICE_APPLICATION_CONTRACT_REQUIRED);
            }
        }

        // 1. 释放当前明细占用（驳回时 onApprovalOutcome 已释占；此处再调用对 0 占用幂等安全：按当前 lines 汇总 CAS）
        // 驳回后占用应为 0；为防漏回调，按现有 lines 再尝试释占（若已 0 则 CAS 失败需区分）
        releaseAllOccupyForApplicationIfAny(appId);

        String currency = FinanceCurrencySupport.requireSupported(reqVO.getCurrency());
        Map<Long, FinanceBusinessOrderDO> orderMap = Map.of();
        if (brandCommerce) {
            orderMap = loadBusinessOrders(newOccupyByBo.keySet());
            for (Map.Entry<Long, BigDecimal> entry : newOccupyByBo.entrySet()) {
                FinanceBusinessOrderDO order = orderMap.get(entry.getKey());
                if (order == null) {
                    throw exception(INVOICE_APPLICATION_BUSINESS_ORDER_NOT_EXISTS);
                }
                order = businessOrderMapper.selectById(entry.getKey());
                if (order == null) {
                    throw exception(INVOICE_APPLICATION_BUSINESS_ORDER_NOT_EXISTS);
                }
                orderMap.put(entry.getKey(), order);
                BigDecimal occupied = defaultZero(order.getInvoicedOccupiedAmount());
                BigDecimal settlement = defaultZero(order.getSettlementAmount());
                if (settlement.subtract(occupied).compareTo(entry.getValue()) < 0) {
                    throw exception(INVOICE_APPLICATION_OCCUPY_EXCEED);
                }
                FinanceCurrencySupport.assertSameIfBothPresent(order.getCurrency(), currency);
                if (!productType.equals(resolveBusinessOrderProductType(order))) {
                    throw exception(INVOICE_APPLICATION_PRODUCT_MIXED);
                }
            }
        } else {
            requireSalesContracts(lines, productType, appId);
        }
        String commonProductType = productType;

        FinanceEntityCompanyResolver.ResolvedCompany invoiceCo =
                entityCompanyResolver.requireByDeptId(reqVO.getInvoiceCompanyDeptId());

        // 3. 替换明细
        lineMapper.deleteByApplicationId(appId);
        int sort = 0;
        for (FinanceInvoiceApplicationResubmitReqVO.Line line : lines) {
            int lineSort = line.getSort() != null ? line.getSort() : sort;
            Long sourceContractId;
            Long businessOrderId = null;
            if (brandCommerce) {
                FinanceBusinessOrderDO order = orderMap.get(line.getBusinessOrderId());
                businessOrderId = line.getBusinessOrderId();
                sourceContractId = order.getContractApplicationId();
            } else {
                sourceContractId = line.getSourceContractApplicationId();
            }
            FinanceInvoiceApplicationLineDO lineDO = FinanceInvoiceApplicationLineDO.builder()
                    .applicationId(appId)
                    .businessOrderId(businessOrderId)
                    .sourceContractApplicationId(sourceContractId)
                    .productTypeSnapshot(commonProductType)
                    .amount(line.getAmount())
                    .invoiceCompany(invoiceCo.name())
                    .invoiceType(line.getInvoiceType() != null ? line.getInvoiceType() : reqVO.getInvoiceType())
                    .billingPeriod(line.getBillingPeriod())
                    .remark(line.getRemark())
                    .issueStatus(FinanceInvoiceIssueStatusEnum.NONE.getStatus())
                    .sort(lineSort)
                    .build();
            lineMapper.insert(lineDO);
            sort++;
        }

        // 4. 更新表头快照 + 回到 PENDING（购方以当前启用档案为准；可空列显式 set）
        BuyerSnapshot buyerSnapshot = resolveBuyerSnapshot(reqVO.getCustomerCompanyId());
        applicationMapper.update(null, new UpdateWrapper<FinanceInvoiceApplicationDO>()
                .eq("id", appId)
                .set("approval_status", FinanceInvoiceApprovalStatusEnum.PENDING.getStatus())
                .set("issue_status", FinanceInvoiceIssueStatusEnum.NONE.getStatus())
                .set("total_amount", totalAmount)
                .set("currency", currency)
                .set("expected_invoice_date", reqVO.getExpectedInvoiceDate())
                .set("invoice_company", invoiceCo.name())
                .set("invoice_company_dept_id", invoiceCo.deptId())
                .set("invoice_type", reqVO.getInvoiceType())
                .set("buyer_name", buyerSnapshot.buyerName())
                .set("buyer_tax_no", buyerSnapshot.buyerTaxNo())
                .set("buyer_address_phone", buyerSnapshot.buyerAddressPhone())
                .set("buyer_bank_account", buyerSnapshot.buyerBankAccount())
                .set("customer_company_id", buyerSnapshot.customerCompanyId())
                .set("special_invoice_requirement", reqVO.getSpecialInvoiceRequirement())
                .set("tax_content", commonProductType)
                .set("tax_rate", reqVO.getTaxRate())
                .set("amount_excluding_tax", reqVO.getAmountExcludingTax())
                .set("tax_amount", reqVO.getTaxAmount())
                .set("evidence_file_url", reqVO.getEvidenceFileUrl())
                .set("remark", reqVO.getRemark())
                .set("voided", Boolean.FALSE));

        if (brandCommerce) {
            increaseOccupy(newOccupyByBo, orderMap);
        }

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
    public void completeIssue(FinanceInvoiceApplicationCompleteIssueReqVO reqVO) {
        FinanceInvoiceApplicationDO application = getApplication(reqVO.getApplicationId());
        if (!FinanceInvoiceApprovalStatusEnum.APPROVED.getStatus().equals(application.getApprovalStatus())
                || Boolean.TRUE.equals(application.getVoided())) {
            throw exception(INVOICE_APPLICATION_ISSUE_NOT_ALLOWED);
        }
        List<FinanceInvoiceApplicationCompleteIssueReqVO.FileItem> files = reqVO.getFiles();
        if (CollUtil.isEmpty(files)) {
            throw exception(INVOICE_APPLICATION_COMPLETE_ISSUE_FILES_EMPTY);
        }
        BigDecimal batchAmount = ZERO;
        for (FinanceInvoiceApplicationCompleteIssueReqVO.FileItem file : files) {
            if (file == null || StrUtil.isBlank(file.getUrl())) {
                throw exception(INVOICE_APPLICATION_COMPLETE_ISSUE_FILES_EMPTY);
            }
            if (file.getAmount() == null || file.getAmount().compareTo(ZERO) <= 0) {
                throw exception(INVOICE_APPLICATION_ISSUE_AMOUNT_REQUIRED);
            }
            batchAmount = batchAmount.add(file.getAmount());
        }

        List<FinanceInvoiceApplicationFileDO> existing =
                fileMapper.selectListByApplicationId(reqVO.getApplicationId());
        BigDecimal issuedAmount = ZERO;
        int sort = 0;
        boolean anyAmount = false;
        if (CollUtil.isNotEmpty(existing)) {
            for (FinanceInvoiceApplicationFileDO row : existing) {
                if (row.getAmount() != null) {
                    anyAmount = true;
                }
                issuedAmount = issuedAmount.add(defaultZero(row.getAmount()));
                if (row.getSort() != null && row.getSort() >= sort) {
                    sort = row.getSort() + 1;
                }
            }
        }
        BigDecimal applyAmount = defaultZero(application.getTotalAmount());
        if (!anyAmount && FinanceInvoiceIssueStatusEnum.FULL.getStatus().equals(application.getIssueStatus())) {
            issuedAmount = applyAmount;
        }
        BigDecimal totalIssued = issuedAmount.add(batchAmount);
        if (totalIssued.compareTo(applyAmount) > 0) {
            throw exception(INVOICE_APPLICATION_ISSUE_AMOUNT_EXCEED);
        }

        for (FinanceInvoiceApplicationCompleteIssueReqVO.FileItem file : files) {
            FinanceInvoiceApplicationFileDO row = FinanceInvoiceApplicationFileDO.builder()
                    .applicationId(reqVO.getApplicationId())
                    .fileUrl(file.getUrl().trim())
                    .fileName(StrUtil.blankToDefault(file.getName(), null))
                    .amount(file.getAmount())
                    .invoiceNo(StrUtil.blankToDefault(file.getInvoiceNo(), null))
                    .invoiceDate(file.getInvoiceDate())
                    .sort(sort++)
                    .build();
            fileMapper.insert(row);
        }

        int issueStatus = totalIssued.compareTo(applyAmount) == 0
                ? FinanceInvoiceIssueStatusEnum.FULL.getStatus()
                : FinanceInvoiceIssueStatusEnum.PARTIAL.getStatus();
        FinanceInvoiceApplicationDO appUpdate = new FinanceInvoiceApplicationDO();
        appUpdate.setId(reqVO.getApplicationId());
        appUpdate.setIssueStatus(issueStatus);
        applicationMapper.updateById(appUpdate);
    }

    @Override
    @Deprecated
    public void updateIssueProgress(FinanceInvoiceApplicationUpdateIssueProgressReqVO reqVO) {
        // I2：废止一行一票写路径，避免绕过 complete-issue 产生 PARTIAL
        throw exception(INVOICE_APPLICATION_USE_COMPLETE_ISSUE);
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
    public List<FinanceInvoiceApplicationFileDO> getApplicationFiles(Long applicationId) {
        return fileMapper.selectListByApplicationId(applicationId);
    }

    @Override
    public PageResult<FinanceInvoiceApplicationDO> getApplicationPage(FinanceInvoiceApplicationPageReqVO pageReqVO) {
        return applicationMapper.selectPage(pageReqVO);
    }

    @Override
    public List<FinanceInvoiceApplicationDO> listSelectableForRedFlush() {
        return applicationMapper.selectSelectableForRedFlush().stream()
                .filter(FinanceInvoiceRedflushEligibility::isSelectablePredecessor)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void releaseOccupyForRedFlush(Long predecessorApplicationId) {
        FinanceInvoiceApplicationDO predecessor = getApplication(predecessorApplicationId);
        if (Boolean.TRUE.equals(predecessor.getRedFlushed())) {
            return;
        }
        releaseAllOccupyForApplication(predecessorApplicationId);
        applicationMapper.markRedFlushed(predecessorApplicationId);
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

    private BuyerSnapshot resolveBuyerSnapshot(Long customerCompanyId) {
        FinanceCustomerCompanyDO company = customerCompanyService.getEnabledCustomerCompany(customerCompanyId);
        return new BuyerSnapshot(
                company.getId(),
                company.getName(),
                company.getTaxNo(),
                FinanceCustomerCompanyService.composeBuyerAddressPhone(company),
                FinanceCustomerCompanyService.composeBuyerBankAccount(company));
    }

    private record BuyerSnapshot(Long customerCompanyId, String buyerName, String buyerTaxNo,
                                 String buyerAddressPhone, String buyerBankAccount) {
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

    /**
     * CAS 占用：金额可开 + 合同/产品与读快照时一致。换合同后期望不匹配则整单失败回滚。
     */
    private void increaseOccupy(Map<Long, BigDecimal> occupyByBo,
                                Map<Long, FinanceBusinessOrderDO> orderMap) {
        for (Map.Entry<Long, BigDecimal> entry : occupyByBo.entrySet()) {
            FinanceBusinessOrderDO order = orderMap.get(entry.getKey());
            if (order == null) {
                throw exception(INVOICE_APPLICATION_BUSINESS_ORDER_NOT_EXISTS);
            }
            String expectedProduct = resolveBusinessOrderProductType(order);
            int updated = businessOrderMapper.increaseInvoicedOccupiedAmount(
                    entry.getKey(),
                    entry.getValue(),
                    order.getContractApplicationId(),
                    expectedProduct);
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
        if (application.getInvoiceCompanyDeptId() != null) {
            variables.put("companyId", application.getInvoiceCompanyDeptId());
        }
        if (StrUtil.isNotBlank(application.getInvoiceCompany())) {
            variables.put("companyName", application.getInvoiceCompany());
        }
        if (StrUtil.isNotBlank(application.getCurrency())) {
            variables.put("currency", application.getCurrency());
        }
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

    /**
     * 新开票写入权威产品：必须合法合同 + 非空 product_type_snapshot。
     * <p>P1 #3：不得回退 product_name（legacy 自由文本不可升级为新财务事实）。
     */
    private static String resolveBusinessOrderProductType(FinanceBusinessOrderDO order) {
        if (order == null) {
            throw exception(INVOICE_APPLICATION_BUSINESS_ORDER_NOT_EXISTS);
        }
        if (order.getContractApplicationId() == null) {
            throw exception(INVOICE_APPLICATION_BUSINESS_ORDER_PRODUCT_MISSING);
        }
        if (StrUtil.isBlank(order.getProductTypeSnapshot())) {
            throw exception(INVOICE_APPLICATION_BUSINESS_ORDER_PRODUCT_MISSING);
        }
        return order.getProductTypeSnapshot().trim();
    }

    private static BigDecimal defaultZero(BigDecimal value) {
        return value == null ? ZERO : value;
    }

    static boolean usesBusinessOrder(String productType) {
        return BUSINESS_ORDER_PRODUCT_TYPES.contains(productType);
    }

    private String requireProductType(String productType) {
        if (StrUtil.isBlank(productType)) {
            throw exception(INVOICE_APPLICATION_PRODUCT_REQUIRED);
        }
        String trimmed = productType.trim();
        try {
            dictDataApi.validateDictDataList(DICT_PRODUCT_TYPE, List.of(trimmed)).checkError();
        } catch (Exception ex) {
            throw exception(INVOICE_APPLICATION_PRODUCT_TYPE_INVALID);
        }
        return trimmed;
    }

    private static void requireInvoiceType(String invoiceType) {
        if (StrUtil.isBlank(invoiceType) || !ALLOWED_INVOICE_TYPES.contains(invoiceType.trim())) {
            throw exception(INVOICE_APPLICATION_INVOICE_TYPE_INVALID);
        }
    }

    private void requireSalesContracts(List<? extends FinanceInvoiceApplicationCreateAndStartReqVO.Line> lines,
                                       String productType, Long excludeApplicationId) {
        List<Long> ids = new ArrayList<>();
        Map<Long, BigDecimal> occupyByContract = new LinkedHashMap<>();
        for (FinanceInvoiceApplicationCreateAndStartReqVO.Line line : lines) {
            if (line.getSourceContractApplicationId() != null) {
                ids.add(line.getSourceContractApplicationId());
                occupyByContract.merge(line.getSourceContractApplicationId(), line.getAmount(), BigDecimal::add);
            }
        }
        if (ids.isEmpty()) {
            throw exception(INVOICE_APPLICATION_CONTRACT_REQUIRED);
        }
        Map<Long, FinanceContractApplicationDO> byId = contractApplicationMapper.selectBatchIds(ids).stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(FinanceContractApplicationDO::getId, Function.identity(), (a, b) -> a));
        for (FinanceInvoiceApplicationCreateAndStartReqVO.Line line : lines) {
            FinanceContractApplicationDO contract = byId.get(line.getSourceContractApplicationId());
            if (contract == null
                    || Boolean.TRUE.equals(contract.getVoided())
                    || !"APPROVED".equals(contract.getApprovalStatus())
                    || !SALES_FILE_TYPE.equals(contract.getFileType())
                    || !productType.equals(StrUtil.trim(contract.getProductType()))) {
                throw exception(INVOICE_APPLICATION_CONTRACT_INVALID);
            }
        }
        for (Map.Entry<Long, BigDecimal> entry : occupyByContract.entrySet()) {
            FinanceContractApplicationDO contract = byId.get(entry.getKey());
            if (Boolean.TRUE.equals(contract.getAmountNa()) || contract.getContractAmount() == null
                    || contract.getContractAmount().compareTo(ZERO) <= 0) {
                continue;
            }
            BigDecimal occupied = occupiedInvoiceAmount(entry.getKey(), excludeApplicationId);
            if (contract.getContractAmount().subtract(occupied).compareTo(entry.getValue()) < 0) {
                throw exception(INVOICE_APPLICATION_CONTRACT_OCCUPY_EXCEED);
            }
        }
    }

    @Override
    public BigDecimal occupiedInvoiceAmount(Long contractId, Long excludeApplicationId) {
        List<FinanceInvoiceApplicationLineDO> lines = lineMapper.selectListBySourceContractApplicationId(contractId);
        if (CollUtil.isEmpty(lines)) {
            return ZERO;
        }
        Set<Long> appIds = lines.stream()
                .map(FinanceInvoiceApplicationLineDO::getApplicationId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (excludeApplicationId != null) {
            appIds.remove(excludeApplicationId);
        }
        if (appIds.isEmpty()) {
            return ZERO;
        }
        Map<Long, FinanceInvoiceApplicationDO> apps = applicationMapper.selectListByIds(appIds).stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(FinanceInvoiceApplicationDO::getId, Function.identity(), (a, b) -> a));
        BigDecimal occupied = ZERO;
        for (FinanceInvoiceApplicationLineDO line : lines) {
            if (excludeApplicationId != null && excludeApplicationId.equals(line.getApplicationId())) {
                continue;
            }
            FinanceInvoiceApplicationDO app = apps.get(line.getApplicationId());
            if (app == null || Boolean.TRUE.equals(app.getVoided())) {
                continue;
            }
            String status = app.getApprovalStatus();
            if (!FinanceInvoiceApprovalStatusEnum.PENDING.getStatus().equals(status)
                    && !FinanceInvoiceApprovalStatusEnum.APPROVED.getStatus().equals(status)) {
                continue;
            }
            occupied = occupied.add(defaultZero(line.getAmount()));
        }
        return occupied;
    }

}
