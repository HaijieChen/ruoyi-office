package cn.iocoder.yudao.module.finance.service.fx;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.finance.controller.admin.fx.vo.FinanceExchangeRatePageReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.fx.vo.FinanceExchangeRateSaveReqVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.fx.FinanceExchangeRateDO;

public interface FinanceExchangeRateService {
    Long save(FinanceExchangeRateSaveReqVO reqVO);
    PageResult<FinanceExchangeRateDO> getPage(FinanceExchangeRatePageReqVO reqVO);

    java.math.BigDecimal toCny(java.math.BigDecimal amount, String currency, java.time.LocalDate date);
}
