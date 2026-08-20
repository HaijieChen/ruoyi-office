package cn.iocoder.yudao.module.finance.service.fx;

import cn.iocoder.yudao.module.finance.dal.dataobject.fx.FinanceExchangeRateDO;
import cn.iocoder.yudao.module.finance.dal.mysql.fx.FinanceExchangeRateMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FinanceExchangeRateServiceImplTest {

    private FinanceExchangeRateMapper mapper;
    private FinanceExchangeRateServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(FinanceExchangeRateMapper.class);
        service = new FinanceExchangeRateServiceImpl(mapper);
    }

    @Test
    void cnyUnchanged() {
        assertEquals(new BigDecimal("10.00"),
                service.toCny(new BigDecimal("10"), "CNY", LocalDate.of(2026, 8, 1)));
    }

    @Test
    void usdUsesMonthlyRate() {
        when(mapper.selectPair("2026-08", "USD", "CNY")).thenReturn(
                FinanceExchangeRateDO.builder().rate(new BigDecimal("7.2")).build());
        assertEquals(new BigDecimal("72.00"),
                service.toCny(new BigDecimal("10"), "USD", LocalDate.of(2026, 8, 15)));
    }
}
