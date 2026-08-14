package cn.iocoder.yudao.module.system.service.mfa;

import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.OAuth2AccessTokenDO;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaIssuancePath;
import cn.iocoder.yudao.module.system.service.mfa.model.MfaIssuanceResult;

import java.util.List;

/**
 * 唯一 ADMIN 用户态 Token 签发出口（ADR-MFA-v2 §1–§2 / ②）。
 */
public interface MfaTokenIssuanceFacade {

    /**
     * 交互式登录/注册入口（login/sms/social/register）。
     */
    MfaIssuanceResult issueAfterPrimaryAuth(MfaIssuancePath path, Long userId, Long tenantId,
                                            Integer userType, String clientId, List<String> scopes,
                                            List<String> amr);

    /**
     * OAuth2 grant 用户态签发（authorization_code / implicit / password）。
     * CHALLENGE 在 password grant 上映射为 REJECT。
     */
    MfaIssuanceResult issueForOAuthGrant(MfaIssuancePath path, Long userId, Long tenantId,
                                         Integer userType, String clientId, List<String> scopes,
                                         List<String> amr);

    /**
     * refresh：重新解析策略版本门闩。
     */
    MfaIssuanceResult refresh(MfaIssuancePath path, String refreshToken, String clientId);

    /**
     * client_credentials 非用户态：N/A，直达底层（userId=0）。
     */
    OAuth2AccessTokenDO issueClientCredentials(String clientId, List<String> scopes);

    /**
     * 内部 createAccessToken：ADMIN 用户态一律 REJECT。
     */
    MfaIssuanceResult rejectInternalAdminCreate(Long userId, Integer userType);

}
