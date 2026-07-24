package cn.iocoder.yudao.module.finance.service.claim;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.finance.controller.admin.claim.vo.FinanceReceiptClaimPageReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.claim.vo.FinanceReceiptClaimBusinessOrderSourceRespVO;
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
import cn.iocoder.yudao.module.finance.enums.FinanceBusinessOrderStatusEnum;
import cn.iocoder.yudao.module.finance.enums.FinanceReceiptClaimReviewStatusEnum;
import cn.iocoder.yudao.module.finance.enums.FinanceReceiptClaimStatusEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class FinanceReceiptClaimServiceImplTest {

    private FinanceReceiptClaimMapper claimMapper;
    private FinanceReceiptClaimItemMapper itemMapper;
    private FinanceBankReceiptMapper receiptMapper;
    private FinanceBusinessOrderMapper businessOrderMapper;
    private FinanceReceiptClaimRevokeAuditMapper revokeAuditMapper;
    private FinanceReceiptClaimServiceImpl claimService;

    @BeforeEach
    void setUp() {
        claimMapper = mock(FinanceReceiptClaimMapper.class);
        itemMapper = mock(FinanceReceiptClaimItemMapper.class);
        receiptMapper = mock(FinanceBankReceiptMapper.class);
        businessOrderMapper = mock(FinanceBusinessOrderMapper.class);
        revokeAuditMapper = mock(FinanceReceiptClaimRevokeAuditMapper.class);
        claimService = new FinanceReceiptClaimServiceImpl(claimMapper, itemMapper, receiptMapper,
                businessOrderMapper, revokeAuditMapper);
        doAnswer(invocation -> {
            FinanceReceiptClaimDO claim = invocation.getArgument(0);
            claim.setId(7L);
            return 1;
        }).when(claimMapper).insert(any(FinanceReceiptClaimDO.class));
    }

    @Test
    void createClaimShouldPersistHeaderAndMultipleAllocationsForOwner() {
        stubSources(100L, new BigDecimal("300.00"), new BigDecimal("500.00"));
        FinanceReceiptClaimSaveReqVO reqVO = claim(null,
                item(1L, 10L, "100.00"), item(1L, 11L, "50.00"));

        Long claimId = claimService.createClaim(reqVO, 100L);

        assertNotNull(claimId);
        verify(claimMapper).insert(argThat((FinanceReceiptClaimDO claim) -> claim.getClaimantId().equals(100L)
                && claim.getStatus().equals(FinanceReceiptClaimReviewStatusEnum.PENDING.getStatus())
                && claim.getTotalClaimAmount().compareTo(new BigDecimal("150.00")) == 0));
        verify(itemMapper, times(2)).insert(any(FinanceReceiptClaimItemDO.class));
    }

    @Test
    void createClaimShouldRejectDuplicateReceiptAndBusinessOrderPair() {
        FinanceReceiptClaimSaveReqVO reqVO = claim(null,
                item(1L, 10L, "10.00"), item(1L, 10L, "20.00"));

        assertThrows(RuntimeException.class, () -> claimService.createClaim(reqVO, 100L));

        verifyNoInteractions(claimMapper, itemMapper, receiptMapper, businessOrderMapper);
    }

    @Test
    void createClaimShouldRejectInactiveOrUnownedBusinessOrder() {
        when(receiptMapper.selectListByIds(any())).thenReturn(List.of(receipt(1L, "100.00")));
        when(businessOrderMapper.selectListByIds(any())).thenReturn(List.of(order(10L, 200L, "100.00", "0.00")));

        assertThrows(RuntimeException.class,
                () -> claimService.createClaim(claim(null, item(1L, 10L, "10.00")), 100L));

        verify(claimMapper, never()).insert((FinanceReceiptClaimDO) any());
    }

    @Test
    void updateClaimShouldReplaceRejectedClaimItemsWithoutResubmitting() {
        FinanceReceiptClaimDO rejected = claimDO(7L, 100L, FinanceReceiptClaimReviewStatusEnum.REJECTED.getStatus());
        when(claimMapper.selectById(7L)).thenReturn(rejected);
        when(claimMapper.updateEditableClaim(any(), eq(100L),
                eq(FinanceReceiptClaimReviewStatusEnum.REJECTED.getStatus()))).thenReturn(1);
        stubSources(100L, new BigDecimal("300.00"), new BigDecimal("500.00"));

        claimService.updateClaim(claim(7L, item(1L, 10L, "80.00")), 100L);

        verify(itemMapper).deleteByClaimId(7L);
        verify(itemMapper).insert(argThat((FinanceReceiptClaimItemDO item) ->
                item.getClaimAmount().compareTo(new BigDecimal("80.00")) == 0));
        verify(claimMapper).updateEditableClaim(argThat((FinanceReceiptClaimDO claim) -> claim.getId().equals(7L)
                        && claim.getStatus().equals(FinanceReceiptClaimReviewStatusEnum.REJECTED.getStatus())),
                eq(100L), eq(FinanceReceiptClaimReviewStatusEnum.REJECTED.getStatus()));
    }

    @Test
    void updateClaimShouldStopWhenFinanceChangedStatusConcurrently() {
        when(claimMapper.selectById(7L)).thenReturn(
                claimDO(7L, 100L, FinanceReceiptClaimReviewStatusEnum.PENDING.getStatus()));
        when(claimMapper.updateEditableClaim(any(), eq(100L),
                eq(FinanceReceiptClaimReviewStatusEnum.PENDING.getStatus()))).thenReturn(0);
        stubSources(100L, new BigDecimal("300.00"), new BigDecimal("500.00"));

        assertThrows(RuntimeException.class,
                () -> claimService.updateClaim(claim(7L, item(1L, 10L, "80.00")), 100L));

        verifyNoInteractions(itemMapper);
    }

    @Test
    void updateClaimShouldRejectAnotherClaimant() {
        when(claimMapper.selectById(7L)).thenReturn(
                claimDO(7L, 200L, FinanceReceiptClaimReviewStatusEnum.REJECTED.getStatus()));

        assertThrows(RuntimeException.class,
                () -> claimService.updateClaim(claim(7L, item(1L, 10L, "10.00")), 100L));

        verifyNoInteractions(itemMapper, receiptMapper, businessOrderMapper);
    }

    @Test
    void createClaimShouldAllowExactRemainingSettlementAmount() {
        when(receiptMapper.selectListByIds(any())).thenReturn(List.of(receipt(1L, "60.00")));
        when(businessOrderMapper.selectListByIds(any())).thenReturn(List.of(
                order(10L, 100L, "100.00", "40.00")));

        Long claimId = claimService.createClaim(claim(null, item(1L, 10L, "60.00")), 100L);

        assertEquals(7L, claimId);
        verify(claimMapper).insert(argThat((FinanceReceiptClaimDO claim) ->
                claim.getTotalClaimAmount().compareTo(new BigDecimal("60.00")) == 0));
    }

    @Test
    void createClaimShouldRejectAmountBeyondRemainingSettlement() {
        when(receiptMapper.selectListByIds(any())).thenReturn(List.of(receipt(1L, "60.01")));
        when(businessOrderMapper.selectListByIds(any())).thenReturn(List.of(
                order(10L, 100L, "100.00", "40.00")));

        assertThrows(RuntimeException.class,
                () -> claimService.createClaim(claim(null, item(1L, 10L, "60.01")), 100L));

        verify(claimMapper, never()).insert((FinanceReceiptClaimDO) any());
        verifyNoInteractions(itemMapper);
    }

    @Test
    void createClaimShouldExplicitlyRejectClosedReceiptBeforeBalanceValidation() {
        when(receiptMapper.selectListByIds(any())).thenReturn(List.of(FinanceReceiptDO.builder()
                .id(1L)
                .claimStatus(FinanceReceiptClaimStatusEnum.CLOSED.getStatus())
                .unclaimedAmount(new BigDecimal("100.00"))
                .build()));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> claimService.createClaim(claim(null, item(1L, 10L, "10.00")), 100L));

        assertEquals("已关闭的银行到款不能认领", exception.getMessage());
        verifyNoInteractions(businessOrderMapper, itemMapper);
        verify(claimMapper, never()).insert((FinanceReceiptClaimDO) any());
    }

    @Test
    void createClaimShouldUseBackfilledSettlementForMigratedOrder() {
        when(receiptMapper.selectListByIds(any())).thenReturn(List.of(receipt(1L, "75.00")));
        when(businessOrderMapper.selectListByIds(any())).thenReturn(List.of(
                order(10L, 100L, "100.00", "25.00")));

        Long claimId = claimService.createClaim(claim(null, item(1L, 10L, "75.00")), 100L);

        assertEquals(7L, claimId);
        verify(itemMapper).insert(argThat((FinanceReceiptClaimItemDO item) ->
                item.getClaimAmount().compareTo(new BigDecimal("75.00")) == 0));
    }

    @Test
    void claimableBusinessOrderSourceShouldExposeRemainingSettlementAmount() {
        FinanceReceiptClaimBusinessOrderSourceRespVO source = new FinanceReceiptClaimBusinessOrderSourceRespVO();
        source.setSettlementAmount(new BigDecimal("100.00"));
        source.setConfirmedClaimedAmount(new BigDecimal("40.00"));

        assertEquals(new BigDecimal("60.00"), source.getRemainingClaimableAmount());
    }

    @Test
    void rejectClaimShouldRequireReasonAndNeverChangeBalances() {
        assertThrows(RuntimeException.class, () -> claimService.rejectClaim(7L, 900L, "  "));

        verifyNoInteractions(receiptMapper, businessOrderMapper);
        verify(claimMapper, never()).updateStatusIfMatch(any(), anyInt());
    }

    @Test
    void rejectClaimShouldOnlyTransitionPendingClaim() {
        when(claimMapper.updateStatusIfMatch(any(FinanceReceiptClaimDO.class),
                eq(FinanceReceiptClaimReviewStatusEnum.PENDING.getStatus()))).thenReturn(1);

        claimService.rejectClaim(7L, 900L, "金额归属不清");

        verify(claimMapper).updateStatusIfMatch(argThat(claim -> claim.getId().equals(7L)
                        && claim.getStatus().equals(FinanceReceiptClaimReviewStatusEnum.REJECTED.getStatus())
                        && claim.getReviewerId().equals(900L)
                        && "金额归属不清".equals(claim.getRejectReason())),
                eq(FinanceReceiptClaimReviewStatusEnum.PENDING.getStatus()));
        verifyNoInteractions(receiptMapper, businessOrderMapper);
    }

    @Test
    void resubmitClaimShouldOnlyAllowOriginalClaimantRejectedClaim() {
        when(claimMapper.updateRejectedToPending(7L, 100L)).thenReturn(1);
        claimService.resubmitClaim(7L, 100L);
        verify(claimMapper).updateRejectedToPending(7L, 100L);

        when(claimMapper.updateRejectedToPending(8L, 100L)).thenReturn(0);
        assertThrows(RuntimeException.class, () -> claimService.resubmitClaim(8L, 100L));
    }

    @Test
    void confirmClaimShouldAggregateRepeatedReceiptAndOrderIds() {
        FinanceReceiptClaimDO pending = claimDO(7L, 100L, FinanceReceiptClaimReviewStatusEnum.PENDING.getStatus());
        when(claimMapper.selectById(7L)).thenReturn(pending);
        when(itemMapper.selectListByClaimId(7L)).thenReturn(List.of(
                claimItem(7L, 1L, 10L, "40.00"),
                claimItem(7L, 1L, 11L, "60.00"),
                claimItem(7L, 2L, 10L, "20.00")));
        when(claimMapper.updateStatusIfMatch(any(), eq(FinanceReceiptClaimReviewStatusEnum.PENDING.getStatus()))).thenReturn(1);
        when(receiptMapper.increaseClaimedAmount(anyLong(), any())).thenReturn(1);
        when(businessOrderMapper.increaseConfirmedClaimedAmount(anyLong(), eq(100L), any())).thenReturn(1);

        claimService.confirmClaim(7L, 900L);

        verify(receiptMapper).increaseClaimedAmount(1L, new BigDecimal("100.00"));
        verify(receiptMapper).increaseClaimedAmount(2L, new BigDecimal("20.00"));
        verify(businessOrderMapper).increaseConfirmedClaimedAmount(10L, 100L, new BigDecimal("60.00"));
        verify(businessOrderMapper).increaseConfirmedClaimedAmount(11L, 100L, new BigDecimal("60.00"));
    }

    @Test
    void confirmClaimShouldStopWhenReceiptBalanceChanged() {
        when(claimMapper.selectById(7L)).thenReturn(
                claimDO(7L, 100L, FinanceReceiptClaimReviewStatusEnum.PENDING.getStatus()));
        when(itemMapper.selectListByClaimId(7L)).thenReturn(List.of(claimItem(7L, 1L, 10L, "100.00")));
        when(claimMapper.updateStatusIfMatch(any(), eq(FinanceReceiptClaimReviewStatusEnum.PENDING.getStatus()))).thenReturn(1);
        when(receiptMapper.increaseClaimedAmount(1L, new BigDecimal("100.00"))).thenReturn(0);

        assertThrows(RuntimeException.class, () -> claimService.confirmClaim(7L, 900L));

        verifyNoInteractions(businessOrderMapper);
    }

    @Test
    void confirmClaimShouldPreventDoubleConfirmation() {
        when(claimMapper.selectById(7L)).thenReturn(
                claimDO(7L, 100L, FinanceReceiptClaimReviewStatusEnum.PENDING.getStatus()));
        when(itemMapper.selectListByClaimId(7L)).thenReturn(List.of(claimItem(7L, 1L, 10L, "10.00")));
        when(claimMapper.updateStatusIfMatch(any(), eq(FinanceReceiptClaimReviewStatusEnum.PENDING.getStatus()))).thenReturn(0);

        assertThrows(RuntimeException.class, () -> claimService.confirmClaim(7L, 900L));

        verifyNoInteractions(receiptMapper, businessOrderMapper);
    }

    @Test
    void confirmClaimShouldBeTransactional() throws NoSuchMethodException {
        Transactional transactional = FinanceReceiptClaimServiceImpl.class
                .getMethod("confirmClaim", Long.class, Long.class).getAnnotation(Transactional.class);
        assertNotNull(transactional);
    }

    @Test
    void getMyClaimPageShouldAlwaysScopeToClaimant() {
        FinanceReceiptClaimPageReqVO reqVO = new FinanceReceiptClaimPageReqVO();
        PageResult<FinanceReceiptClaimDO> expected = new PageResult<>(List.of(claimDO(7L, 100L, 0)), 1L);
        when(claimMapper.selectClaimPage(reqVO, 100L)).thenReturn(expected);

        assertSame(expected, claimService.getMyClaimPage(reqVO, 100L));
    }

    @Test
    void getReviewClaimPageShouldNotAddClaimantScope() {
        FinanceReceiptClaimPageReqVO reqVO = new FinanceReceiptClaimPageReqVO();
        PageResult<FinanceReceiptClaimDO> expected = new PageResult<>(List.of(claimDO(7L, 100L, 0)), 1L);
        when(claimMapper.selectClaimPage(reqVO, null)).thenReturn(expected);

        assertSame(expected, claimService.getReviewClaimPage(reqVO));
    }

    @Test
    void getClaimDetailShouldHideAnotherClaimantsClaimFromBusinessUser() {
        when(claimMapper.selectById(7L)).thenReturn(claimDO(7L, 200L, 0));

        assertThrows(RuntimeException.class, () -> claimService.getClaimDetail(7L, 100L, false));

        verifyNoInteractions(itemMapper);
    }

     @Test
     void getClaimDetailShouldReturnItemsToReviewer() {
         FinanceReceiptClaimDO claim = claimDO(7L, 200L, 0);
         List<FinanceReceiptClaimItemDO> items = List.of(claimItem(7L, 1L, 10L, "10.00"));
         when(claimMapper.selectById(7L)).thenReturn(claim);
         when(itemMapper.selectListByClaimId(7L)).thenReturn(items);
         FinanceReceiptDO receipt = FinanceReceiptDO.builder().id(1L).receiptNo("RC-001").build();
         FinanceBusinessOrderDO order = FinanceBusinessOrderDO.builder().id(10L).orderNo("BO-001").build();
         when(receiptMapper.selectListByIds(any())).thenReturn(List.of(receipt));
         when(businessOrderMapper.selectListByIds(any())).thenReturn(List.of(order));

         FinanceReceiptClaimDetail detail = claimService.getClaimDetail(7L, 900L, true);

         assertSame(claim, detail.claim());
         assertSame(items, detail.items());
         assertSame(receipt, detail.receipts().get(1L));
         assertSame(order, detail.businessOrders().get(10L));
     }

     @Test
     void revokeClaimShouldRollbackConfirmedClaimWithValidReason() {
         FinanceReceiptClaimDO confirmed = claimDO(7L, 100L, FinanceReceiptClaimReviewStatusEnum.CONFIRMED.getStatus());
         when(claimMapper.selectById(7L)).thenReturn(confirmed);
         when(itemMapper.selectListByClaimId(7L)).thenReturn(List.of(
                 claimItem(7L, 1L, 10L, "40.00"),
                 claimItem(7L, 1L, 11L, "60.00"),
                 claimItem(7L, 2L, 10L, "20.00")));
         when(claimMapper.updateStatusIfMatch(any(), eq(FinanceReceiptClaimReviewStatusEnum.CONFIRMED.getStatus()))).thenReturn(1);
         when(receiptMapper.decreaseClaimedAmount(anyLong(), any())).thenReturn(1);
         when(businessOrderMapper.decreaseConfirmedClaimedAmount(anyLong(), eq(100L), any())).thenReturn(1);
         when(revokeAuditMapper.insert(any(FinanceReceiptClaimRevokeAuditDO.class))).thenReturn(1);

         claimService.revokeClaim(7L, 900L, "金额有误");

         verify(claimMapper).updateStatusIfMatch(argThat(claim -> claim.getId().equals(7L)
                 && claim.getStatus().equals(FinanceReceiptClaimReviewStatusEnum.REVOKED.getStatus())
                 && claim.getReviewerId().equals(900L)
                 && "金额有误".equals(claim.getRevokeReason())),
                 eq(FinanceReceiptClaimReviewStatusEnum.CONFIRMED.getStatus()));
         verify(receiptMapper).decreaseClaimedAmount(1L, new BigDecimal("100.00"));
         verify(receiptMapper).decreaseClaimedAmount(2L, new BigDecimal("20.00"));
         verify(businessOrderMapper).decreaseConfirmedClaimedAmount(10L, 100L, new BigDecimal("60.00"));
         verify(businessOrderMapper).decreaseConfirmedClaimedAmount(11L, 100L, new BigDecimal("60.00"));
         verify(revokeAuditMapper).insert(argThat((FinanceReceiptClaimRevokeAuditDO audit) ->
                 audit.getClaimId().equals(7L) && audit.getReviewerId().equals(900L)
                         && "金额有误".equals(audit.getRevokeReason()) && audit.getRevokeTime() != null));
     }

     @Test
     void revokeClaimShouldRejectBlankReason() {
         assertThrows(RuntimeException.class, () -> claimService.revokeClaim(7L, 900L, "  "));

         verifyNoInteractions(claimMapper, receiptMapper, businessOrderMapper);
     }

     @Test
     void revokeClaimShouldOnlyTransitionConfirmedClaim() {
         when(claimMapper.selectById(7L)).thenReturn(
                 claimDO(7L, 100L, FinanceReceiptClaimReviewStatusEnum.PENDING.getStatus()));

         assertThrows(RuntimeException.class, () -> claimService.revokeClaim(7L, 900L, "金额有误"));

         verifyNoInteractions(itemMapper, receiptMapper, businessOrderMapper);
     }

     @Test
     void revokeClaimShouldNotChangeBalancesIfCompareAndSetFails() {
         FinanceReceiptClaimDO confirmed = claimDO(7L, 100L, FinanceReceiptClaimReviewStatusEnum.CONFIRMED.getStatus());
         when(claimMapper.selectById(7L)).thenReturn(confirmed);
         when(itemMapper.selectListByClaimId(7L)).thenReturn(List.of(claimItem(7L, 1L, 10L, "100.00")));
         when(claimMapper.updateStatusIfMatch(any(), eq(FinanceReceiptClaimReviewStatusEnum.CONFIRMED.getStatus()))).thenReturn(0);

         assertThrows(RuntimeException.class, () -> claimService.revokeClaim(7L, 900L, "金额有误"));

         verifyNoInteractions(receiptMapper, businessOrderMapper);
     }

     @Test
     void revokeClaimShouldThrowWhenAuditInsertDoesNotAffectExactlyOneRow() {
         FinanceReceiptClaimDO confirmed = claimDO(7L, 100L, FinanceReceiptClaimReviewStatusEnum.CONFIRMED.getStatus());
         when(claimMapper.selectById(7L)).thenReturn(confirmed);
         when(itemMapper.selectListByClaimId(7L)).thenReturn(List.of(claimItem(7L, 1L, 10L, "100.00")));
         when(claimMapper.updateStatusIfMatch(any(), eq(FinanceReceiptClaimReviewStatusEnum.CONFIRMED.getStatus()))).thenReturn(1);
         when(receiptMapper.decreaseClaimedAmount(anyLong(), any())).thenReturn(1);
         when(businessOrderMapper.decreaseConfirmedClaimedAmount(anyLong(), eq(100L), any())).thenReturn(1);
         when(revokeAuditMapper.insert(any(FinanceReceiptClaimRevokeAuditDO.class))).thenReturn(0);

         assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                 () -> claimService.revokeClaim(7L, 900L, "金额有误"));

         verify(revokeAuditMapper).insert(any(FinanceReceiptClaimRevokeAuditDO.class));
     }

     @Test
     void revokeClaimShouldBeTransactional() throws NoSuchMethodException {
         Transactional transactional = FinanceReceiptClaimServiceImpl.class
                 .getMethod("revokeClaim", Long.class, Long.class, String.class).getAnnotation(Transactional.class);
         assertNotNull(transactional);
     }

     @Test
     void getRevokeAuditListShouldDelegateToMapperAndReturnOrderedResult() {
         List<FinanceReceiptClaimRevokeAuditDO> expected = List.of(
                 FinanceReceiptClaimRevokeAuditDO.builder().id(3L).claimId(7L).reviewerId(900L)
                         .revokeTime(java.time.LocalDateTime.of(2026, 1, 15, 10, 0)).revokeReason("第二次撤销").build(),
                 FinanceReceiptClaimRevokeAuditDO.builder().id(1L).claimId(7L).reviewerId(900L)
                         .revokeTime(java.time.LocalDateTime.of(2026, 1, 10, 9, 0)).revokeReason("第一次撤销").build());
         when(revokeAuditMapper.selectListByClaimId(7L)).thenReturn(expected);

         List<FinanceReceiptClaimRevokeAuditDO> actual = claimService.getRevokeAuditList(7L);

         assertSame(expected, actual);
         assertEquals(2, actual.size());
         assertEquals(3L, actual.get(0).getId());
         assertEquals(1L, actual.get(1).getId());
         verify(revokeAuditMapper).selectListByClaimId(7L);
     }

     @Test
     void getRevokeAuditListShouldReturnEmptyListWhenNoAudits() {
         when(revokeAuditMapper.selectListByClaimId(99L)).thenReturn(List.of());

         List<FinanceReceiptClaimRevokeAuditDO> actual = claimService.getRevokeAuditList(99L);

         assertTrue(actual.isEmpty());
         verify(revokeAuditMapper).selectListByClaimId(99L);
     }

    private void stubSources(Long importerId, BigDecimal receiptBalance, BigDecimal settlementAmount) {
        when(receiptMapper.selectListByIds(any())).thenReturn(List.of(receipt(1L, receiptBalance.toPlainString())));
        when(businessOrderMapper.selectListByIds(any())).thenReturn(List.of(
                order(10L, importerId, settlementAmount.toPlainString(), "0.00"),
                order(11L, importerId, settlementAmount.toPlainString(), "0.00")));
    }

    private static FinanceReceiptClaimSaveReqVO claim(Long id, FinanceReceiptClaimSaveReqVO.Item... items) {
        FinanceReceiptClaimSaveReqVO reqVO = new FinanceReceiptClaimSaveReqVO();
        reqVO.setId(id);
        reqVO.setRemark("认领说明");
        reqVO.setItems(List.of(items));
        return reqVO;
    }

    private static FinanceReceiptClaimSaveReqVO.Item item(Long receiptId, Long orderId, String amount) {
        FinanceReceiptClaimSaveReqVO.Item item = new FinanceReceiptClaimSaveReqVO.Item();
        item.setReceiptId(receiptId);
        item.setBusinessOrderId(orderId);
        item.setClaimAmount(new BigDecimal(amount));
        return item;
    }

    private static FinanceReceiptDO receipt(Long id, String unclaimedAmount) {
        return FinanceReceiptDO.builder().id(id).unclaimedAmount(new BigDecimal(unclaimedAmount)).build();
    }

    private static FinanceBusinessOrderDO order(Long id, Long importerId,
                                                 String settlementAmount, String confirmedAmount) {
        return FinanceBusinessOrderDO.builder().id(id).importerId(importerId)
                .settlementAmount(new BigDecimal(settlementAmount))
                .confirmedClaimedAmount(new BigDecimal(confirmedAmount)).build();
    }

    private static FinanceReceiptClaimDO claimDO(Long id, Long claimantId, Integer status) {
        return FinanceReceiptClaimDO.builder().id(id).claimantId(claimantId).status(status).build();
    }

    private static FinanceReceiptClaimItemDO claimItem(Long claimId, Long receiptId, Long orderId, String amount) {
        return FinanceReceiptClaimItemDO.builder().claimId(claimId).receiptId(receiptId)
                .businessOrderId(orderId).claimAmount(new BigDecimal(amount)).build();
    }

}
