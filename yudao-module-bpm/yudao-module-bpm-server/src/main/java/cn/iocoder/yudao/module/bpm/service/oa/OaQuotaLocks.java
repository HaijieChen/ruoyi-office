package cn.iocoder.yudao.module.bpm.service.oa;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.dal.mysql.oa.BpmOAQuotaLockMapper;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.TreeSet;

/**
 * 申请人+周期额度行锁。必须先有 TenantContext。
 */
final class OaQuotaLocks {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    private OaQuotaLocks() {
    }

    static void lockOvertimeDay(BpmOAQuotaLockMapper mapper, Long userId, LocalDate day) {
        mapper.upsertLock(TenantContextHolder.getRequiredTenantId(), userId,
                BpmOAQuotaLockMapper.OVERTIME_DAY, day.toString());
    }

    static void lockOvertimeDays(BpmOAQuotaLockMapper mapper, Long userId, Iterable<LocalDate> days) {
        TreeSet<LocalDate> ordered = new TreeSet<>();
        for (LocalDate day : days) {
            ordered.add(day);
        }
        for (LocalDate day : ordered) {
            lockOvertimeDay(mapper, userId, day);
        }
    }

    static void lockPunchMonth(BpmOAQuotaLockMapper mapper, Long userId, LocalDate punchDate) {
        mapper.upsertLock(TenantContextHolder.getRequiredTenantId(), userId,
                BpmOAQuotaLockMapper.PUNCH_MONTH, punchDate.format(MONTH));
    }
}
