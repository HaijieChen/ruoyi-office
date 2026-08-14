package cn.iocoder.yudao.module.system.service.mfa.delivery;

/**
 * SMS/EMAIL 挑战码投递边界（切片 4）。默认桩实现，不接生产账号。
 */
public interface MfaChallengeDelivery {

    /**
     * @param factorType SMS / EMAIL
     * @param destination 掩码前的投递目标（桩可只记录）
     * @param code 明文挑战码
     */
    void deliver(String factorType, String destination, String code);
}
