package cn.iocoder.yudao.module.bpm.framework.im;

/**
 * R11：卡片能否直接通过/拒绝。节点级自定义办理页、签名、强制意见、隐藏通过、拒绝即退回 → 去应用。
 */
public final class ImCardGate {

    private ImCardGate() {
    }

    public static boolean canApprove(ImCardTaskSnapshot snapshot, Long actorUserId) {
        if (snapshot == null || actorUserId == null) {
            return false;
        }
        if (!actorUserId.equals(snapshot.getAssigneeUserId())) {
            return false;
        }
        if (Boolean.FALSE.equals(snapshot.getApproveButtonEnabled())) {
            return false;
        }
        if (Boolean.TRUE.equals(snapshot.getSignEnable())) {
            return false;
        }
        if (Boolean.TRUE.equals(snapshot.getReasonRequire())) {
            return false;
        }
        if (snapshot.getNodeFormCustomViewPath() != null && !snapshot.getNodeFormCustomViewPath().isBlank()) {
            return false;
        }
        return true;
    }

    public static boolean canReject(ImCardTaskSnapshot snapshot, Long actorUserId) {
        if (!canApprove(snapshot, actorUserId)) {
            return false;
        }
        return !Boolean.TRUE.equals(snapshot.getRejectHandlerIsReturn());
    }

    public static boolean canWithdraw(ImCardTaskSnapshot snapshot, Long actorUserId) {
        if (snapshot == null || actorUserId == null) {
            return false;
        }
        return actorUserId.equals(snapshot.getStartUserId())
                && Boolean.TRUE.equals(snapshot.getProcessRunning());
    }
}
