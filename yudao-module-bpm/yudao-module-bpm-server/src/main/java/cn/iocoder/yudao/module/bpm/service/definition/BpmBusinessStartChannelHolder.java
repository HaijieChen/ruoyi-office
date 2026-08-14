package cn.iocoder.yudao.module.bpm.service.definition;

import java.util.concurrent.Callable;

/**
 * EXP-87 G1：可信业务启动通道（服务端 ThreadLocal，禁止由请求 DTO 自报）。
 * <p>
 * 仅 BPM 服务端在「业务领域 create 专用 API」入口设置；通用 HTTP/RPC create 永不置位。
 * 调用方无法通过线路字段伪造信任。
 */
public final class BpmBusinessStartChannelHolder {

    private static final ThreadLocal<Boolean> TRUSTED = new ThreadLocal<>();

    private BpmBusinessStartChannelHolder() {
    }

    /** 当前线程是否处于可信业务通道 */
    public static boolean isTrustedBusinessStart() {
        return Boolean.TRUE.equals(TRUSTED.get());
    }

    /**
     * 在可信业务通道内执行（服务端派生，finally 清理）。
     */
    public static <V> V callTrusted(Callable<V> callable) {
        Boolean prev = TRUSTED.get();
        TRUSTED.set(Boolean.TRUE);
        try {
            return callable.call();
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        } finally {
            if (prev == null) {
                TRUSTED.remove();
            } else {
                TRUSTED.set(prev);
            }
        }
    }
}
