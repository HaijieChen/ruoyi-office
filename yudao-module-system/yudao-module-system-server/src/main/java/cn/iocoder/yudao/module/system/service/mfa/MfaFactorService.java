package cn.iocoder.yudao.module.system.service.mfa;

/**
 * 因子通道最小闭环（切片 2）：挑战发送与校验边界。
 * <p>
 * 不负责策略决策；不默认启用 OPTIONAL/REQUIRED。
 * 支持类型：TOTP / SMS / EMAIL（BACKUP_CODE 恢复路径后续切片）。
 */
public interface MfaFactorService {

    /**
     * 向 SMS/EMAIL 因子发送一次性验证码（绑定 active flow）。
     * TOTP 无需发送，调用抛 {@link IllegalArgumentException}。
     */
    void sendChallengeCode(String rawFlowToken, String factorId, String factorType);

    /**
     * 校验挑战码。成功不消费 flow（由 Facade T_issue 路径 CAS 消费）。
     */
    boolean verifyChallengeCode(String rawFlowToken, String factorId, String factorType, String code);

    /**
     * 注册/绑定内存中的测试用因子（生产由 enrollment 事务写入 DB）。
     */
    void registerActiveFactor(Long tenantId, Long userId, String factorId, String factorType, String secretOrDestination);

    void clear();
}
