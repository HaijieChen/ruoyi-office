package cn.iocoder.yudao.module.finance.service.common;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.bpm.api.task.BpmFinanceAttachAccess;
import cn.iocoder.yudao.module.finance.dal.dataobject.expense.FinanceExpenseReimbursementDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.expense.FinanceExpenseReimbursementLineDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.payment.FinancePaymentApplicationDO;
import cn.iocoder.yudao.module.finance.dal.mysql.expense.FinanceExpenseReimbursementLineMapper;
import cn.iocoder.yudao.module.finance.dal.mysql.expense.FinanceExpenseReimbursementMapper;
import cn.iocoder.yudao.module.finance.dal.mysql.payment.FinancePaymentApplicationMapper;
import cn.iocoder.yudao.module.finance.service.expense.FinanceExpensePredocService;
import cn.iocoder.yudao.module.finance.service.expense.FinanceExpenseReimbursementService;
import cn.iocoder.yudao.module.finance.service.payment.FinancePaymentApplicationService;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
public class FinanceBpmAttachAccessImpl implements BpmFinanceAttachAccess {

    private final FinanceExpenseReimbursementLineMapper expenseLineMapper;
    private final FinanceExpenseReimbursementMapper expenseMapper;
    private final FinanceExpensePredocService expensePredocService;
    private final FinancePaymentApplicationMapper paymentMapper;
    private final FinancePaymentApplicationService paymentApplicationService;
    private final SecurityFrameworkService securityFrameworkService;

    public FinanceBpmAttachAccessImpl(FinanceExpenseReimbursementLineMapper expenseLineMapper,
                                      FinanceExpenseReimbursementMapper expenseMapper,
                                      FinanceExpensePredocService expensePredocService,
                                      FinancePaymentApplicationMapper paymentMapper,
                                      FinancePaymentApplicationService paymentApplicationService,
                                      SecurityFrameworkService securityFrameworkService) {
        this.expenseLineMapper = expenseLineMapper;
        this.expenseMapper = expenseMapper;
        this.expensePredocService = expensePredocService;
        this.paymentMapper = paymentMapper;
        this.paymentApplicationService = paymentApplicationService;
        this.securityFrameworkService = securityFrameworkService;
    }

    @Override
    public boolean canReadProcessInstanceViaBill(Long userId, String processInstanceId) {
        if (userId == null || StrUtil.isBlank(processInstanceId)) {
            return false;
        }
        String pid = processInstanceId.trim();
        List<FinanceExpenseReimbursementLineDO> lines = expenseLineMapper.selectList(
                new LambdaQueryWrapperX<FinanceExpenseReimbursementLineDO>()
                        .eq(FinanceExpenseReimbursementLineDO::getPredocProcessInstanceId, pid));
        for (FinanceExpenseReimbursementLineDO line : lines) {
            FinanceExpenseReimbursementDO header = expenseMapper.selectById(line.getReimbursementId());
            if (canReadExpense(header, userId)) {
                return true;
            }
        }
        List<FinancePaymentApplicationDO> payments = paymentMapper.selectList(
                new LambdaQueryWrapperX<FinancePaymentApplicationDO>()
                        .eq(FinancePaymentApplicationDO::getPurchaseProcessInstanceId, pid));
        for (FinancePaymentApplicationDO payment : payments) {
            if (canReadPayment(payment, userId)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean canReadContractViaBill(Long userId, Long contractApplicationId) {
        if (userId == null || contractApplicationId == null) {
            return false;
        }
        List<FinancePaymentApplicationDO> payments = paymentMapper.selectList(
                new LambdaQueryWrapperX<FinancePaymentApplicationDO>()
                        .and(w -> w.eq(FinancePaymentApplicationDO::getLeaseContractApplicationId, contractApplicationId)
                                .or()
                                .eq(FinancePaymentApplicationDO::getRelatedContractApplicationId, contractApplicationId)));
        for (FinancePaymentApplicationDO payment : payments) {
            if (canReadPayment(payment, userId)) {
                return true;
            }
        }
        return false;
    }

    private boolean canReadExpense(FinanceExpenseReimbursementDO header, Long userId) {
        if (header == null) {
            return false;
        }
        if (securityFrameworkService.hasPermission(FinanceExpenseReimbursementService.QUERY_PERMISSION)
                || Objects.equals(header.getApplicantUserId(), userId)) {
            return true;
        }
        return expensePredocService.isProcessAssignee(header.getProcessInstanceId(), userId);
    }

    private boolean canReadPayment(FinancePaymentApplicationDO payment, Long userId) {
        if (payment == null) {
            return false;
        }
        if (securityFrameworkService.hasPermission("finance:payment-application:update")) {
            return true;
        }
        return paymentApplicationService.canAccessDetail(payment.getId(), userId)
                || expensePredocService.isProcessAssignee(payment.getProcessInstanceId(), userId);
    }
}
