package cn.iocoder.yudao.module.finance.service.opening;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.finance.controller.admin.opening.vo.FinanceBankOpeningBalancePageReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.opening.vo.FinanceBankOpeningBalanceSaveReqVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.opening.FinanceBankOpeningBalanceDO;

public interface FinanceBankOpeningBalanceService {

    /**
     * 按账户 upsert 一条期初：不存在则插入，已存在则更新。
     */
    Long upsert(FinanceBankOpeningBalanceSaveReqVO reqVO);

    /**
     * 只读查询。账户尚无期初时返回 null，不得插入。
     */
    FinanceBankOpeningBalanceDO getByAccountId(Long accountId);

    PageResult<FinanceBankOpeningBalanceDO> getPage(FinanceBankOpeningBalancePageReqVO reqVO);

}
