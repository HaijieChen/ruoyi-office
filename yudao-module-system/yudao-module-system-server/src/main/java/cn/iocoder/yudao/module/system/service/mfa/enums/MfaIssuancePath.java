package cn.iocoder.yudao.module.system.service.mfa.enums;

/**
 * Token 签发入口路径标识（F-01 全路径表）。
 */
public enum MfaIssuancePath {

    LOGIN_PASSWORD,
    LOGIN_SMS,
    LOGIN_SOCIAL,
    REGISTER,
    REFRESH_TOKEN,
    OAUTH2_AUTHORIZATION_CODE,
    OAUTH2_IMPLICIT,
    OAUTH2_PASSWORD,
    OAUTH2_REFRESH_TOKEN,
    OAUTH2_CLIENT_CREDENTIALS,
    INTERNAL_CREATE,
    INTERNAL_REFRESH

}
