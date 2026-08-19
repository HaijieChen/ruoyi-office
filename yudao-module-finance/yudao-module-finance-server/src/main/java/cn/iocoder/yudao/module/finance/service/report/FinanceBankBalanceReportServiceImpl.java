package cn.iocoder.yudao.module.finance.service.report;

import cn.iocoder.yudao.module.finance.controller.admin.report.vo.FinanceBankBalanceReportReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.report.vo.FinanceBankBalanceReportRespVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.companyaccount.FinanceCompanyBankAccountDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.opening.FinanceBankOpeningBalanceDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.payment.FinancePaymentPayLineDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.receipt.FinanceReceiptDO;
import cn.iocoder.yudao.module.finance.dal.mysql.companyaccount.FinanceCompanyBankAccountMapper;
import cn.iocoder.yudao.module.finance.dal.mysql.opening.FinanceBankOpeningBalanceMapper;
import cn.iocoder.yudao.module.finance.dal.mysql.payment.FinancePaymentPayLineMapper;
import cn.iocoder.yudao.module.finance.dal.mysql.receipt.FinanceBankReceiptMapper;
import cn.iocoder.yudao.module.finance.service.companyaccount.FinanceCompanyBankAccountService;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Validated
public class FinanceBankBalanceReportServiceImpl implements FinanceBankBalanceReportService {

    private final FinanceCompanyBankAccountMapper accountMapper;
    private final FinanceBankOpeningBalanceMapper openingMapper;
    private final FinanceBankReceiptMapper receiptMapper;
    private final FinancePaymentPayLineMapper payLineMapper;

    public FinanceBankBalanceReportServiceImpl(FinanceCompanyBankAccountMapper accountMapper,
                                               FinanceBankOpeningBalanceMapper openingMapper,
                                               FinanceBankReceiptMapper receiptMapper,
                                               FinancePaymentPayLineMapper payLineMapper) {
        this.accountMapper = accountMapper;
        this.openingMapper = openingMapper;
        this.receiptMapper = receiptMapper;
        this.payLineMapper = payLineMapper;
    }

    @Override
    public FinanceBankBalanceReportRespVO query(FinanceBankBalanceReportReqVO reqVO) {
        LocalDate asOf = reqVO == null || reqVO.getAsOf() == null ? LocalDate.now() : reqVO.getAsOf();
        return aggregate(accountMapper.selectList(), openingMapper.selectList(),
                receiptMapper.selectList(), payLineMapper.selectList(), asOf);
    }

    FinanceBankBalanceReportRespVO aggregate(List<FinanceCompanyBankAccountDO> accounts,
                                             List<FinanceBankOpeningBalanceDO> openings,
                                             List<FinanceReceiptDO> receipts,
                                             List<FinancePaymentPayLineDO> payLines,
                                             LocalDate asOf) {
        Map<Long, FinanceBankOpeningBalanceDO> openingByAccount = new HashMap<>();
        for (FinanceBankOpeningBalanceDO opening : openings) {
            openingByAccount.put(opening.getAccountId(), opening);
        }
        List<FinanceBankBalanceReportRespVO.Row> rows = new ArrayList<>();
        long excludedFx = 0L;
        for (FinanceCompanyBankAccountDO account : accounts) {
            FinanceBankOpeningBalanceDO opening = openingByAccount.get(account.getId());
            LocalDate openDate = opening == null ? null : opening.getAsOfDate();
            BigDecimal openingAmt = opening == null || opening.getAmount() == null
                    ? BigDecimal.ZERO : opening.getAmount();
            BigDecimal income = BigDecimal.ZERO;
            BigDecimal pay = BigDecimal.ZERO;
            for (FinanceReceiptDO receipt : receipts) {
                if (!FinanceBankBalanceMatcher.matchesAccount(receipt, account)) {
                    continue;
                }
                if (!FinanceArDetailCalculator.isCny(receipt.getCurrency())) {
                    excludedFx++;
                    continue;
                }
                LocalDate day = FinanceBankBalanceMatcher.toLocalDate(receipt.getTransactionDate());
                if (!FinanceBankBalanceMatcher.inWindow(day, openDate, asOf)) {
                    continue;
                }
                income = income.add(nz(receipt.getTransactionAmount()));
            }
            for (FinancePaymentPayLineDO line : payLines) {
                if (!account.getId().equals(line.getCompanyBankAccountId())) {
                    continue;
                }
                if (!FinanceArDetailCalculator.isCny(line.getCurrencySnapshot())) {
                    excludedFx++;
                    continue;
                }
                if (!FinanceBankBalanceMatcher.inWindow(line.getActualPayDate(), openDate, asOf)) {
                    continue;
                }
                pay = pay.add(nz(line.getPayAmount()));
            }
            FinanceBankBalanceReportRespVO.Row row = new FinanceBankBalanceReportRespVO.Row();
            row.setAccountId(account.getId());
            row.setAccountName(account.getAccountName());
            row.setAccountNoMasked(FinanceCompanyBankAccountService.maskAccountNo(account.getAccountNo()));
            row.setEntityCompanyDeptId(account.getEntityCompanyDeptId());
            row.setOpeningAmount(openingAmt);
            row.setOpeningAsOfDate(openDate);
            row.setIncomeAmount(income);
            row.setPayExpenseAmount(pay);
            row.setReimbursementExpenseAmount(BigDecimal.ZERO);
            row.setBalanceAmount(openingAmt.add(income).subtract(pay));
            row.setAsOf(asOf);
            rows.add(row);
        }
        long unmatched = 0L;
        for (FinanceReceiptDO receipt : receipts) {
            boolean hit = false;
            for (FinanceCompanyBankAccountDO account : accounts) {
                if (FinanceBankBalanceMatcher.matchesAccount(receipt, account)) {
                    hit = true;
                    break;
                }
            }
            if (!hit) {
                unmatched++;
            }
        }
        FinanceBankBalanceReportRespVO resp = new FinanceBankBalanceReportRespVO();
        resp.setList(rows);
        resp.setExcludedFxCount(excludedFx);
        resp.setUnmatchedReceiptCount(unmatched);
        return resp;
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
