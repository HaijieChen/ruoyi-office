package cn.iocoder.yudao.module.system.service.mfa.model;

import cn.iocoder.yudao.module.system.controller.admin.auth.vo.AuthLoginRespVO;
import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.OAuth2AccessTokenDO;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaIssuanceOutcome;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaLoginStatus;
import lombok.Builder;
import lombok.Value;

/**
 * Facade 签发结果。
 * <p>
 * CHALLENGE/REJECT 时 {@link #accessToken} 与 loginResp 中的 access/refresh 必须为 null。
 */
@Value
@Builder
public class MfaIssuanceResult {

    MfaIssuanceOutcome outcome;
    MfaLoginStatus loginStatus;
    /** 仅 ALLOWED 时非空 */
    OAuth2AccessTokenDO accessToken;
    /** 交互式入口统一响应 */
    AuthLoginRespVO loginResp;
    String rejectReason;

    public boolean hasAccessOrRefreshToken() {
        if (accessToken != null
                && (accessToken.getAccessToken() != null || accessToken.getRefreshToken() != null)) {
            return true;
        }
        if (loginResp == null) {
            return false;
        }
        return loginResp.getAccessToken() != null || loginResp.getRefreshToken() != null;
    }

}
