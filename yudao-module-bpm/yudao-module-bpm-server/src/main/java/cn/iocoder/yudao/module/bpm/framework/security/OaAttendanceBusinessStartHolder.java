package cn.iocoder.yudao.module.bpm.framework.security;

import java.util.concurrent.Callable;

/**
 * 加班/补卡业务启流上下文。独立于 {@code BpmBusinessStartChannelHolder}，无线路字段、无 HMAC。
 * 仅两 key 的业务 create 在额度锁+insert 之后置位。
 */
public final class OaAttendanceBusinessStartHolder {

    private static final ThreadLocal<Boolean> FLAG = new ThreadLocal<>();

    private OaAttendanceBusinessStartHolder() {
    }

    public static boolean isSet() {
        return Boolean.TRUE.equals(FLAG.get());
    }

    public static boolean isAttendanceProcessKey(String processDefinitionKey) {
        return "oa_overtime".equals(processDefinitionKey)
                || "oa_punch_correction".equals(processDefinitionKey);
    }

    /** 两 key 且未置位 → 应在删历史前拒绝。 */
    public static boolean mustRejectGenericStart(String processDefinitionKey) {
        return isAttendanceProcessKey(processDefinitionKey) && !isSet();
    }

    public static <V> V callBusiness(Callable<V> callable) {
        Boolean prev = FLAG.get();
        FLAG.set(Boolean.TRUE);
        try {
            return callable.call();
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        } finally {
            if (prev == null) {
                FLAG.remove();
            } else {
                FLAG.set(prev);
            }
        }
    }
}
