package cn.iocoder.yudao.module.finance.framework.rpc.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.*;

class FinanceBpmApiModeTest {

    @Test
    void explicitLocalAndFeign() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty(FinanceBpmApiMode.PROPERTY, "local");
        assertTrue(FinanceBpmApiMode.useLocal(env, getClass().getClassLoader()));
        assertFalse(FinanceBpmApiMode.useFeign(env, getClass().getClassLoader()));

        env.setProperty(FinanceBpmApiMode.PROPERTY, "feign");
        assertFalse(FinanceBpmApiMode.useLocal(env, getClass().getClassLoader()));
        assertTrue(FinanceBpmApiMode.useFeign(env, getClass().getClassLoader()));
    }

    @Test
    void autoUsesClasspath() {
        MockEnvironment env = new MockEnvironment();
        // finance-server test CP has OpenFeign
        assertEquals(FinanceBpmApiMode.FEIGN,
                FinanceBpmApiMode.resolve(env, getClass().getClassLoader()));
    }
}
