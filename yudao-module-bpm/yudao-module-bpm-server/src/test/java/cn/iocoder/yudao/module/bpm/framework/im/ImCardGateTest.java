package cn.iocoder.yudao.module.bpm.framework.im;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ImCardGateTest {

    @Test
    void leaveReadOnlyViewCanApprove() {
        ImCardTaskSnapshot snap = legal();
        assertTrue(ImCardGate.canApprove(snap, 8L));
        assertTrue(ImCardGate.canReject(snap, 8L));
    }

    @Test
    void reasonRequireGoesToApp() {
        ImCardTaskSnapshot snap = legal();
        snap.setReasonRequire(true);
        assertFalse(ImCardGate.canApprove(snap, 8L));
    }

    @Test
    void nodeCustomFormGoesToApp() {
        ImCardTaskSnapshot snap = legal();
        snap.setNodeFormCustomViewPath("/bpm/finance/payment/task");
        assertFalse(ImCardGate.canApprove(snap, 8L));
    }

    @Test
    void rejectAsReturnGoesToApp() {
        ImCardTaskSnapshot snap = legal();
        snap.setRejectHandlerIsReturn(true);
        assertTrue(ImCardGate.canApprove(snap, 8L));
        assertFalse(ImCardGate.canReject(snap, 8L));
    }

    @Test
    void wrongAssigneeCannotApprove() {
        assertFalse(ImCardGate.canApprove(legal(), 99L));
    }

    @Test
    void starterCanWithdrawWhenRunning() {
        ImCardTaskSnapshot snap = legal();
        snap.setStartUserId(3L);
        assertTrue(ImCardGate.canWithdraw(snap, 3L));
        snap.setProcessRunning(false);
        assertFalse(ImCardGate.canWithdraw(snap, 3L));
    }

    private static ImCardTaskSnapshot legal() {
        ImCardTaskSnapshot snap = new ImCardTaskSnapshot();
        snap.setTaskId("t1");
        snap.setProcessInstanceId("p1");
        snap.setAssigneeUserId(8L);
        snap.setStartUserId(3L);
        snap.setSignEnable(false);
        snap.setReasonRequire(false);
        snap.setApproveButtonEnabled(true);
        snap.setProcessRunning(true);
        return snap;
    }
}
