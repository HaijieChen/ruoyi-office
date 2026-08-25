package cn.iocoder.yudao.module.system.service.mfa;

import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.OAuth2AccessTokenDO;

/**
 * 每次 ADMIN access Token 使用时的强一致门闩（ADR-MFA-v3 §6.5，切片 2）。
 */
public interface MfaSessionGuard {

    /**
     * 校验 access token 是否可作为业务 Bearer。
     * <ul>
     *   <li>非 ADMIN 用户态：直接通过</li>
     *   <li>ADMIN：主库 control tuple + assurance epoch 门闩；fail-closed</li>
     * </ul>
     *
     * @throws cn.iocoder.yudao.framework.common.exception.ServiceException 拒绝时
     */
    void assertAccessAllowed(OAuth2AccessTokenDO accessToken);
}
