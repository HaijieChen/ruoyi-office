package cn.iocoder.yudao.module.system.service.auth;

import cn.hutool.core.util.ReflectUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.system.api.sms.SmsCodeApi;
import cn.iocoder.yudao.module.system.api.social.dto.SocialUserBindReqDTO;
import cn.iocoder.yudao.module.system.api.social.dto.SocialUserRespDTO;
import cn.iocoder.yudao.module.system.controller.admin.auth.vo.*;
import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.OAuth2AccessTokenDO;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.enums.logger.LoginLogTypeEnum;
import cn.iocoder.yudao.module.system.enums.logger.LoginResultEnum;
import cn.iocoder.yudao.module.system.enums.sms.SmsSceneEnum;
import cn.iocoder.yudao.module.system.enums.social.SocialTypeEnum;
import cn.iocoder.yudao.module.system.service.logger.LoginLogService;
import cn.iocoder.yudao.module.system.service.member.MemberService;
import cn.iocoder.yudao.module.system.service.mfa.MfaAuthFlowService;
import cn.iocoder.yudao.module.system.service.mfa.MfaFactorService;
import cn.iocoder.yudao.module.system.service.mfa.MfaTokenIssuanceFacade;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaIssuanceOutcome;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaLoginStatus;
import cn.iocoder.yudao.module.system.service.mfa.model.MfaIssuanceResult;
import cn.iocoder.yudao.module.system.service.oauth2.OAuth2TokenService;
import cn.iocoder.yudao.module.system.enums.permission.RoleCodeEnum;
import cn.iocoder.yudao.module.system.service.permission.PermissionService;
import cn.iocoder.yudao.module.system.service.social.SocialUserService;
import cn.iocoder.yudao.module.system.service.user.AdminUserService;
import com.anji.captcha.model.common.ResponseModel;
import com.anji.captcha.service.CaptchaService;
import jakarta.annotation.Resource;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static cn.hutool.core.util.RandomUtil.randomEle;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertPojoEquals;
import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.framework.test.core.util.RandomUtils.randomPojo;
import static cn.iocoder.yudao.framework.test.core.util.RandomUtils.randomString;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@Import(AdminAuthServiceImpl.class)
public class AdminAuthServiceImplTest extends BaseDbUnitTest {

    @Resource
    private AdminAuthServiceImpl authService;

    @MockitoBean
    private AdminUserService userService;
    @MockitoBean
    private CaptchaService captchaService;
    @MockitoBean
    private LoginLogService loginLogService;
    @MockitoBean
    private SocialUserService socialUserService;
    @MockitoBean
    private SmsCodeApi smsCodeApi;
    @MockitoBean
    private OAuth2TokenService oauth2TokenService;
    @MockitoBean
    private MfaTokenIssuanceFacade mfaTokenIssuanceFacade;
    @MockitoBean
    private MfaAuthFlowService mfaAuthFlowService;
    @MockitoBean
    private MfaFactorService mfaFactorService;
    @MockitoBean
    private MemberService memberService;
    @MockitoBean
    private PermissionService permissionService;
    @MockitoBean
    private Validator validator;

    @BeforeEach
    public void setUp() {
        authService.setCaptchaEnable(true);
        // 注入一个 Validator 对象
        ReflectUtil.setFieldValue(authService, "validator",
                Validation.buildDefaultValidatorFactory().getValidator());
    }

    @Test
    public void testAuthenticate_success() {
        // 准备参数
        String username = randomString();
        String password = randomString();
        // mock user 数据
        AdminUserDO user = randomPojo(AdminUserDO.class, o -> o.setUsername(username)
                .setPassword(password).setStatus(CommonStatusEnum.ENABLE.getStatus()));
        when(userService.getUserByUsername(eq(username))).thenReturn(user);
        // mock password 匹配
        when(userService.isPasswordMatch(eq(password), eq(user.getPassword()))).thenReturn(true);

        // 调用
        AdminUserDO loginUser = authService.authenticate(username, password);
        // 校验
        assertPojoEquals(user, loginUser);
    }

    @Test
    public void testAuthenticate_userNotFound() {
        // 准备参数
        String username = randomString();
        String password = randomString();

        // 调用, 并断言异常
        assertServiceException(() -> authService.authenticate(username, password),
                AUTH_LOGIN_BAD_CREDENTIALS);
        verify(loginLogService).createLoginLog(
                argThat(o -> o.getLogType().equals(LoginLogTypeEnum.LOGIN_USERNAME.getType())
                        && o.getResult().equals(LoginResultEnum.BAD_CREDENTIALS.getResult())
                        && o.getUserId() == null)
        );
    }

    @Test
    public void testAuthenticate_badCredentials() {
        // 准备参数
        String username = randomString();
        String password = randomString();
        // mock user 数据
        AdminUserDO user = randomPojo(AdminUserDO.class, o -> o.setUsername(username)
                .setPassword(password).setStatus(CommonStatusEnum.ENABLE.getStatus()));
        when(userService.getUserByUsername(eq(username))).thenReturn(user);

        // 调用, 并断言异常
        assertServiceException(() -> authService.authenticate(username, password),
                AUTH_LOGIN_BAD_CREDENTIALS);
        verify(loginLogService).createLoginLog(
                argThat(o -> o.getLogType().equals(LoginLogTypeEnum.LOGIN_USERNAME.getType())
                        && o.getResult().equals(LoginResultEnum.BAD_CREDENTIALS.getResult())
                        && o.getUserId().equals(user.getId()))
        );
    }

    @Test
    public void testAuthenticate_userDisabled() {
        // 准备参数
        String username = randomString();
        String password = randomString();
        // mock user 数据
        AdminUserDO user = randomPojo(AdminUserDO.class, o -> o.setUsername(username)
                .setPassword(password).setStatus(CommonStatusEnum.DISABLE.getStatus()));
        when(userService.getUserByUsername(eq(username))).thenReturn(user);
        // mock password 匹配
        when(userService.isPasswordMatch(eq(password), eq(user.getPassword()))).thenReturn(true);

        // 调用, 并断言异常
        assertServiceException(() -> authService.authenticate(username, password),
                AUTH_LOGIN_USER_DISABLED);
        verify(loginLogService).createLoginLog(
                argThat(o -> o.getLogType().equals(LoginLogTypeEnum.LOGIN_USERNAME.getType())
                        && o.getResult().equals(LoginResultEnum.USER_DISABLED.getResult())
                        && o.getUserId().equals(user.getId()))
        );
    }

    @Test
    public void testLogin_success() {
        // 准备参数
        AuthLoginReqVO reqVO = randomPojo(AuthLoginReqVO.class, o ->
                o.setUsername("test_username").setPassword("test_password")
                        .setSocialType(randomEle(SocialTypeEnum.values()).getType()));

        // mock 验证码正确
        authService.setCaptchaEnable(false);
        // mock user 数据
        AdminUserDO user = randomPojo(AdminUserDO.class, o -> o.setId(1L).setUsername("test_username")
                .setPassword("test_password").setStatus(CommonStatusEnum.ENABLE.getStatus()));
        when(userService.getUserByUsername(eq("test_username"))).thenReturn(user);
        // mock password 匹配
        when(userService.isPasswordMatch(eq("test_password"), eq(user.getPassword()))).thenReturn(true);
        // mock MFA Facade 签发
        OAuth2AccessTokenDO accessTokenDO = randomPojo(OAuth2AccessTokenDO.class, o -> o.setUserId(1L)
                .setUserType(UserTypeEnum.ADMIN.getValue()));
        AuthLoginRespVO facadeResp = AuthLoginRespVO.builder()
                .userId(1L)
                .loginStatus(MfaLoginStatus.AUTHENTICATED.name())
                .accessToken(accessTokenDO.getAccessToken())
                .refreshToken(accessTokenDO.getRefreshToken())
                .expiresTime(accessTokenDO.getExpiresTime())
                .build();
        when(mfaTokenIssuanceFacade.issueAfterPrimaryAuth(any(), eq(1L), any(), eq(UserTypeEnum.ADMIN.getValue()),
                eq("default"), isNull(), any())).thenReturn(MfaIssuanceResult.builder()
                .outcome(MfaIssuanceOutcome.ALLOWED)
                .loginStatus(MfaLoginStatus.AUTHENTICATED)
                .accessToken(accessTokenDO)
                .loginResp(facadeResp)
                .build());

        // 调用，并校验
        AuthLoginRespVO loginRespVO = authService.login(reqVO);
        assertEquals(accessTokenDO.getAccessToken(), loginRespVO.getAccessToken());
        assertEquals(accessTokenDO.getRefreshToken(), loginRespVO.getRefreshToken());
        // 校验调用参数
        verify(loginLogService).createLoginLog(
                argThat(o -> o.getLogType().equals(LoginLogTypeEnum.LOGIN_USERNAME.getType())
                        && o.getResult().equals(LoginResultEnum.SUCCESS.getResult())
                        && o.getUserId().equals(user.getId()))
        );
        verify(socialUserService).bindSocialUser(eq(new SocialUserBindReqDTO(
                user.getId(), UserTypeEnum.ADMIN.getValue(),
                reqVO.getSocialType(), reqVO.getSocialCode(), reqVO.getSocialState())));
    }

    @Test
    public void testSendSmsCode() {
        // 准备参数
        String mobile = randomString();
        Integer scene = SmsSceneEnum.ADMIN_MEMBER_LOGIN.getScene();
        AuthSmsSendReqVO reqVO = new AuthSmsSendReqVO(mobile, scene);
        // mock 方法（用户信息）
        AdminUserDO user = randomPojo(AdminUserDO.class);
        when(userService.getUserByMobile(eq(mobile))).thenReturn(user);

        // 调用
        authService.sendSmsCode(reqVO);
        // 断言
        verify(smsCodeApi).sendSmsCode(argThat(sendReqDTO -> {
            assertEquals(mobile, sendReqDTO.getMobile());
            assertEquals(scene, sendReqDTO.getScene());
            return true;
        }));
    }

    @Test
    public void testSmsLogin_success() {
        // 准备参数
        String mobile = randomString();
        String code = randomString();
        AuthSmsLoginReqVO reqVO = new AuthSmsLoginReqVO(mobile, code);
        // mock 方法（验证码）
        when(smsCodeApi.useSmsCode(argThat(smsCodeUseReqDTO -> {
            assertEquals(mobile, smsCodeUseReqDTO.getMobile());
            assertEquals(code, smsCodeUseReqDTO.getCode());
            assertEquals(SmsSceneEnum.ADMIN_MEMBER_LOGIN.getScene(), smsCodeUseReqDTO.getScene());
            return true;
        }))).thenReturn(success(null));
        // mock 方法（用户信息）
        AdminUserDO user = randomPojo(AdminUserDO.class, o -> o.setId(1L));
        when(userService.getUserByMobile(eq(mobile))).thenReturn(user);
        OAuth2AccessTokenDO accessTokenDO = randomPojo(OAuth2AccessTokenDO.class, o -> o.setUserId(1L)
                .setUserType(UserTypeEnum.ADMIN.getValue()));
        AuthLoginRespVO facadeResp = AuthLoginRespVO.builder()
                .userId(1L)
                .loginStatus(MfaLoginStatus.AUTHENTICATED.name())
                .accessToken(accessTokenDO.getAccessToken())
                .refreshToken(accessTokenDO.getRefreshToken())
                .expiresTime(accessTokenDO.getExpiresTime())
                .build();
        when(mfaTokenIssuanceFacade.issueAfterPrimaryAuth(any(), eq(1L), any(), eq(UserTypeEnum.ADMIN.getValue()),
                eq("default"), isNull(), any())).thenReturn(MfaIssuanceResult.builder()
                .outcome(MfaIssuanceOutcome.ALLOWED)
                .loginStatus(MfaLoginStatus.AUTHENTICATED)
                .accessToken(accessTokenDO)
                .loginResp(facadeResp)
                .build());

        // 调用，并断言
        AuthLoginRespVO loginRespVO = authService.smsLogin(reqVO);
        assertEquals(accessTokenDO.getAccessToken(), loginRespVO.getAccessToken());
        // 断言调用
        verify(loginLogService).createLoginLog(
                argThat(o -> o.getLogType().equals(LoginLogTypeEnum.LOGIN_MOBILE.getType())
                        && o.getResult().equals(LoginResultEnum.SUCCESS.getResult())
                        && o.getUserId().equals(user.getId()))
        );
    }

    @Test
    public void testSocialLogin_success() {
        // 准备参数
        AuthSocialLoginReqVO reqVO = randomPojo(AuthSocialLoginReqVO.class);
        // mock 方法（绑定的用户编号）
        Long userId = 1L;
        when(socialUserService.getSocialUserByCode(eq(UserTypeEnum.ADMIN.getValue()), eq(reqVO.getType()),
                eq(reqVO.getCode()), eq(reqVO.getState()))).thenReturn(new SocialUserRespDTO(randomString(), randomString(), randomString(), userId));
        // mock（用户）
        AdminUserDO user = randomPojo(AdminUserDO.class, o -> o.setId(userId));
        when(userService.getUser(eq(userId))).thenReturn(user);
        OAuth2AccessTokenDO accessTokenDO = randomPojo(OAuth2AccessTokenDO.class, o -> o.setUserId(1L)
                .setUserType(UserTypeEnum.ADMIN.getValue()));
        AuthLoginRespVO facadeResp = AuthLoginRespVO.builder()
                .userId(1L)
                .loginStatus(MfaLoginStatus.AUTHENTICATED.name())
                .accessToken(accessTokenDO.getAccessToken())
                .refreshToken(accessTokenDO.getRefreshToken())
                .expiresTime(accessTokenDO.getExpiresTime())
                .build();
        when(mfaTokenIssuanceFacade.issueAfterPrimaryAuth(any(), eq(1L), any(), eq(UserTypeEnum.ADMIN.getValue()),
                eq("default"), isNull(), any())).thenReturn(MfaIssuanceResult.builder()
                .outcome(MfaIssuanceOutcome.ALLOWED)
                .loginStatus(MfaLoginStatus.AUTHENTICATED)
                .accessToken(accessTokenDO)
                .loginResp(facadeResp)
                .build());

        // 调用，并断言
        AuthLoginRespVO loginRespVO = authService.socialLogin(reqVO);
        assertEquals(accessTokenDO.getAccessToken(), loginRespVO.getAccessToken());
        // 断言调用
        verify(loginLogService).createLoginLog(
                argThat(o -> o.getLogType().equals(LoginLogTypeEnum.LOGIN_SOCIAL.getType())
                        && o.getResult().equals(LoginResultEnum.SUCCESS.getResult())
                        && o.getUserId().equals(user.getId()))
        );
    }

    @Test
    public void testImSilentLogin_success() {
        AuthSocialLoginReqVO reqVO = randomPojo(AuthSocialLoginReqVO.class);
        Long userId = 1L;
        when(socialUserService.getSocialUserByCode(eq(UserTypeEnum.ADMIN.getValue()), eq(reqVO.getType()),
                eq(reqVO.getCode()), eq(reqVO.getState()))).thenReturn(
                new SocialUserRespDTO(randomString(), randomString(), randomString(), userId));
        AdminUserDO user = randomPojo(AdminUserDO.class, o -> o.setId(userId)
                .setStatus(CommonStatusEnum.ENABLE.getStatus()));
        when(userService.getUser(eq(userId))).thenReturn(user);
        when(permissionService.hasAnyRoles(eq(userId), eq(RoleCodeEnum.SUPER_ADMIN.getCode()))).thenReturn(false);
        OAuth2AccessTokenDO accessTokenDO = randomPojo(OAuth2AccessTokenDO.class, o -> o.setUserId(1L)
                .setUserType(UserTypeEnum.ADMIN.getValue()));
        AuthLoginRespVO facadeResp = AuthLoginRespVO.builder()
                .userId(1L)
                .loginStatus(MfaLoginStatus.AUTHENTICATED.name())
                .accessToken(accessTokenDO.getAccessToken())
                .refreshToken(accessTokenDO.getRefreshToken())
                .expiresTime(accessTokenDO.getExpiresTime())
                .build();
        when(mfaTokenIssuanceFacade.issueAfterPrimaryAuth(eq(cn.iocoder.yudao.module.system.service.mfa.enums.MfaIssuancePath.LOGIN_IM_SILENT),
                eq(1L), any(), eq(UserTypeEnum.ADMIN.getValue()),
                eq("default"), isNull(), any())).thenReturn(MfaIssuanceResult.builder()
                .outcome(MfaIssuanceOutcome.ALLOWED)
                .loginStatus(MfaLoginStatus.AUTHENTICATED)
                .accessToken(accessTokenDO)
                .loginResp(facadeResp)
                .build());

        AuthLoginRespVO loginRespVO = authService.imSilentLogin(reqVO);
        assertEquals(accessTokenDO.getAccessToken(), loginRespVO.getAccessToken());
        verify(mfaTokenIssuanceFacade).issueAfterPrimaryAuth(
                eq(cn.iocoder.yudao.module.system.service.mfa.enums.MfaIssuancePath.LOGIN_IM_SILENT),
                eq(1L), any(), eq(UserTypeEnum.ADMIN.getValue()), eq("default"), isNull(), any());
    }

    @Test
    public void testImSilentLogin_superAdminForbidden() {
        AuthSocialLoginReqVO reqVO = randomPojo(AuthSocialLoginReqVO.class);
        Long userId = 1L;
        when(socialUserService.getSocialUserByCode(eq(UserTypeEnum.ADMIN.getValue()), eq(reqVO.getType()),
                eq(reqVO.getCode()), eq(reqVO.getState()))).thenReturn(
                new SocialUserRespDTO(randomString(), randomString(), randomString(), userId));
        AdminUserDO user = randomPojo(AdminUserDO.class, o -> o.setId(userId)
                .setStatus(CommonStatusEnum.ENABLE.getStatus()));
        when(userService.getUser(eq(userId))).thenReturn(user);
        when(permissionService.hasAnyRoles(eq(userId), eq(RoleCodeEnum.SUPER_ADMIN.getCode()))).thenReturn(true);

        assertServiceException(() -> authService.imSilentLogin(reqVO), AUTH_IM_SUPER_ADMIN_FORBIDDEN);
        verify(mfaTokenIssuanceFacade, never()).issueAfterPrimaryAuth(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    public void testValidateCaptcha_successWithEnable() {
        // 准备参数
        AuthLoginReqVO reqVO = randomPojo(AuthLoginReqVO.class);

        // mock 验证通过
        when(captchaService.verification(argThat(captchaVO -> {
            assertEquals(reqVO.getCaptchaVerification(), captchaVO.getCaptchaVerification());
            return true;
        }))).thenReturn(ResponseModel.success());

        // 调用，无需断言
        authService.validateCaptcha(reqVO);
    }

    @Test
    public void testValidateCaptcha_successWithDisable() {
        // 准备参数
        AuthLoginReqVO reqVO = randomPojo(AuthLoginReqVO.class);

        // mock 验证码关闭
        authService.setCaptchaEnable(false);

        // 调用，无需断言
        authService.validateCaptcha(reqVO);
    }

    @Test
    public void testCaptcha_fail() {
        // 准备参数
        AuthLoginReqVO reqVO = randomPojo(AuthLoginReqVO.class);

        // mock 验证通过
        when(captchaService.verification(argThat(captchaVO -> {
            assertEquals(reqVO.getCaptchaVerification(), captchaVO.getCaptchaVerification());
            return true;
        }))).thenReturn(ResponseModel.errorMsg("就是不对"));

        // 调用, 并断言异常
        assertServiceException(() -> authService.validateCaptcha(reqVO), AUTH_LOGIN_CAPTCHA_CODE_ERROR, "就是不对");
        // 校验调用参数
        verify(loginLogService).createLoginLog(
                argThat(o -> o.getLogType().equals(LoginLogTypeEnum.LOGIN_USERNAME.getType())
                        && o.getResult().equals(LoginResultEnum.CAPTCHA_CODE_ERROR.getResult()))
        );
    }

    @Test
    public void testRefreshToken() {
        // 准备参数
        String refreshToken = randomString();
        // mock 方法
        OAuth2AccessTokenDO accessTokenDO = randomPojo(OAuth2AccessTokenDO.class);
        AuthLoginRespVO facadeResp = AuthLoginRespVO.builder()
                .userId(accessTokenDO.getUserId())
                .loginStatus(MfaLoginStatus.AUTHENTICATED.name())
                .accessToken(accessTokenDO.getAccessToken())
                .refreshToken(accessTokenDO.getRefreshToken())
                .expiresTime(accessTokenDO.getExpiresTime())
                .build();
        when(mfaTokenIssuanceFacade.refresh(any(), eq(refreshToken), eq("default")))
                .thenReturn(MfaIssuanceResult.builder()
                        .outcome(MfaIssuanceOutcome.ALLOWED)
                        .loginStatus(MfaLoginStatus.AUTHENTICATED)
                        .accessToken(accessTokenDO)
                        .loginResp(facadeResp)
                        .build());

        // 调用
        AuthLoginRespVO loginRespVO = authService.refreshToken(refreshToken);
        // 断言
        assertEquals(accessTokenDO.getAccessToken(), loginRespVO.getAccessToken());
    }

    @Test
    public void testLogout_success() {
        // 准备参数
        String token = randomString();
        // mock
        OAuth2AccessTokenDO accessTokenDO = randomPojo(OAuth2AccessTokenDO.class, o -> o.setUserId(1L)
                .setUserType(UserTypeEnum.ADMIN.getValue()));
        when(oauth2TokenService.removeAccessToken(eq(token))).thenReturn(accessTokenDO);

        // 调用
        authService.logout(token, LoginLogTypeEnum.LOGOUT_SELF.getType());
        // 校验调用参数
        verify(loginLogService).createLoginLog(
                argThat(o -> o.getLogType().equals(LoginLogTypeEnum.LOGOUT_SELF.getType())
                        && o.getResult().equals(LoginResultEnum.SUCCESS.getResult()))
        );
        // 调用，并校验

    }

    @Test
    public void testLogout_fail() {
        // 准备参数
        String token = randomString();

        // 调用
        authService.logout(token, LoginLogTypeEnum.LOGOUT_SELF.getType());
        // 校验调用参数
        verify(loginLogService, never()).createLoginLog(any());
    }

}
