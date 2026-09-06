package cn.iocoder.yudao.module.bpm.service.oa;

import cn.iocoder.yudao.module.bpm.framework.security.OaAttendanceBusinessStartHolder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OaAttendanceBusinessStartGuardTest {

    @Test
    void onlyTwoKeysRequireHolder() {
        assertTrue(OaAttendanceBusinessStartHolder.isAttendanceProcessKey("oa_overtime"));
        assertTrue(OaAttendanceBusinessStartHolder.isAttendanceProcessKey("oa_punch_correction"));
        assertFalse(OaAttendanceBusinessStartHolder.isAttendanceProcessKey("oa_outing"));
        assertFalse(OaAttendanceBusinessStartHolder.isAttendanceProcessKey("finance_salary_payment_apply"));
        assertFalse(OaAttendanceBusinessStartHolder.isAttendanceProcessKey("finance_payment_apply"));
    }

    @Test
    void genericStartRejectedWithoutHolderForAttendanceKeysOnly() {
        assertTrue(OaAttendanceBusinessStartHolder.mustRejectGenericStart("oa_overtime"));
        assertTrue(OaAttendanceBusinessStartHolder.mustRejectGenericStart("oa_punch_correction"));
        assertFalse(OaAttendanceBusinessStartHolder.mustRejectGenericStart("oa_outing"));
        assertFalse(OaAttendanceBusinessStartHolder.mustRejectGenericStart("finance_salary_payment_apply"));
    }

    @Test
    void callBusinessAllowsAttendanceKeysAndCleansUp() throws Exception {
        assertTrue(OaAttendanceBusinessStartHolder.mustRejectGenericStart("oa_overtime"));
        Boolean inside = OaAttendanceBusinessStartHolder.callBusiness(() -> {
            assertTrue(OaAttendanceBusinessStartHolder.isSet());
            assertFalse(OaAttendanceBusinessStartHolder.mustRejectGenericStart("oa_overtime"));
            return true;
        });
        assertTrue(inside);
        assertFalse(OaAttendanceBusinessStartHolder.isSet());
        assertTrue(OaAttendanceBusinessStartHolder.mustRejectGenericStart("oa_overtime"));
    }

    @Test
    void financeTrustedHolderDoesNotSatisfyAttendanceGuard() {
        cn.iocoder.yudao.module.bpm.service.definition.BpmBusinessStartChannelHolder.callTrusted(() -> {
            assertTrue(cn.iocoder.yudao.module.bpm.service.definition.BpmBusinessStartChannelHolder
                    .isTrustedBusinessStart());
            assertTrue(OaAttendanceBusinessStartHolder.mustRejectGenericStart("oa_overtime"));
            return null;
        });
        assertFalse(cn.iocoder.yudao.module.bpm.service.definition.BpmBusinessStartChannelHolder
                .isTrustedBusinessStart());
    }
}
