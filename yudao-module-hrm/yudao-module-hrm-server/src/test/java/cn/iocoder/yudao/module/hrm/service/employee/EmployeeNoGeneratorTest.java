package cn.iocoder.yudao.module.hrm.service.employee;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EmployeeNoGeneratorTest {

    @Test
    void emptyStartsAt0001() {
        assertEquals("0001", EmployeeNoGenerator.next(null));
        assertEquals("0001", EmployeeNoGenerator.next("  "));
    }

    @Test
    void keepsFourDigitWidth() {
        assertEquals("0072", EmployeeNoGenerator.next("0071"));
    }

    @Test
    void keepsEightDigitWidth() {
        assertEquals("10000084", EmployeeNoGenerator.next("10000083"));
    }

    @Test
    void growsWidthWhenOverflow() {
        assertEquals("10000", EmployeeNoGenerator.next("9999"));
    }

}
