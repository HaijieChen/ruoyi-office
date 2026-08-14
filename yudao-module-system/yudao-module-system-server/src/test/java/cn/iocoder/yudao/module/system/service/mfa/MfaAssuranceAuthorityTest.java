package cn.iocoder.yudao.module.system.service.mfa;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaEnrollmentState;
import cn.iocoder.yudao.module.system.service.mfa.model.MfaUserAssuranceView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.MFA_POLICY_UNAVAILABLE;
import static org.junit.jupiter.api.Assertions.*;

/**
 * ADR-MFA-v3 §5 assurance 权威（切片 1）。
 */
public class MfaAssuranceAuthorityTest extends BaseMockitoUnitTest {

    private InMemoryMfaAuthorityStore store;
    private MfaAssuranceAuthorityImpl authority;

    @BeforeEach
    void setUp() {
        store = new InMemoryMfaAuthorityStore();
        authority = new MfaAssuranceAuthorityImpl(store);
    }

    @Test
    void missingRow_returnsNull_andRequireFails() {
        assertNull(authority.getAssurance(1L, 100L));
        ServiceException ex = assertThrows(ServiceException.class,
                () -> authority.requireAssuranceForAdmin(1L, 100L));
        assertEquals(MFA_POLICY_UNAVAILABLE.getCode(), ex.getCode());
    }

    @Test
    void bootstrap_epochZero() {
        MfaUserAssuranceView v = authority.ensureBootstrapRow(1L, 200L);
        assertEquals(0L, v.getAssuranceEpoch());
        assertEquals(MfaEnrollmentState.NONE, v.getEnrollmentState());
        assertFalse(v.isEnabled());
        // idempotent
        MfaUserAssuranceView v2 = authority.ensureBootstrapRow(1L, 200L);
        assertEquals(0L, v2.getAssuranceEpoch());
    }

    @Test
    void bumpEpoch_monotonic() {
        authority.ensureBootstrapRow(1L, 300L);
        long e1 = authority.bumpAssuranceEpoch(1L, 300L, MfaEnrollmentState.PENDING, true);
        long e2 = authority.bumpAssuranceEpoch(1L, 300L, MfaEnrollmentState.COMPLETED, true);
        assertEquals(1L, e1);
        assertEquals(2L, e2);
        MfaUserAssuranceView v = authority.requireAssuranceForAdmin(1L, 300L);
        assertEquals(2L, v.getAssuranceEpoch());
        assertEquals(MfaEnrollmentState.COMPLETED, v.getEnrollmentState());
        assertTrue(v.isEnabled());
    }
}
