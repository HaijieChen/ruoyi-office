package cn.iocoder.yudao.module.finance.framework.bpm;

import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import cn.iocoder.yudao.module.bpm.enums.task.BpmTaskStatusEnum;
import cn.iocoder.yudao.module.finance.dal.dataobject.expense.FinanceExpenseReimbursementDO;

/**
 * 费用报销流程状态 → 台账终态。只映射释放占用的 REJECTED / CANCELLED。
 * APPROVE / RUNNING 不改占用（通过仍走 approve / recordPay）。
 */
public final class FinanceExpenseProcessStatusMapper {

    private FinanceExpenseProcessStatusMapper() {
    }

    public static String toOutcome(Integer processStatus) {
        if (processStatus == null
                || BpmProcessInstanceStatusEnum.RUNNING.getStatus().equals(processStatus)
                || BpmProcessInstanceStatusEnum.APPROVE.getStatus().equals(processStatus)
                || BpmTaskStatusEnum.APPROVE.getStatus().equals(processStatus)) {
            return null;
        }
        if (BpmProcessInstanceStatusEnum.REJECT.getStatus().equals(processStatus)
                || BpmTaskStatusEnum.REJECT.getStatus().equals(processStatus)) {
            return FinanceExpenseReimbursementDO.STATUS_REJECTED;
        }
        if (BpmProcessInstanceStatusEnum.CANCEL.getStatus().equals(processStatus)
                || BpmTaskStatusEnum.CANCEL.getStatus().equals(processStatus)
                || BpmTaskStatusEnum.WITHDRAW.getStatus().equals(processStatus)) {
            return FinanceExpenseReimbursementDO.STATUS_CANCELLED;
        }
        return null;
    }
}
