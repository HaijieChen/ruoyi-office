package cn.iocoder.yudao.module.finance.service.expense;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.finance.controller.admin.expense.vo.FinanceExpenseApproveReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.expense.vo.FinanceExpenseRecordPayReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.expense.vo.FinanceExpenseReimbursementCreateReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.expense.vo.FinanceExpenseReimbursementLineReqVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.expense.FinanceExpenseReimbursementDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.expense.FinanceExpenseReimbursementLineDO;
import cn.iocoder.yudao.module.finance.dal.mysql.expense.FinanceExpenseReimbursementLineMapper;
import cn.iocoder.yudao.module.finance.dal.mysql.expense.FinanceExpenseReimbursementMapper;
import cn.iocoder.yudao.module.finance.framework.rpc.FinanceBpmProcessInstanceApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.EXPENSE_REIMBURSEMENT_APPROVED_AMOUNT_INVALID;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.EXPENSE_REIMBURSEMENT_LINE_KIND_MISMATCH;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.EXPENSE_REIMBURSEMENT_INVOICE_REQUIRED;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.EXPENSE_REIMBURSEMENT_PAY_ACCOUNT_REQUIRED;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.EXPENSE_REIMBURSEMENT_PREDOC_REQUIRED;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.EXPENSE_REIMBURSEMENT_LINES_EMPTY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FinanceExpenseReimbursementServiceImplTest {

    private FinanceExpenseReimbursementMapper mapper;
    private FinanceExpenseReimbursementLineMapper lineMapper;
    private FinanceExpenseReimbursementServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(FinanceExpenseReimbursementMapper.class);
        lineMapper = mock(FinanceExpenseReimbursementLineMapper.class);
        AdminUserApi users = mock(AdminUserApi.class);
        FinanceBpmProcessInstanceApi bpm = mock(FinanceBpmProcessInstanceApi.class);
        AdminUserRespDTO user = new AdminUserRespDTO();
        user.setId(1L);
        user.setDeptId(10L);
        user.setNickname("张三");
        when(users.getUser(anyLong())).thenReturn(CommonResult.success(user));
        CommonResult<String> pi = mock(CommonResult.class);
        when(pi.getCheckedData()).thenReturn("proc-1");
        when(bpm.createProcessInstance(anyLong(), any())).thenReturn(pi);
        doAnswer(inv -> {
            FinanceExpenseReimbursementDO row = inv.getArgument(0);
            row.setId(88L);
            return 1;
        }).when(mapper).insert(any(FinanceExpenseReimbursementDO.class));
        FinanceExpensePredocService predoc = mock(FinanceExpensePredocService.class);
        when(predoc.isApprovedTrip(anyLong(), any())).thenReturn(true);
        when(predoc.isApprovedOuting(anyLong(), any())).thenReturn(true);
        service = new FinanceExpenseReimbursementServiceImpl(mapper, lineMapper, users, bpm,
                mock(cn.iocoder.yudao.module.finance.service.companyaccount.FinanceCompanyBankAccountService.class),
                predoc);
    }

    @Test
    void createSumsApplyAmountAndStartsProcess() {
        Long id = service.create(baseReq(false), 1L);
        assertEquals(88L, id);
        ArgumentCaptor<FinanceExpenseReimbursementDO> cap =
                ArgumentCaptor.forClass(FinanceExpenseReimbursementDO.class);
        verify(mapper).insert(cap.capture());
        assertEquals(new BigDecimal("30.00"), cap.getValue().getApplyAmount());
        assertEquals("【报销】-张三-2026-08-30.00", cap.getValue().getProcessTitle());
        verify(mapper).updateById(any(FinanceExpenseReimbursementDO.class));
    }

    @Test
    void emptyLinesRejected() {
        FinanceExpenseReimbursementCreateReqVO req = baseReq(false);
        req.setLines(List.of());
        ServiceException ex = assertThrows(ServiceException.class, () -> service.create(req, 1L));
        assertEquals(EXPENSE_REIMBURSEMENT_LINES_EMPTY.getCode(), ex.getCode());
    }

    @Test
    void proxyRejectsNormalLines() {
        ServiceException ex = assertThrows(ServiceException.class, () -> service.create(baseReq(true), 1L));
        assertEquals(EXPENSE_REIMBURSEMENT_LINE_KIND_MISMATCH.getCode(), ex.getCode());
    }

    @Test
    void approveRejectsAmountOverApply() {
        when(mapper.selectById(88L)).thenReturn(FinanceExpenseReimbursementDO.builder()
                .id(88L).applyAmount(new BigDecimal("30.00"))
                .status(FinanceExpenseReimbursementDO.STATUS_PENDING).build());
        FinanceExpenseApproveReqVO req = new FinanceExpenseApproveReqVO();
        req.setId(88L);
        req.setApprovedAmount(new BigDecimal("40"));
        ServiceException ex = assertThrows(ServiceException.class, () -> service.approve(req, 1L));
        assertEquals(EXPENSE_REIMBURSEMENT_APPROVED_AMOUNT_INVALID.getCode(), ex.getCode());
    }

    @Test
    void recordPayRequiresCompanyAccount() {
        FinanceExpenseRecordPayReqVO req = new FinanceExpenseRecordPayReqVO();
        req.setId(88L);
        req.setActualPayDate(LocalDate.of(2026, 8, 20));
        req.setPayVoucherUrl("https://x/v.pdf");
        ServiceException ex = assertThrows(ServiceException.class, () -> service.recordPay(req, 1L));
        assertEquals(EXPENSE_REIMBURSEMENT_PAY_ACCOUNT_REQUIRED.getCode(), ex.getCode());
    }

    @Test
    void withInvoiceRejectsMissingInvoice() {
        FinanceExpenseReimbursementCreateReqVO req = baseReq(false);
        req.getLines().get(0).setInvoiceFileUrl(null);
        ServiceException ex = assertThrows(ServiceException.class, () -> service.create(req, 1L));
        assertEquals(EXPENSE_REIMBURSEMENT_INVOICE_REQUIRED.getCode(), ex.getCode());
    }

    @Test
    void travelRequiresTripPredoc() {
        FinanceExpenseReimbursementCreateReqVO req = baseReq(false);
        req.getLines().get(0).setCategory("travel");
        req.getLines().get(0).setPredocType(null);
        ServiceException ex = assertThrows(ServiceException.class, () -> service.create(req, 1L));
        assertEquals(EXPENSE_REIMBURSEMENT_PREDOC_REQUIRED.getCode(), ex.getCode());
    }

    private static FinanceExpenseReimbursementCreateReqVO baseReq(boolean proxy) {
        FinanceExpenseReimbursementLineReqVO line = new FinanceExpenseReimbursementLineReqVO();
        line.setLineKind(FinanceExpenseReimbursementLineDO.KIND_NORMAL);
        line.setCategory("office");
        line.setFeeDate(LocalDate.of(2026, 8, 1));
        line.setAmount(new BigDecimal("10"));
        line.setInvoiceFileUrl("https://files.example/inv1.jpg");
        FinanceExpenseReimbursementLineReqVO line2 = new FinanceExpenseReimbursementLineReqVO();
        line2.setLineKind(FinanceExpenseReimbursementLineDO.KIND_NORMAL);
        line2.setCategory("office");
        line2.setFeeDate(LocalDate.of(2026, 8, 2));
        line2.setAmount(new BigDecimal("20"));
        line2.setInvoiceFileUrl("https://files.example/inv2.jpg");
        FinanceExpenseReimbursementCreateReqVO req = new FinanceExpenseReimbursementCreateReqVO();
        req.setPeriodLabel("2026-08");
        req.setProxyTicket(proxy);
        req.setPayeeAccountName("张三");
        req.setPayeeAccountNo("622200001111");
        req.setLines(List.of(line, line2));
        return req;
    }
}
