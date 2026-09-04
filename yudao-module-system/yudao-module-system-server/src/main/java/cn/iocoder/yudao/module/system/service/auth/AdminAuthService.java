package cn.iocoder.yudao.module.system.service.auth;

import cn.iocoder.yudao.module.system.controller.admin.auth.vo.*;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import jakarta.validation.Valid;

/**
 * 管理后台的认证 Service 接口
 *
 * 提供用户的登录、登出的能力
 *
 * @author 宇擎源码
 */
public interface AdminAuthService {

    /**
     * 验证账号 + 密码。如果通过，则返回用户
     *
     * @param username 账号
     * @param password 密码
     * @return 用户
     */
    AdminUserDO authenticate(String username, String password);

    /**
     * 账号登录
     *
     * @param reqVO 登录信息
     * @return 登录结果
     */
    AuthLoginRespVO login(@Valid AuthLoginReqVO reqVO);

    /**
     * 基于 token 退出登录
     *
     * @param token token
     * @param logType 登出类型
     */
    void logout(String token, Integer logType);

    /**
     * 短信验证码发送
     *
     * @param reqVO 发送请求
     */
    void sendSmsCode(AuthSmsSendReqVO reqVO);

    /**
     * 短信登录
     *
     * @param reqVO 登录信息
     * @return 登录结果
     */
    AuthLoginRespVO smsLogin(AuthSmsLoginReqVO reqVO);

    /**
     * 社交快捷登录，使用 code 授权码
     *
     * @param reqVO 登录信息
     * @return 登录结果
     */
    AuthLoginRespVO socialLogin(@Valid AuthSocialLoginReqVO reqVO);

    /**
     * IM 工作台免登。已绑定非超管跳过 MFA；超管、未绑定、停用失败。
     */
    AuthLoginRespVO imSilentLogin(@Valid AuthSocialLoginReqVO reqVO);

    /**
     * 刷新访问令牌
     *
     * @param refreshToken 刷新令牌
     * @return 登录结果
     */
    AuthLoginRespVO refreshToken(String refreshToken);

    /**
     * 用户注册
     *
     * @param createReqVO 注册用户
     * @return 注册结果
     */
    AuthLoginRespVO register(AuthRegisterReqVO createReqVO);

    /**
     * 重置密码
     *
     * @param reqVO 验证码信息
     */
    void resetPassword(AuthResetPasswordReqVO reqVO);

    // ========== MFA 切片 3 ==========

    /** PRE_AUTH：发送 SMS/EMAIL 挑战码 */
    void mfaSendCode(AuthMfaCodeSendReqVO reqVO);

    /** PRE_AUTH：校验因子并签发 access/refresh */
    AuthLoginRespVO mfaVerify(AuthMfaVerifyReqVO reqVO);

    /** ENROLLMENT：开始 TOTP 绑定 */
    AuthMfaEnrollmentTotpStartRespVO mfaEnrollmentTotpStart(AuthMfaEnrollmentTotpStartReqVO reqVO);

    /** ENROLLMENT：确认 TOTP 并签发 */
    AuthLoginRespVO mfaEnrollmentTotpConfirm(AuthMfaEnrollmentTotpConfirmReqVO reqVO);

}
