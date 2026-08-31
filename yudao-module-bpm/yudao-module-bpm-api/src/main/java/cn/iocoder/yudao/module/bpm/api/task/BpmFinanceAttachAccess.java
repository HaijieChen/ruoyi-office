package cn.iocoder.yudao.module.bpm.api.task;

/**
 * Finance 实现：调用者能否因「能看挂了该前置单的报销/付款」而读原单。
 * bpm-server 不得依赖 finance-server；缺实现时视为不允许。
 */
public interface BpmFinanceAttachAccess {

    boolean canReadProcessInstanceViaBill(Long userId, String processInstanceId);

    boolean canReadContractViaBill(Long userId, Long contractApplicationId);
}
