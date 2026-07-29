package cn.iocoder.yudao.module.finance.service.claim;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.finance.controller.admin.claim.vo.FinanceReceiptClaimPageReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.claim.vo.FinanceReceiptClaimSaveReqVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.business.FinanceBusinessOrderDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.claim.FinanceReceiptClaimDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.claim.FinanceReceiptClaimItemDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.claim.FinanceReceiptClaimRevokeAuditDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.invoice.FinanceInvoiceApplicationDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.invoice.FinanceInvoiceApplicationLineDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.receipt.FinanceReceiptDO;
import cn.iocoder.yudao.module.finance.dal.mysql.business.FinanceBusinessOrderMapper;
import cn.iocoder.yudao.module.finance.dal.mysql.claim.FinanceReceiptClaimItemMapper;
import cn.iocoder.yudao.module.finance.dal.mysql.claim.FinanceReceiptClaimMapper;
import cn.iocoder.yudao.module.finance.dal.mysql.claim.FinanceReceiptClaimRevokeAuditMapper;
import cn.iocoder.yudao.module.finance.dal.mysql.invoice.FinanceInvoiceApplicationLineMapper;
import cn.iocoder.yudao.module.finance.dal.mysql.invoice.FinanceInvoiceApplicationMapper;
import cn.iocoder.yudao.module.finance.dal.mysql.receipt.FinanceBankReceiptMapper;
import cn.iocoder.yudao.module.finance.enums.FinanceInvoiceApprovalStatusEnum;
import cn.iocoder.yudao.module.finance.enums.FinanceReceiptClaimReviewStatusEnum;
import cn.iocoder.yudao.module.finance.enums.FinanceReceiptClaimSourceEnum;
import cn.iocoder.yudao.module.finance.enums.FinanceReceiptClaimStatusEnum;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.*;

/**
 * 到款认领：新链路挂开票申请 + 到款/开票双边 pending CAS；LEGACY_BO 全拒写。
 */
@Service
@Validated
public class FinanceReceiptClaimServiceImpl implements FinanceReceiptClaimService {

    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private final FinanceReceiptClaimMapper claimMapper;
    private final FinanceReceiptClaimItemMapper itemMapper;
    private final FinanceBankReceiptMapper receiptMapper;
    private final FinanceBusinessOrderMapper businessOrderMapper;
    private final FinanceInvoiceApplicationMapper invoiceApplicationMapper;
    private final FinanceInvoiceApplicationLineMapper invoiceLineMapper;
    private final FinanceReceiptClaimRevokeAuditMapper revokeAuditMapper;

    public FinanceReceiptClaimServiceImpl(FinanceReceiptClaimMapper claimMapper,
                                          FinanceReceiptClaimItemMapper itemMapper,
                                          FinanceBankReceiptMapper receiptMapper,
                                          FinanceBusinessOrderMapper businessOrderMapper,
                                          FinanceInvoiceApplicationMapper invoiceApplicationMapper,
                                          FinanceInvoiceApplicationLineMapper invoiceLineMapper,
                                          FinanceReceiptClaimRevokeAuditMapper revokeAuditMapper) {
        this.claimMapper = claimMapper;
        this.itemMapper = itemMapper;
        this.receiptMapper = receiptMapper;
        this.businessOrderMapper = businessOrderMapper;
        this.invoiceApplicationMapper = invoiceApplicationMapper;
        this.invoiceLineMapper = invoiceLineMapper;
        this.revokeAuditMapper = revokeAuditMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createClaim(FinanceReceiptClaimSaveReqVO createReqVO, Long claimantId) {
        ValidatedAllocations allocations = validateInvoiceAllocations(createReqVO.getItems(), claimantId);
        FinanceReceiptClaimDO claim = FinanceReceiptClaimDO.builder()
                .claimantId(claimantId)
                .status(FinanceReceiptClaimReviewStatusEnum.PENDING.getStatus())
                .totalClaimAmount(allocations.totalAmount())
                .remark(createReqVO.getRemark())
                .build();
        claimMapper.insert(claim);
        insertInvoiceItems(claim.getId(), createReqVO.getItems());
        applyPending(allocations.receiptAmounts(), allocations.invoiceAmounts());
        return claim.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateClaim(FinanceReceiptClaimSaveReqVO updateReqVO, Long claimantId) {
        FinanceReceiptClaimDO claim = validateEditableClaim(updateReqVO.getId(), claimantId);
        assertWritableSource(itemMapper.selectListByClaimId(claim.getId()));
        // 待确认态：先释放旧 pending；已驳回态 pending 已在 reject 释放，无需再释
        List<FinanceReceiptClaimItemDO> oldItems = itemMapper.selectListByClaimId(claim.getId());
        if (FinanceReceiptClaimReviewStatusEnum.PENDING.getStatus().equals(claim.getStatus())) {
            releasePendingFromItems(oldItems);
        }

        ValidatedAllocations allocations = validateInvoiceAllocations(updateReqVO.getItems(), claimantId);
        FinanceReceiptClaimDO update = FinanceReceiptClaimDO.builder()
                .id(claim.getId())
                .status(claim.getStatus())
                .totalClaimAmount(allocations.totalAmount())
                .remark(updateReqVO.getRemark())
                .build();
        if (claimMapper.updateEditableClaim(update, claimantId, claim.getStatus()) != 1) {
            throw exception(RECEIPT_CLAIM_CONCURRENT_MODIFICATION);
        }
        itemMapper.deleteByClaimId(claim.getId());
        insertInvoiceItems(claim.getId(), updateReqVO.getItems());
        applyPending(allocations.receiptAmounts(), allocations.invoiceAmounts());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmClaim(Long id, Long reviewerId) {
        FinanceReceiptClaimDO claim = claimMapper.selectById(id);
        if (claim == null) {
            throw exception(RECEIPT_CLAIM_NOT_EXISTS);
        }
        List<FinanceReceiptClaimItemDO> items = itemMapper.selectListByClaimId(id);
        if (CollUtil.isEmpty(items)) {
            throw exception(RECEIPT_CLAIM_ITEMS_EMPTY);
        }
        assertWritableSource(items);

        FinanceReceiptClaimDO confirmed = FinanceReceiptClaimDO.builder().id(id)
                .status(FinanceReceiptClaimReviewStatusEnum.CONFIRMED.getStatus())
                .reviewerId(reviewerId).reviewTime(LocalDateTime.now()).build();
        if (claimMapper.updateStatusIfMatch(confirmed,
                FinanceReceiptClaimReviewStatusEnum.PENDING.getStatus()) != 1) {
            throw exception(RECEIPT_CLAIM_CONCURRENT_MODIFICATION);
        }

        Map<Long, BigDecimal> receiptAmounts = aggregate(items, FinanceReceiptClaimItemDO::getReceiptId);
        Map<Long, BigDecimal> invoiceAmounts = aggregate(items, FinanceReceiptClaimItemDO::getInvoiceApplicationId);

        // 锁序：receipt 升序 → invoice 升序
        for (Long receiptId : sortedKeys(receiptAmounts)) {
            BigDecimal amount = receiptAmounts.get(receiptId);
            if (receiptMapper.decreasePendingClaimedAmount(receiptId, amount) != 1
                    || receiptMapper.increaseClaimedAmount(receiptId, amount) != 1) {
                throw exception(RECEIPT_CLAIM_CONCURRENT_MODIFICATION);
            }
        }
        for (Long invoiceId : sortedKeys(invoiceAmounts)) {
            BigDecimal amount = invoiceAmounts.get(invoiceId);
            if (invoiceApplicationMapper.confirmPendingToClaimed(invoiceId, amount) != 1) {
                throw exception(RECEIPT_CLAIM_CONCURRENT_MODIFICATION);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejectClaim(Long id, Long reviewerId, String reason) {
        if (StrUtil.isBlank(reason)) {
            throw exception(RECEIPT_CLAIM_REJECT_REASON_REQUIRED);
        }
        FinanceReceiptClaimDO claim = claimMapper.selectById(id);
        if (claim == null) {
            throw exception(RECEIPT_CLAIM_NOT_EXISTS);
        }
        List<FinanceReceiptClaimItemDO> items = itemMapper.selectListByClaimId(id);
        assertWritableSource(items);

        FinanceReceiptClaimDO rejected = FinanceReceiptClaimDO.builder().id(id)
                .status(FinanceReceiptClaimReviewStatusEnum.REJECTED.getStatus())
                .reviewerId(reviewerId).reviewTime(LocalDateTime.now()).rejectReason(reason.trim()).build();
        if (claimMapper.updateStatusIfMatch(rejected,
                FinanceReceiptClaimReviewStatusEnum.PENDING.getStatus()) != 1) {
            throw exception(RECEIPT_CLAIM_STATUS_INVALID);
        }
        releasePendingFromItems(items);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revokeClaim(Long id, Long reviewerId, String reviewerName, String reason) {
        if (StrUtil.isBlank(reason)) {
            throw exception(RECEIPT_CLAIM_REVOKE_REASON_REQUIRED);
        }
        FinanceReceiptClaimDO claim = claimMapper.selectById(id);
        if (claim == null) {
            throw exception(RECEIPT_CLAIM_NOT_EXISTS);
        }
        if (!FinanceReceiptClaimReviewStatusEnum.CONFIRMED.getStatus().equals(claim.getStatus())) {
            throw exception(RECEIPT_CLAIM_STATUS_INVALID);
        }
        List<FinanceReceiptClaimItemDO> items = itemMapper.selectListByClaimId(id);
        assertWritableSource(items); // LEGACY 禁止 revoke

        FinanceReceiptClaimDO revoked = FinanceReceiptClaimDO.builder().id(id)
                .status(FinanceReceiptClaimReviewStatusEnum.REVOKED.getStatus())
                .reviewerId(reviewerId).reviewTime(LocalDateTime.now())
                .revokeReason(reason.trim()).build();
        if (claimMapper.updateStatusIfMatch(revoked,
                FinanceReceiptClaimReviewStatusEnum.CONFIRMED.getStatus()) != 1) {
            throw exception(RECEIPT_CLAIM_CONCURRENT_MODIFICATION);
        }

        Map<Long, BigDecimal> receiptAmounts = aggregate(items, FinanceReceiptClaimItemDO::getReceiptId);
        Map<Long, BigDecimal> invoiceAmounts = aggregate(items, FinanceReceiptClaimItemDO::getInvoiceApplicationId);
        for (Long receiptId : sortedKeys(receiptAmounts)) {
            if (receiptMapper.decreaseClaimedAmount(receiptId, receiptAmounts.get(receiptId)) != 1) {
                throw exception(RECEIPT_CLAIM_CONCURRENT_MODIFICATION);
            }
        }
        for (Long invoiceId : sortedKeys(invoiceAmounts)) {
            if (invoiceApplicationMapper.decreaseConfirmedClaimedAmount(invoiceId, invoiceAmounts.get(invoiceId)) != 1) {
                throw exception(RECEIPT_CLAIM_CONCURRENT_MODIFICATION);
            }
        }
        if (revokeAuditMapper.insert(FinanceReceiptClaimRevokeAuditDO.builder()
                .claimId(id).reviewerId(reviewerId)
                .reviewerName(StrUtil.blankToDefault(StrUtil.trim(reviewerName), null))
                .revokeTime(LocalDateTime.now()).revokeReason(reason.trim())
                .build()) != 1) {
            throw exception(RECEIPT_CLAIM_CONCURRENT_MODIFICATION);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resubmitClaim(Long id, Long claimantId) {
        FinanceReceiptClaimDO claim = claimMapper.selectById(id);
        if (claim == null) {
            throw exception(RECEIPT_CLAIM_NOT_EXISTS);
        }
        if (!Objects.equals(claim.getClaimantId(), claimantId)) {
            throw exception(RECEIPT_CLAIM_NOT_OWNER);
        }
        List<FinanceReceiptClaimItemDO> items = itemMapper.selectListByClaimId(id);
        assertWritableSource(items);

        if (claimMapper.updateRejectedToPending(id, claimantId) != 1) {
            throw exception(RECEIPT_CLAIM_STATUS_INVALID);
        }
        // 驳回已释 pending；重提再占
        Map<Long, BigDecimal> receiptAmounts = aggregate(items, FinanceReceiptClaimItemDO::getReceiptId);
        Map<Long, BigDecimal> invoiceAmounts = aggregate(items, FinanceReceiptClaimItemDO::getInvoiceApplicationId);
        // 重提时再校验权限与 claimAllowed
        validateReceiptsPending(receiptAmounts);
        validateInvoiceApps(invoiceAmounts, claimantId);
        applyPending(receiptAmounts, invoiceAmounts);
    }

    @Override
    public PageResult<FinanceReceiptClaimDO> getMyClaimPage(FinanceReceiptClaimPageReqVO pageReqVO,
                                                            Long claimantId) {
        return claimMapper.selectClaimPage(pageReqVO, claimantId);
    }

    @Override
    public PageResult<FinanceReceiptClaimDO> getReviewClaimPage(FinanceReceiptClaimPageReqVO pageReqVO) {
        return claimMapper.selectClaimPage(pageReqVO, null);
    }

    @Override
    public FinanceReceiptClaimDetail getClaimDetail(Long id, Long viewerId, boolean reviewer) {
        FinanceReceiptClaimDO claim = claimMapper.selectById(id);
        if (claim == null) {
            throw exception(RECEIPT_CLAIM_NOT_EXISTS);
        }
        if (!reviewer && !Objects.equals(claim.getClaimantId(), viewerId)) {
            throw exception(RECEIPT_CLAIM_NOT_OWNER);
        }
        List<FinanceReceiptClaimItemDO> items = itemMapper.selectListByClaimId(id);
        Set<Long> receiptIds = new HashSet<>();
        Set<Long> businessOrderIds = new HashSet<>();
        Set<Long> invoiceIds = new HashSet<>();
        items.forEach(item -> {
            receiptIds.add(item.getReceiptId());
            if (item.getBusinessOrderId() != null) {
                businessOrderIds.add(item.getBusinessOrderId());
            }
            if (item.getInvoiceApplicationId() != null) {
                invoiceIds.add(item.getInvoiceApplicationId());
            }
        });
        Map<Long, FinanceReceiptDO> receipts = new HashMap<>();
        Map<Long, FinanceBusinessOrderDO> businessOrders = new HashMap<>();
        Map<Long, FinanceInvoiceApplicationDO> invoices = new HashMap<>();
        if (!receiptIds.isEmpty()) {
            receiptMapper.selectListByIds(receiptIds).forEach(receipt -> receipts.put(receipt.getId(), receipt));
        }
        if (!businessOrderIds.isEmpty()) {
            businessOrderMapper.selectListByIds(businessOrderIds)
                    .forEach(order -> businessOrders.put(order.getId(), order));
        }
        if (!invoiceIds.isEmpty()) {
            invoiceApplicationMapper.selectListByIds(invoiceIds)
                    .forEach(app -> invoices.put(app.getId(), app));
        }
        return new FinanceReceiptClaimDetail(claim, items, receipts, businessOrders, invoices);
    }

    @Override
    public List<FinanceReceiptClaimRevokeAuditDO> getRevokeAuditList(Long claimId) {
        return revokeAuditMapper.selectListByClaimId(claimId);
    }

    // -------------------------------------------------------------------------
    // validation + pending
    // -------------------------------------------------------------------------

    private FinanceReceiptClaimDO validateEditableClaim(Long id, Long claimantId) {
        FinanceReceiptClaimDO claim = claimMapper.selectById(id);
        if (claim == null) {
            throw exception(RECEIPT_CLAIM_NOT_EXISTS);
        }
        if (!Objects.equals(claim.getClaimantId(), claimantId)) {
            throw exception(RECEIPT_CLAIM_NOT_OWNER);
        }
        if (!FinanceReceiptClaimReviewStatusEnum.PENDING.getStatus().equals(claim.getStatus())
                && !FinanceReceiptClaimReviewStatusEnum.REJECTED.getStatus().equals(claim.getStatus())) {
            throw exception(RECEIPT_CLAIM_STATUS_INVALID);
        }
        return claim;
    }

    private void assertWritableSource(List<FinanceReceiptClaimItemDO> items) {
        if (CollUtil.isEmpty(items)) {
            return;
        }
        for (FinanceReceiptClaimItemDO item : items) {
            if (FinanceReceiptClaimSourceEnum.LEGACY_BO.getSource().equals(item.getClaimSource())
                    || (item.getClaimSource() == null && item.getBusinessOrderId() != null
                    && item.getInvoiceApplicationId() == null)) {
                throw exception(RECEIPT_CLAIM_LEGACY_WRITE_FORBIDDEN);
            }
        }
    }

    private ValidatedAllocations validateInvoiceAllocations(List<FinanceReceiptClaimSaveReqVO.Item> items,
                                                            Long claimantId) {
        if (CollUtil.isEmpty(items)) {
            throw exception(RECEIPT_CLAIM_ITEMS_EMPTY);
        }
        Set<String> pairs = new HashSet<>();
        Map<Long, BigDecimal> receiptAmounts = new HashMap<>();
        Map<Long, BigDecimal> invoiceAmounts = new HashMap<>();
        BigDecimal totalAmount = ZERO;
        for (FinanceReceiptClaimSaveReqVO.Item item : items) {
            if (item.getClaimAmount() == null || item.getClaimAmount().compareTo(ZERO) <= 0) {
                throw exception(RECEIPT_CLAIM_AMOUNT_INVALID);
            }
            if (item.getInvoiceApplicationId() == null) {
                throw exception(RECEIPT_CLAIM_SOURCE_INVALID);
            }
            if (!pairs.add(item.getReceiptId() + ":" + item.getInvoiceApplicationId())) {
                throw exception(RECEIPT_CLAIM_ITEM_DUPLICATE_INVOICE);
            }
            receiptAmounts.merge(item.getReceiptId(), item.getClaimAmount(), BigDecimal::add);
            invoiceAmounts.merge(item.getInvoiceApplicationId(), item.getClaimAmount(), BigDecimal::add);
            totalAmount = totalAmount.add(item.getClaimAmount());
        }
        validateReceiptsPending(receiptAmounts);
        validateInvoiceApps(invoiceAmounts, claimantId);
        return new ValidatedAllocations(totalAmount, receiptAmounts, invoiceAmounts);
    }

    private void validateReceiptsPending(Map<Long, BigDecimal> amounts) {
        Map<Long, FinanceReceiptDO> receipts = new HashMap<>();
        receiptMapper.selectListByIds(amounts.keySet()).forEach(r -> receipts.put(r.getId(), r));
        for (Map.Entry<Long, BigDecimal> entry : amounts.entrySet()) {
            FinanceReceiptDO receipt = receipts.get(entry.getKey());
            if (receipt != null && FinanceReceiptClaimStatusEnum.CLOSED.getStatus()
                    .equals(receipt.getClaimStatus())) {
                throw exception(RECEIPT_CLAIM_RECEIPT_CLOSED);
            }
            BigDecimal pending = defaultZero(receipt == null ? null : receipt.getPendingClaimedAmount());
            BigDecimal unclaimed = defaultZero(receipt == null ? null : receipt.getUnclaimedAmount());
            // 可认 = unclaimed - pending
            if (receipt == null || unclaimed.subtract(pending).compareTo(entry.getValue()) < 0) {
                throw exception(RECEIPT_CLAIM_RECEIPT_INVALID);
            }
        }
    }

    private void validateInvoiceApps(Map<Long, BigDecimal> amounts, Long claimantId) {
        Map<Long, FinanceInvoiceApplicationDO> apps = new HashMap<>();
        invoiceApplicationMapper.selectListByIds(amounts.keySet()).forEach(a -> apps.put(a.getId(), a));
        for (Map.Entry<Long, BigDecimal> entry : amounts.entrySet()) {
            FinanceInvoiceApplicationDO app = apps.get(entry.getKey());
            if (app == null || !isClaimAllowed(app) || !canAccessInvoiceApp(app, claimantId)) {
                throw exception(RECEIPT_CLAIM_INVOICE_INVALID);
            }
            BigDecimal pending = defaultZero(app.getPendingClaimedAmount());
            BigDecimal confirmed = defaultZero(app.getConfirmedClaimedAmount());
            BigDecimal total = defaultZero(app.getTotalAmount());
            if (total.subtract(confirmed).subtract(pending).compareTo(entry.getValue()) < 0) {
                throw exception(RECEIPT_CLAIM_INVOICE_INVALID);
            }
        }
    }

    /**
     * claimAllowed：APPROVED && !voided && 可认领额 &gt; 0（不读 issue_status）
     */
    static boolean isClaimAllowed(FinanceInvoiceApplicationDO app) {
        if (app == null) {
            return false;
        }
        if (!FinanceInvoiceApprovalStatusEnum.APPROVED.getStatus().equals(app.getApprovalStatus())) {
            return false;
        }
        if (Boolean.TRUE.equals(app.getVoided())) {
            return false;
        }
        BigDecimal remaining = defaultZero(app.getTotalAmount())
                .subtract(defaultZero(app.getConfirmedClaimedAmount()))
                .subtract(defaultZero(app.getPendingClaimedAmount()));
        return remaining.compareTo(ZERO) > 0;
    }

    /**
     * D-T7：申请人本人 或 明细任一商务单导入人；FA 走 review 权限入口，create 侧按 BS 谓词。
     */
    private boolean canAccessInvoiceApp(FinanceInvoiceApplicationDO app, Long userId) {
        if (Objects.equals(app.getApplicantUserId(), userId)) {
            return true;
        }
        List<FinanceInvoiceApplicationLineDO> lines =
                invoiceLineMapper.selectListByApplicationId(app.getId());
        if (CollUtil.isEmpty(lines)) {
            return false;
        }
        Set<Long> boIds = lines.stream().map(FinanceInvoiceApplicationLineDO::getBusinessOrderId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (boIds.isEmpty()) {
            return false;
        }
        List<FinanceBusinessOrderDO> orders = businessOrderMapper.selectListByIds(boIds);
        return orders.stream().anyMatch(o -> Objects.equals(o.getImporterId(), userId));
    }

    private void insertInvoiceItems(Long claimId, List<FinanceReceiptClaimSaveReqVO.Item> items) {
        for (FinanceReceiptClaimSaveReqVO.Item item : items) {
            itemMapper.insert(FinanceReceiptClaimItemDO.builder()
                    .claimId(claimId)
                    .receiptId(item.getReceiptId())
                    .invoiceApplicationId(item.getInvoiceApplicationId())
                    .businessOrderId(null)
                    .claimSource(FinanceReceiptClaimSourceEnum.INVOICE.getSource())
                    .claimAmount(item.getClaimAmount())
                    .build());
        }
    }

    private void applyPending(Map<Long, BigDecimal> receiptAmounts, Map<Long, BigDecimal> invoiceAmounts) {
        for (Long receiptId : sortedKeys(receiptAmounts)) {
            if (receiptMapper.increasePendingClaimedAmount(receiptId, receiptAmounts.get(receiptId)) != 1) {
                throw exception(RECEIPT_CLAIM_CONCURRENT_MODIFICATION);
            }
        }
        for (Long invoiceId : sortedKeys(invoiceAmounts)) {
            if (invoiceApplicationMapper.increasePendingClaimedAmount(invoiceId, invoiceAmounts.get(invoiceId)) != 1) {
                throw exception(RECEIPT_CLAIM_CONCURRENT_MODIFICATION);
            }
        }
    }

    private void releasePendingFromItems(List<FinanceReceiptClaimItemDO> items) {
        if (CollUtil.isEmpty(items)) {
            return;
        }
        Map<Long, BigDecimal> receiptAmounts = aggregate(items, FinanceReceiptClaimItemDO::getReceiptId);
        Map<Long, BigDecimal> invoiceAmounts = aggregate(items, FinanceReceiptClaimItemDO::getInvoiceApplicationId);
        for (Long receiptId : sortedKeys(receiptAmounts)) {
            if (receiptMapper.decreasePendingClaimedAmount(receiptId, receiptAmounts.get(receiptId)) != 1) {
                throw exception(RECEIPT_CLAIM_CONCURRENT_MODIFICATION);
            }
        }
        for (Long invoiceId : sortedKeys(invoiceAmounts)) {
            if (invoiceId == null) {
                continue;
            }
            if (invoiceApplicationMapper.decreasePendingClaimedAmount(invoiceId, invoiceAmounts.get(invoiceId)) != 1) {
                throw exception(RECEIPT_CLAIM_CONCURRENT_MODIFICATION);
            }
        }
    }

    private static List<Long> sortedKeys(Map<Long, BigDecimal> map) {
        return map.keySet().stream().filter(Objects::nonNull).sorted().toList();
    }

    private static Map<Long, BigDecimal> aggregate(List<FinanceReceiptClaimItemDO> items,
                                                   java.util.function.Function<FinanceReceiptClaimItemDO, Long> idGetter) {
        Map<Long, BigDecimal> amounts = new LinkedHashMap<>();
        items.forEach(item -> {
            Long key = idGetter.apply(item);
            if (key != null) {
                amounts.merge(key, item.getClaimAmount(), BigDecimal::add);
            }
        });
        return amounts;
    }

    private static BigDecimal defaultZero(BigDecimal value) {
        return value == null ? ZERO : value;
    }

    private record ValidatedAllocations(BigDecimal totalAmount,
                                        Map<Long, BigDecimal> receiptAmounts,
                                        Map<Long, BigDecimal> invoiceAmounts) {
    }

}
