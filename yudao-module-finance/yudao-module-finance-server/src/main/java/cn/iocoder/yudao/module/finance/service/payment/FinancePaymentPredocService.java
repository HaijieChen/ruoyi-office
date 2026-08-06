package cn.iocoder.yudao.module.finance.service.payment;

import cn.iocoder.yudao.module.finance.controller.admin.payment.vo.FinancePurchaseInstanceRespVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.contract.FinanceContractApplicationDO;

import java.util.List;

/**
 * 付款前置引用契约（PAY-P0-3）：采购 BPM 实例 + 租赁合同。
 * <p>不拥有采购主数据；校验方法供 PR1 createAndStart 复用。
 */
public interface FinancePaymentPredocService {

    /**
     * 当前用户可引用的已通过采购实例（key 白名单 · 成功结束 · 本人发起）。
     */
    List<FinancePurchaseInstanceRespVO> listSelectablePurchaseInstances(Long userId);

    /**
     * 校验采购 processInstanceId 可挂付款；失败抛业务错误。
     *
     * @return 摘要文本（可写 purchase_snapshot）
     */
    String validateAndSummarizePurchaseRef(String processInstanceId, Long userId);

    /**
     * 当前用户可引用的已通过租赁合同。
     */
    List<FinanceContractApplicationDO> listSelectableLeaseContracts(Long userId);

    /**
     * 校验租赁合同申请可挂付款；返回台账行。
     */
    FinanceContractApplicationDO validateLeaseContractRef(Long contractApplicationId, Long userId);

}
