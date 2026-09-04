package cn.iocoder.yudao.module.bpm.framework.im;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ImCardSignatureTest {

    @Test
    void acceptsFreshHmac() {
        long ts = 1_700_000_000_000L;
        String sig = ImCardSignature.sign("s", ts, "e", 30, "wo", "APPROVE");
        assertTrue(ImCardSignature.verify("s", ts, "e", 30, "wo", "APPROVE", sig, ts));
    }

    @Test
    void rejectsEmptySecretAndStale() {
        long ts = 1_700_000_000_000L;
        String sig = ImCardSignature.sign("s", ts, "e", 30, "wo", "APPROVE");
        assertFalse(ImCardSignature.verify("", ts, "e", 30, "wo", "APPROVE", sig, ts));
        assertFalse(ImCardSignature.verify("s", ts, "e", 30, "wo", "APPROVE", sig, ts + ImCardSignature.MAX_SKEW_MS + 1));
    }
}
