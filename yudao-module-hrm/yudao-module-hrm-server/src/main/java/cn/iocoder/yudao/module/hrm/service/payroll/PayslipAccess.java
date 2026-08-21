package cn.iocoder.yudao.module.hrm.service.payroll;

public class PayslipAccess {

    public static boolean canReadOwn(Long snapshotUserId, Long loginUserId) {
        return snapshotUserId != null && snapshotUserId.equals(loginUserId);
    }

    public static boolean payslipIgnoresBatchPermission(boolean hasBatchPermission) {
        return true;
    }
}
