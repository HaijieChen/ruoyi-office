package cn.iocoder.yudao.module.finance.service.expense;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FinanceStayCityCapsTest {

    @Test
    void oddEvenRooms() {
        assertEquals(0, FinanceStayCityCaps.roomsForSameGender(0));
        assertEquals(1, FinanceStayCityCaps.roomsForSameGender(1));
        assertEquals(1, FinanceStayCityCaps.roomsForSameGender(2));
        assertEquals(2, FinanceStayCityCaps.roomsForSameGender(3));
        assertEquals(2, FinanceStayCityCaps.roomsForSameGender(4));
    }

    @Test
    void mixedGenderRooms() {
        assertEquals(2, FinanceStayCityCaps.rooms(List.of(1, 1, 2)));
        assertEquals(1, FinanceStayCityCaps.rooms(List.of(1)));
        assertEquals(2, FinanceStayCityCaps.rooms(List.of(1, null)));
    }

    @Test
    void nightsAndCap() {
        assertEquals(1, FinanceStayCityCaps.nights(LocalDate.of(2026, 8, 13), LocalDate.of(2026, 8, 13)));
        assertEquals(2, FinanceStayCityCaps.nights(LocalDate.of(2026, 8, 13), LocalDate.of(2026, 8, 15)));
        assertEquals(new BigDecimal("1600"), FinanceStayCityCaps.stayCap("T1", 2, 2));
        assertEquals(new BigDecimal("300"), FinanceStayCityCaps.stayCap("OTHER", 1, 1));
    }
}
