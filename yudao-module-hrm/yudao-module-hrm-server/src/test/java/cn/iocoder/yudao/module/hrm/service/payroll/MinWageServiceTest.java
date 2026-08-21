package cn.iocoder.yudao.module.hrm.service.payroll;

import cn.iocoder.yudao.module.hrm.dal.dataobject.payroll.MinWageDO;
import cn.iocoder.yudao.module.hrm.dal.mysql.payroll.MinWageMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MinWageServiceTest {

    @InjectMocks
    private MinWageServiceImpl minWageService;

    @Mock
    private MinWageMapper minWageMapper;

    @Test
    void effectiveOn_usesLatestRowNotAfterPayrollMonth() {
        MinWageDO june = row(1L, "2690", 202606);
        when(minWageMapper.selectList(any())).thenReturn(List.of(june));

        BigDecimal amount = minWageService.effectiveOn(202606);

        assertEquals(new BigDecimal("2690"), amount);
    }

    @Test
    void effectiveOn_emptyHistory_returnsNull() {
        when(minWageMapper.selectList(any())).thenReturn(List.of());
        assertNull(minWageService.effectiveOn(202608));
    }

    @Test
    void create_defaultsEffectiveMonthToCurrentYearMonth() {
        minWageService.create(new BigDecimal("2690"), false, 202608);
        ArgumentCaptor<MinWageDO> captor = ArgumentCaptor.forClass(MinWageDO.class);
        verify(minWageMapper).insert(captor.capture());
        assertEquals(0, captor.getValue().getAmount().compareTo(new BigDecimal("2690")));
        assertEquals(202608, captor.getValue().getEffectiveMonth());
    }

    @Test
    void create_nextMonth_storesFollowingMonth() {
        minWageService.create(new BigDecimal("2800"), true, 202608);
        ArgumentCaptor<MinWageDO> captor = ArgumentCaptor.forClass(MinWageDO.class);
        verify(minWageMapper).insert(captor.capture());
        assertEquals(202609, captor.getValue().getEffectiveMonth());
    }

    @Test
    void history_returnsMapperRowsNewestFirst() {
        MinWageDO newer = row(2L, "2800", 202609);
        MinWageDO older = row(1L, "2690", 202608);
        when(minWageMapper.selectList(any())).thenReturn(List.of(newer, older));
        List<MinWageDO> rows = minWageService.history();
        assertEquals(202609, rows.get(0).getEffectiveMonth());
        assertEquals(202608, rows.get(1).getEffectiveMonth());
    }

    private static MinWageDO row(Long id, String amount, int month) {
        MinWageDO row = new MinWageDO();
        row.setId(id);
        row.setAmount(new BigDecimal(amount));
        row.setEffectiveMonth(month);
        return row;
    }
}
