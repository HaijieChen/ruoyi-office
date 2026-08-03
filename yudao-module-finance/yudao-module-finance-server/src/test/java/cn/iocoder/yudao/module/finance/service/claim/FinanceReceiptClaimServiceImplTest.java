package cn.iocoder.yudao.module.finance.service.claim;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.finance.controller.admin.claim.vo.FinanceReceiptClaimPageReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.claim.vo.FinanceReceiptClaimSaveReqVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.claim.FinanceReceiptClaimDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.claim.FinanceReceiptClaimItemDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.claim.FinanceReceiptClaimRevokeAuditDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.invoice.FinanceInvoiceApplicationDO;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.RECEIPT_CLAIM_LEGACY_WRITE_FORBIDDEN;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 认领改挂开票 + 双边 pending + LEGACY 拒写。
 */
class FinanceReceiptClaimServiceImplTest {

    private FinanceReceiptClaimMapper claimMapper;
    private FinanceReceiptClaimItemMapper itemMapper;
    private FinanceBankReceiptMapper receiptMapper;
    private FinanceBusinessOrderMapper businessOrderMapper;
    private FinanceInvoiceApplicationMapper invoiceApplicationMapper;
    private FinanceInvoiceApplicationLineMapper invoiceLineMapper;
    private FinanceReceiptClaimRevokeAuditMapper revokeAuditMapper;
    private FinanceReceiptClaimServiceImpl claimService;

    @BeforeEach
    void setUp() {
        claimMapper = mock(FinanceReceiptClaimMapper.class);
        itemMapper = mock(FinanceReceiptClaimItemMapper.class);
        receiptMapper = mock(FinanceBankReceiptMapper.class);
        businessOrderMapper = mock(FinanceBusinessOrderMapper.class);
        invoiceApplicationMapper = mock(FinanceInvoiceApplicationMapper.class);
        invoiceLineMapper = mock(FinanceInvoiceApplicationLineMapper.class);
        revokeAuditMapper = mock(FinanceReceiptClaimRevokeAuditMapper.class);
        claimService = new FinanceReceiptClaimServiceImpl(claimMapper, itemMapper, receiptMapper,
                businessOrderMapper, invoiceApplicationMapper, invoiceLineMapper, revokeAuditMapper);
        doAnswer(invocation -> {
            FinanceReceiptClaimDO claim = invocation.getArgument(0);
            claim.setId(7L);
            return 1;
        }).when(claimMapper).insert(any(FinanceReceiptClaimDO.class));
        when(receiptMapper.increasePendingClaimedAmount(anyLong(), any())).thenReturn(1);
        when(invoiceApplicationMapper.increasePendingClaimedAmount(anyLong(), any())).thenReturn(1);
        when(receiptMapper.decreasePendingClaimedAmount(anyLong(), any())).thenReturn(1);
        when(invoiceApplicationMapper.decreasePendingClaimedAmount(anyLong(), any())).thenReturn(1);
        when(invoiceApplicationMapper.confirmPendingToClaimed(anyLong(), any())).thenReturn(1);
        when(invoiceApplicationMapper.decreaseConfirmedClaimedAmount(anyLong(), any())).thenReturn(1);
        when(receiptMapper.increaseClaimedAmount(anyLong(), any())).thenReturn(1);
        when(receiptMapper.decreaseClaimedAmount(anyLong(), any())).thenReturn(1);
    }

    @Test
    void createClaimShouldOccupyPendingThenAutoConfirm() {
        stubInvoiceSources(100L, "300.00", "0.00", "500.00", "0.00", "0.00");
        when(claimMapper.selectById(7L)).thenReturn(
                claimDO(7L, 100L, FinanceReceiptClaimReviewStatusEnum.PENDING.getStatus()));
        when(itemMapper.selectListByClaimId(7L)).thenReturn(List.of(
                invoiceItem(7L, 1L, 20L, "100.00"),
                invoiceItem(7L, 1L, 21L, "50.00")));
        when(claimMapper.updateStatusIfMatch(any(), eq(FinanceReceiptClaimReviewStatusEnum.PENDING.getStatus())))
                .thenReturn(1);

        Long claimId = claimService.createClaim(claim(null, item(1L, 20L, "100.00"), item(1L, 21L, "50.00")), 100L);

        assertEquals(7L, claimId);
        verify(receiptMapper).increasePendingClaimedAmount(1L, new BigDecimal("150.00"));
        verify(invoiceApplicationMapper).increasePendingClaimedAmount(20L, new BigDecimal("100.00"));
        verify(invoiceApplicationMapper).increasePendingClaimedAmount(21L, new BigDecimal("50.00"));
        // 自动确认：pending→claimed
        verify(receiptMapper).decreasePendingClaimedAmount(1L, new BigDecimal("150.00"));
        verify(receiptMapper).increaseClaimedAmount(1L, new BigDecimal("150.00"));
        verify(invoiceApplicationMapper).confirmPendingToClaimed(20L, new BigDecimal("100.00"));
        verify(invoiceApplicationMapper).confirmPendingToClaimed(21L, new BigDecimal("50.00"));
        verify(claimMapper).updateStatusIfMatch(argThat(c ->
                        FinanceReceiptClaimReviewStatusEnum.CONFIRMED.getStatus().equals(c.getStatus())
                                && Long.valueOf(100L).equals(c.getReviewerId())),
                eq(FinanceReceiptClaimReviewStatusEnum.PENDING.getStatus()));
        verify(itemMapper, times(2)).insert(argThat((FinanceReceiptClaimItemDO i) ->
                FinanceReceiptClaimSourceEnum.INVOICE.getSource().equals(i.getClaimSource())
                        && i.getBusinessOrderId() == null
                        && i.getInvoiceApplicationId() != null));
    }

    @Test
    void createClaimShouldRejectDuplicateReceiptInvoicePair() {
        assertThrows(RuntimeException.class, () ->
                claimService.createClaim(claim(null, item(1L, 20L, "10.00"), item(1L, 20L, "20.00")), 100L));
        verify(claimMapper, never()).insert(any(FinanceReceiptClaimDO.class));
    }

    @Test
    void createClaimShouldRejectWhenReceiptPendingInsufficient() {
        when(receiptMapper.selectListByIds(any())).thenReturn(List.of(
                receipt(1L, "100.00", "80.00"))); // available 20
        when(invoiceApplicationMapper.selectListByIds(any())).thenReturn(List.of(approvedApp(20L, 100L, "500.00")));

        assertThrows(RuntimeException.class,
                () -> claimService.createClaim(claim(null, item(1L, 20L, "30.00")), 100L));
        verify(claimMapper, never()).insert(any(FinanceReceiptClaimDO.class));
    }

    @Test
    void createClaimShouldRejectClosedReceipt() {
        when(receiptMapper.selectListByIds(any())).thenReturn(List.of(FinanceReceiptDO.builder()
                .id(1L).claimStatus(FinanceReceiptClaimStatusEnum.CLOSED.getStatus())
                .businessFund(Boolean.TRUE)
                .unclaimedAmount(new BigDecimal("100.00")).pendingClaimedAmount(ZERO).build()));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> claimService.createClaim(claim(null, item(1L, 20L, "10.00")), 100L));
        assertTrue(ex.getMessage().contains("关闭"));
    }

    @Test
    void createClaimShouldRejectNonBusinessFundReceipt() {
        when(receiptMapper.selectListByIds(any())).thenReturn(List.of(FinanceReceiptDO.builder()
                .id(1L).claimStatus(FinanceReceiptClaimStatusEnum.UNCLAIMED.getStatus())
                .businessFund(Boolean.FALSE)
                .unclaimedAmount(new BigDecimal("100.00")).pendingClaimedAmount(ZERO).build()));
        when(invoiceApplicationMapper.selectListByIds(any())).thenReturn(List.of(approvedApp(20L, 100L, "500.00")));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> claimService.createClaim(claim(null, item(1L, 20L, "10.00")), 100L));
        assertTrue(ex.getMessage().contains("业务款"));
        verify(claimMapper, never()).insert(any(FinanceReceiptClaimDO.class));
    }

    @Test
    void createClaimShouldRejectUnapprovedInvoice() {
        when(receiptMapper.selectListByIds(any())).thenReturn(List.of(receipt(1L, "100.00", "0.00")));
        FinanceInvoiceApplicationDO pending = approvedApp(20L, 100L, "100.00");
        pending.setApprovalStatus(FinanceInvoiceApprovalStatusEnum.PENDING.getStatus());
        when(invoiceApplicationMapper.selectListByIds(any())).thenReturn(List.of(pending));

        assertThrows(RuntimeException.class,
                () -> claimService.createClaim(claim(null, item(1L, 20L, "10.00")), 100L));
    }

    @Test
    void rejectClaimShouldReleasePendingBothSides() {
        when(claimMapper.selectById(7L)).thenReturn(
                claimDO(7L, 100L, FinanceReceiptClaimReviewStatusEnum.PENDING.getStatus()));
        when(itemMapper.selectListByClaimId(7L)).thenReturn(List.of(invoiceItem(7L, 1L, 20L, "40.00")));
        when(claimMapper.updateStatusIfMatch(any(), eq(FinanceReceiptClaimReviewStatusEnum.PENDING.getStatus())))
                .thenReturn(1);

        claimService.rejectClaim(7L, 900L, "金额归属不清");

        verify(receiptMapper).decreasePendingClaimedAmount(1L, new BigDecimal("40.00"));
        verify(invoiceApplicationMapper).decreasePendingClaimedAmount(20L, new BigDecimal("40.00"));
    }

    @Test
    void confirmClaimShouldMovePendingToConfirmed() {
        when(claimMapper.selectById(7L)).thenReturn(
                claimDO(7L, 100L, FinanceReceiptClaimReviewStatusEnum.PENDING.getStatus()));
        when(itemMapper.selectListByClaimId(7L)).thenReturn(List.of(
                invoiceItem(7L, 1L, 20L, "40.00"),
                invoiceItem(7L, 1L, 21L, "60.00")));
        when(claimMapper.updateStatusIfMatch(any(), eq(FinanceReceiptClaimReviewStatusEnum.PENDING.getStatus())))
                .thenReturn(1);

        claimService.confirmClaim(7L, 900L);

        verify(receiptMapper).decreasePendingClaimedAmount(1L, new BigDecimal("100.00"));
        verify(receiptMapper).increaseClaimedAmount(1L, new BigDecimal("100.00"));
        verify(invoiceApplicationMapper).confirmPendingToClaimed(20L, new BigDecimal("40.00"));
        verify(invoiceApplicationMapper).confirmPendingToClaimed(21L, new BigDecimal("60.00"));
    }

    @Test
    void revokeClaimShouldRejectLegacySource() {
        when(claimMapper.selectById(7L)).thenReturn(
                claimDO(7L, 100L, FinanceReceiptClaimReviewStatusEnum.CONFIRMED.getStatus()));
        when(itemMapper.selectListByClaimId(7L)).thenReturn(List.of(
                FinanceReceiptClaimItemDO.builder().claimId(7L).receiptId(1L).businessOrderId(10L)
                        .claimSource(FinanceReceiptClaimSourceEnum.LEGACY_BO.getSource())
                        .claimAmount(new BigDecimal("10.00")).build()));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> claimService.revokeClaim(7L, 900L, "FA", "误认"));
        assertEquals(RECEIPT_CLAIM_LEGACY_WRITE_FORBIDDEN.getCode(), ex.getCode());
        verify(receiptMapper, never()).decreaseClaimedAmount(anyLong(), any());
    }

    @Test
    void revokeClaimShouldRollbackConfirmedInvoiceClaim() {
        when(claimMapper.selectById(7L)).thenReturn(
                claimDO(7L, 100L, FinanceReceiptClaimReviewStatusEnum.CONFIRMED.getStatus()));
        when(itemMapper.selectListByClaimId(7L)).thenReturn(List.of(invoiceItem(7L, 1L, 20L, "100.00")));
        when(claimMapper.updateStatusIfMatch(any(), eq(FinanceReceiptClaimReviewStatusEnum.CONFIRMED.getStatus())))
                .thenReturn(1);
        when(revokeAuditMapper.insert(any(FinanceReceiptClaimRevokeAuditDO.class))).thenReturn(1);

        claimService.revokeClaim(7L, 900L, "FA", "金额有误");

        verify(receiptMapper).decreaseClaimedAmount(1L, new BigDecimal("100.00"));
        verify(invoiceApplicationMapper).decreaseConfirmedClaimedAmount(20L, new BigDecimal("100.00"));
        verify(revokeAuditMapper).insert(any(FinanceReceiptClaimRevokeAuditDO.class));
    }

    @Test
    void resubmitShouldReapplyPendingThenAutoConfirm() {
        // resubmit 先读 REJECTED；confirm 再读 PENDING
        when(claimMapper.selectById(7L))
                .thenReturn(claimDO(7L, 100L, FinanceReceiptClaimReviewStatusEnum.REJECTED.getStatus()))
                .thenReturn(claimDO(7L, 100L, FinanceReceiptClaimReviewStatusEnum.PENDING.getStatus()));
        when(itemMapper.selectListByClaimId(7L)).thenReturn(List.of(invoiceItem(7L, 1L, 20L, "30.00")));
        when(claimMapper.updateRejectedToPending(7L, 100L)).thenReturn(1);
        when(claimMapper.updateStatusIfMatch(any(), eq(FinanceReceiptClaimReviewStatusEnum.PENDING.getStatus())))
                .thenReturn(1);
        stubInvoiceSources(100L, "300.00", "0.00", "500.00", "0.00", "0.00");

        claimService.resubmitClaim(7L, 100L);

        verify(receiptMapper).increasePendingClaimedAmount(1L, new BigDecimal("30.00"));
        verify(invoiceApplicationMapper).increasePendingClaimedAmount(20L, new BigDecimal("30.00"));
        verify(receiptMapper).decreasePendingClaimedAmount(1L, new BigDecimal("30.00"));
        verify(receiptMapper).increaseClaimedAmount(1L, new BigDecimal("30.00"));
        verify(invoiceApplicationMapper).confirmPendingToClaimed(20L, new BigDecimal("30.00"));
        // 与 create 对称：状态 CONFIRMED 且 reviewerId=认领人
        verify(claimMapper).updateStatusIfMatch(argThat(c ->
                        FinanceReceiptClaimReviewStatusEnum.CONFIRMED.getStatus().equals(c.getStatus())
                                && Long.valueOf(100L).equals(c.getReviewerId())),
                eq(FinanceReceiptClaimReviewStatusEnum.PENDING.getStatus()));
    }

    @Test
    void getMyClaimPageShouldScopeToClaimant() {
        FinanceReceiptClaimPageReqVO reqVO = new FinanceReceiptClaimPageReqVO();
        PageResult<FinanceReceiptClaimDO> expected = new PageResult<>(List.of(claimDO(7L, 100L, 0)), 1L);
        when(claimMapper.selectClaimPage(reqVO, 100L)).thenReturn(expected);
        assertSame(expected, claimService.getMyClaimPage(reqVO, 100L));
    }

    @Test
    void getClaimDetailShouldHideOtherUsersClaim() {
        when(claimMapper.selectById(7L)).thenReturn(claimDO(7L, 200L, 0));
        assertThrows(RuntimeException.class, () -> claimService.getClaimDetail(7L, 100L, false));
    }

    @Test
    void confirmClaimShouldBeTransactional() throws NoSuchMethodException {
        Transactional transactional = FinanceReceiptClaimServiceImpl.class
                .getMethod("confirmClaim", Long.class, Long.class).getAnnotation(Transactional.class);
        assertNotNull(transactional);
    }

    @Test
    void claimAllowedHelperIgnoresIssueStatus() {
        FinanceInvoiceApplicationDO app = approvedApp(1L, 1L, "100.00");
        app.setIssueStatus(0); // 未开票
        assertTrue(FinanceReceiptClaimServiceImpl.isClaimAllowed(app));
        app.setApprovalStatus(FinanceInvoiceApprovalStatusEnum.PENDING.getStatus());
        assertFalse(FinanceReceiptClaimServiceImpl.isClaimAllowed(app));
    }

    // ---------- helpers ----------

    private void stubInvoiceSources(Long applicantId, String unclaimed, String receiptPending,
                                    String invoiceTotal, String confirmed, String invoicePending) {
        when(receiptMapper.selectListByIds(any())).thenReturn(List.of(
                receipt(1L, unclaimed, receiptPending)));
        when(invoiceApplicationMapper.selectListByIds(any())).thenReturn(List.of(
                approvedApp(20L, applicantId, invoiceTotal, confirmed, invoicePending),
                approvedApp(21L, applicantId, invoiceTotal, confirmed, invoicePending)));
    }

    private static FinanceReceiptClaimSaveReqVO claim(Long id, FinanceReceiptClaimSaveReqVO.Item... items) {
        FinanceReceiptClaimSaveReqVO reqVO = new FinanceReceiptClaimSaveReqVO();
        reqVO.setId(id);
        reqVO.setRemark("认领说明");
        reqVO.setItems(List.of(items));
        return reqVO;
    }

    private static FinanceReceiptClaimSaveReqVO.Item item(Long receiptId, Long invoiceAppId, String amount) {
        FinanceReceiptClaimSaveReqVO.Item item = new FinanceReceiptClaimSaveReqVO.Item();
        item.setReceiptId(receiptId);
        item.setInvoiceApplicationId(invoiceAppId);
        item.setClaimAmount(new BigDecimal(amount));
        return item;
    }

    private static FinanceReceiptDO receipt(Long id, String unclaimed, String pending) {
        return FinanceReceiptDO.builder().id(id)
                .claimStatus(FinanceReceiptClaimStatusEnum.UNCLAIMED.getStatus())
                .businessFund(Boolean.TRUE)
                .unclaimedAmount(new BigDecimal(unclaimed))
                .pendingClaimedAmount(new BigDecimal(pending))
                .build();
    }

    private static FinanceInvoiceApplicationDO approvedApp(Long id, Long applicantId, String total) {
        return approvedApp(id, applicantId, total, "0.00", "0.00");
    }

    private static FinanceInvoiceApplicationDO approvedApp(Long id, Long applicantId, String total,
                                                           String confirmed, String pending) {
        return FinanceInvoiceApplicationDO.builder()
                .id(id)
                .applicationNo("INV-" + id)
                .approvalStatus(FinanceInvoiceApprovalStatusEnum.APPROVED.getStatus())
                .applicantUserId(applicantId)
                .totalAmount(new BigDecimal(total))
                .confirmedClaimedAmount(new BigDecimal(confirmed))
                .pendingClaimedAmount(new BigDecimal(pending))
                .voided(Boolean.FALSE)
                .build();
    }

    private static FinanceReceiptClaimDO claimDO(Long id, Long claimantId, Integer status) {
        return FinanceReceiptClaimDO.builder().id(id).claimantId(claimantId).status(status).build();
    }

    private static FinanceReceiptClaimItemDO invoiceItem(Long claimId, Long receiptId, Long invoiceId, String amount) {
        return FinanceReceiptClaimItemDO.builder()
                .claimId(claimId).receiptId(receiptId)
                .invoiceApplicationId(invoiceId)
                .claimSource(FinanceReceiptClaimSourceEnum.INVOICE.getSource())
                .claimAmount(new BigDecimal(amount)).build();
    }

    private static final BigDecimal ZERO = new BigDecimal("0.00");

}
