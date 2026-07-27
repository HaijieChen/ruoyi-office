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
import cn.iocoder.yudao.module.finance.dal.dataobject.receipt.FinanceReceiptDO;
import cn.iocoder.yudao.module.finance.dal.mysql.business.FinanceBusinessOrderMapper;
import cn.iocoder.yudao.module.finance.dal.mysql.claim.FinanceReceiptClaimItemMapper;
import cn.iocoder.yudao.module.finance.dal.mysql.claim.FinanceReceiptClaimMapper;
import cn.iocoder.yudao.module.finance.dal.mysql.claim.FinanceReceiptClaimRevokeAuditMapper;
import cn.iocoder.yudao.module.finance.dal.mysql.receipt.FinanceBankReceiptMapper;
import cn.iocoder.yudao.module.finance.enums.FinanceReceiptClaimReviewStatusEnum;
import cn.iocoder.yudao.module.finance.enums.FinanceReceiptClaimStatusEnum;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.*;

@Service
@Validated
public class FinanceReceiptClaimServiceImpl implements FinanceReceiptClaimService {

    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private final FinanceReceiptClaimMapper claimMapper;
    private final FinanceReceiptClaimItemMapper itemMapper;
    private final FinanceBankReceiptMapper receiptMapper;
    private final FinanceBusinessOrderMapper businessOrderMapper;
    private final FinanceReceiptClaimRevokeAuditMapper revokeAuditMapper;

    public FinanceReceiptClaimServiceImpl(FinanceReceiptClaimMapper claimMapper,
                                          FinanceReceiptClaimItemMapper itemMapper,
                                          FinanceBankReceiptMapper receiptMapper,
                                          FinanceBusinessOrderMapper businessOrderMapper,
                                          FinanceReceiptClaimRevokeAuditMapper revokeAuditMapper) {
        this.claimMapper = claimMapper;
        this.itemMapper = itemMapper;
        this.receiptMapper = receiptMapper;
        this.businessOrderMapper = businessOrderMapper;
        this.revokeAuditMapper = revokeAuditMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createClaim(FinanceReceiptClaimSaveReqVO createReqVO, Long claimantId) {
        ValidatedAllocations allocations = validateAllocations(createReqVO.getItems(), claimantId);
        FinanceReceiptClaimDO claim = FinanceReceiptClaimDO.builder()
                .claimantId(claimantId)
                .status(FinanceReceiptClaimReviewStatusEnum.PENDING.getStatus())
                .totalClaimAmount(allocations.totalAmount())
                .remark(createReqVO.getRemark())
                .build();
        claimMapper.insert(claim);
        insertItems(claim.getId(), createReqVO.getItems());
        return claim.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateClaim(FinanceReceiptClaimSaveReqVO updateReqVO, Long claimantId) {
        FinanceReceiptClaimDO claim = validateEditableClaim(updateReqVO.getId(), claimantId);
        ValidatedAllocations allocations = validateAllocations(updateReqVO.getItems(), claimantId);
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
        insertItems(claim.getId(), updateReqVO.getItems());
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
        FinanceReceiptClaimDO confirmed = FinanceReceiptClaimDO.builder().id(id)
                .status(FinanceReceiptClaimReviewStatusEnum.CONFIRMED.getStatus())
                .reviewerId(reviewerId).reviewTime(LocalDateTime.now()).build();
        if (claimMapper.updateStatusIfMatch(confirmed,
                FinanceReceiptClaimReviewStatusEnum.PENDING.getStatus()) != 1) {
            throw exception(RECEIPT_CLAIM_CONCURRENT_MODIFICATION);
        }
        Map<Long, BigDecimal> receiptAmounts = aggregate(items, FinanceReceiptClaimItemDO::getReceiptId);
        Map<Long, BigDecimal> orderAmounts = aggregate(items, FinanceReceiptClaimItemDO::getBusinessOrderId);
        receiptAmounts.forEach((receiptId, amount) -> {
            if (receiptMapper.increaseClaimedAmount(receiptId, amount) != 1) {
                throw exception(RECEIPT_CLAIM_CONCURRENT_MODIFICATION);
            }
        });
        orderAmounts.forEach((orderId, amount) -> {
            if (businessOrderMapper.increaseConfirmedClaimedAmount(orderId, claim.getClaimantId(), amount) != 1) {
                throw exception(RECEIPT_CLAIM_CONCURRENT_MODIFICATION);
            }
        });
    }

    @Override
    public void rejectClaim(Long id, Long reviewerId, String reason) {
        if (StrUtil.isBlank(reason)) {
            throw exception(RECEIPT_CLAIM_REJECT_REASON_REQUIRED);
        }
        FinanceReceiptClaimDO rejected = FinanceReceiptClaimDO.builder().id(id)
                .status(FinanceReceiptClaimReviewStatusEnum.REJECTED.getStatus())
                .reviewerId(reviewerId).reviewTime(LocalDateTime.now()).rejectReason(reason.trim()).build();
        if (claimMapper.updateStatusIfMatch(rejected,
                FinanceReceiptClaimReviewStatusEnum.PENDING.getStatus()) != 1) {
            throw exception(RECEIPT_CLAIM_STATUS_INVALID);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revokeClaim(Long id, Long reviewerId, String reason) {
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
        FinanceReceiptClaimDO revoked = FinanceReceiptClaimDO.builder().id(id)
                .status(FinanceReceiptClaimReviewStatusEnum.REVOKED.getStatus())
                .reviewerId(reviewerId).reviewTime(LocalDateTime.now())
                .revokeReason(reason.trim()).build();
        if (claimMapper.updateStatusIfMatch(revoked,
                FinanceReceiptClaimReviewStatusEnum.CONFIRMED.getStatus()) != 1) {
            throw exception(RECEIPT_CLAIM_CONCURRENT_MODIFICATION);
        }
        Map<Long, BigDecimal> receiptAmounts = aggregate(items, FinanceReceiptClaimItemDO::getReceiptId);
        receiptAmounts.forEach((receiptId, amount) -> {
            if (receiptMapper.decreaseClaimedAmount(receiptId, amount) != 1) {
                throw exception(RECEIPT_CLAIM_CONCURRENT_MODIFICATION);
            }
        });
        Map<Long, BigDecimal> orderAmounts = aggregate(items, FinanceReceiptClaimItemDO::getBusinessOrderId);
        orderAmounts.forEach((orderId, amount) -> {
            if (businessOrderMapper.decreaseConfirmedClaimedAmount(orderId, claim.getClaimantId(), amount) != 1) {
                throw exception(RECEIPT_CLAIM_CONCURRENT_MODIFICATION);
            }
        });
        if (revokeAuditMapper.insert(FinanceReceiptClaimRevokeAuditDO.builder()
                .claimId(id).reviewerId(reviewerId)
                .revokeTime(LocalDateTime.now()).revokeReason(reason.trim())
                .build()) != 1) {
            throw exception(RECEIPT_CLAIM_CONCURRENT_MODIFICATION);
        }
    }

    @Override
    public void resubmitClaim(Long id, Long claimantId) {
        if (claimMapper.updateRejectedToPending(id, claimantId) != 1) {
            throw exception(RECEIPT_CLAIM_STATUS_INVALID);
        }
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
        items.forEach(item -> {
            receiptIds.add(item.getReceiptId());
            businessOrderIds.add(item.getBusinessOrderId());
        });
        Map<Long, FinanceReceiptDO> receipts = new HashMap<>();
        Map<Long, FinanceBusinessOrderDO> businessOrders = new HashMap<>();
        if (!receiptIds.isEmpty()) {
            receiptMapper.selectListByIds(receiptIds).forEach(receipt -> receipts.put(receipt.getId(), receipt));
        }
        if (!businessOrderIds.isEmpty()) {
            businessOrderMapper.selectListByIds(businessOrderIds)
                    .forEach(order -> businessOrders.put(order.getId(), order));
        }
        return new FinanceReceiptClaimDetail(claim, items, receipts, businessOrders);
    }

    @Override
    public List<FinanceReceiptClaimRevokeAuditDO> getRevokeAuditList(Long claimId) {
        return revokeAuditMapper.selectListByClaimId(claimId);
    }

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

    private ValidatedAllocations validateAllocations(List<FinanceReceiptClaimSaveReqVO.Item> items, Long claimantId) {
        if (CollUtil.isEmpty(items)) {
            throw exception(RECEIPT_CLAIM_ITEMS_EMPTY);
        }
        Set<String> pairs = new HashSet<>();
        Map<Long, BigDecimal> receiptAmounts = new HashMap<>();
        Map<Long, BigDecimal> orderAmounts = new HashMap<>();
        BigDecimal totalAmount = ZERO;
        for (FinanceReceiptClaimSaveReqVO.Item item : items) {
            if (item.getClaimAmount() == null || item.getClaimAmount().compareTo(ZERO) <= 0) {
                throw exception(RECEIPT_CLAIM_AMOUNT_INVALID);
            }
            if (!pairs.add(item.getReceiptId() + ":" + item.getBusinessOrderId())) {
                throw exception(RECEIPT_CLAIM_ITEM_DUPLICATE);
            }
            receiptAmounts.merge(item.getReceiptId(), item.getClaimAmount(), BigDecimal::add);
            orderAmounts.merge(item.getBusinessOrderId(), item.getClaimAmount(), BigDecimal::add);
            totalAmount = totalAmount.add(item.getClaimAmount());
        }
        validateReceipts(receiptAmounts);
        validateBusinessOrders(orderAmounts, claimantId);
        return new ValidatedAllocations(totalAmount);
    }

    private void validateReceipts(Map<Long, BigDecimal> amounts) {
        Map<Long, FinanceReceiptDO> receipts = new HashMap<>();
        receiptMapper.selectListByIds(amounts.keySet()).forEach(receipt -> receipts.put(receipt.getId(), receipt));
        for (Map.Entry<Long, BigDecimal> entry : amounts.entrySet()) {
            FinanceReceiptDO receipt = receipts.get(entry.getKey());
            if (receipt != null && FinanceReceiptClaimStatusEnum.CLOSED.getStatus()
                    .equals(receipt.getClaimStatus())) {
                throw exception(RECEIPT_CLAIM_RECEIPT_CLOSED);
            }
            if (receipt == null || receipt.getUnclaimedAmount() == null
                    || receipt.getUnclaimedAmount().compareTo(entry.getValue()) < 0) {
                throw exception(RECEIPT_CLAIM_RECEIPT_INVALID);
            }
        }
    }

    private void validateBusinessOrders(Map<Long, BigDecimal> amounts, Long claimantId) {
        Map<Long, FinanceBusinessOrderDO> orders = new HashMap<>();
        businessOrderMapper.selectListByIds(amounts.keySet()).forEach(order -> orders.put(order.getId(), order));
        for (Map.Entry<Long, BigDecimal> entry : amounts.entrySet()) {
            FinanceBusinessOrderDO order = orders.get(entry.getKey());
            BigDecimal confirmed = order == null || order.getConfirmedClaimedAmount() == null
                    ? ZERO : order.getConfirmedClaimedAmount();
            if (order == null || !Objects.equals(order.getImporterId(), claimantId)
                    || order.getSettlementAmount() == null
                    || order.getSettlementAmount().subtract(confirmed).compareTo(entry.getValue()) < 0) {
                throw exception(RECEIPT_CLAIM_BUSINESS_ORDER_INVALID);
            }
        }
    }

    private void insertItems(Long claimId, List<FinanceReceiptClaimSaveReqVO.Item> items) {
        for (FinanceReceiptClaimSaveReqVO.Item item : items) {
            itemMapper.insert(FinanceReceiptClaimItemDO.builder().claimId(claimId)
                    .receiptId(item.getReceiptId()).businessOrderId(item.getBusinessOrderId())
                    .claimAmount(item.getClaimAmount()).build());
        }
    }

    private static Map<Long, BigDecimal> aggregate(List<FinanceReceiptClaimItemDO> items,
                                                   java.util.function.Function<FinanceReceiptClaimItemDO, Long> idGetter) {
        Map<Long, BigDecimal> amounts = new LinkedHashMap<>();
        items.forEach(item -> amounts.merge(idGetter.apply(item), item.getClaimAmount(), BigDecimal::add));
        return amounts;
    }

    private record ValidatedAllocations(BigDecimal totalAmount) {
    }

}
