package cn.iocoder.yudao.module.bpm.util;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BpmProcessVariableUtilsTest {

    @Test
    void getBillCodePrefersBillCode() {
        assertEquals("PAY-1", BpmProcessVariableUtils.getBillCode(
                Map.of("billCode", "PAY-1", "applicationNo", "PAY-OLD")));
    }

    @Test
    void getBillCodeFallsBackToApplicationNo() {
        assertEquals("PAY-2", BpmProcessVariableUtils.getBillCode(Map.of("applicationNo", "PAY-2")));
    }

    @Test
    void getBillCodeBlankFallsBack() {
        assertEquals("PAY-3", BpmProcessVariableUtils.getBillCode(
                Map.of("billCode", "  ", "applicationNo", "PAY-3")));
        assertNull(BpmProcessVariableUtils.getBillCode(Map.of()));
        assertNull(BpmProcessVariableUtils.getBillCode(null));
    }
}
