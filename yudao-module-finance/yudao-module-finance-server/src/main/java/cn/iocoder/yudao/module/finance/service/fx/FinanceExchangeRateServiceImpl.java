package cn.iocoder.yudao.module.finance.service.fx;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.finance.controller.admin.fx.vo.FinanceExchangeRatePageReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.fx.vo.FinanceExchangeRateSaveReqVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.fx.FinanceExchangeRateDO;
import cn.iocoder.yudao.module.finance.dal.mysql.fx.FinanceExchangeRateMapper;
import cn.iocoder.yudao.module.finance.service.common.FinanceCurrencySupport;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.EXCHANGE_RATE_MISSING;

@Service
@Validated
public class FinanceExchangeRateServiceImpl implements FinanceExchangeRateService {

    private static final int MONEY_SCALE = 2;

    private final FinanceExchangeRateMapper mapper;

    public FinanceExchangeRateServiceImpl(FinanceExchangeRateMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Long save(FinanceExchangeRateSaveReqVO reqVO) {
        String period = reqVO.getPeriodLabel().trim();
        String from = reqVO.getFromCurrency().trim().toUpperCase();
        String to = reqVO.getToCurrency().trim().toUpperCase();
        FinanceExchangeRateDO existing = mapper.selectPair(period, from, to);
        FinanceExchangeRateDO row = FinanceExchangeRateDO.builder()
                .id(reqVO.getId() != null ? reqVO.getId() : (existing == null ? null : existing.getId()))
                .periodLabel(period)
                .fromCurrency(from)
                .toCurrency(to)
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

    @Override
    public BigDecimal toCny(BigDecimal amount, String currency, LocalDate date) {
        if (amount == null) {
            return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }
        String from = StrUtil.isBlank(currency) ? "CNY" : FinanceCurrencySupport.requireSupported(currency);
        if ("CNY".equals(from)) {
            return amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }
        String period = YearMonth.from(date == null ? LocalDate.now() : date).toString();
        FinanceExchangeRateDO direct = mapper.selectPair(period, from, "CNY");
        if (direct != null && direct.getRate() != null) {
            return amount.multiply(direct.getRate()).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }
        FinanceExchangeRateDO inverse = mapper.selectPair(period, "CNY", from);
        if (inverse != null && inverse.getRate() != null && inverse.getRate().signum() != 0) {
            return amount.divide(inverse.getRate(), MONEY_SCALE, RoundingMode.HALF_UP);
        }
        throw exception(EXCHANGE_RATE_MISSING);
    }
}
