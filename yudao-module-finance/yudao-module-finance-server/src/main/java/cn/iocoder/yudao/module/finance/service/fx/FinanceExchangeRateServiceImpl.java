package cn.iocoder.yudao.module.finance.service.fx;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.finance.controller.admin.fx.vo.FinanceExchangeRatePageReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.fx.vo.FinanceExchangeRateSaveReqVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.fx.FinanceExchangeRateDO;
import cn.iocoder.yudao.module.finance.dal.mysql.fx.FinanceExchangeRateMapper;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
public class FinanceExchangeRateServiceImpl implements FinanceExchangeRateService {
    private final FinanceExchangeRateMapper mapper;
    public FinanceExchangeRateServiceImpl(FinanceExchangeRateMapper mapper) { this.mapper = mapper; }

    @Override
    public Long save(FinanceExchangeRateSaveReqVO reqVO) {
        FinanceExchangeRateDO row = FinanceExchangeRateDO.builder()
                .id(reqVO.getId())
                .periodLabel(reqVO.getPeriodLabel().trim())
                .fromCurrency(reqVO.getFromCurrency().trim().toUpperCase())
                .toCurrency(reqVO.getToCurrency().trim().toUpperCase())
                .rate(reqVO.getRate())
                .build();
        if (row.getId() == null) {
            mapper.insert(row);
        } else {
            mapper.updateById(row);
        }
        return row.getId();
    }

    @Override
    public PageResult<FinanceExchangeRateDO> getPage(FinanceExchangeRatePageReqVO reqVO) {
        return mapper.selectPage(reqVO);
    }
}
