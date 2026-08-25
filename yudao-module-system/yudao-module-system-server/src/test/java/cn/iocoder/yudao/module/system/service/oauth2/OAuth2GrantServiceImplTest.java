package cn.iocoder.yudao.module.system.service.oauth2;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.OAuth2AccessTokenDO;
import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.OAuth2CodeDO;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.service.auth.AdminAuthService;
import cn.iocoder.yudao.module.system.service.mfa.MfaTokenIssuanceFacade;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaIssuanceOutcome;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaLoginStatus;
import cn.iocoder.yudao.module.system.service.mfa.model.MfaIssuanceResult;
import cn.iocoder.yudao.module.system.service.user.AdminUserService;
import com.google.common.collect.Lists;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.List;

import static cn.hutool.core.util.RandomUtil.randomEle;
import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertPojoEquals;
import static cn.iocoder.yudao.framework.test.core.util.RandomUtils.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * {@link OAuth2GrantServiceImpl} 的单元测试
 *
 * @author 宇擎源码
 */
public class OAuth2GrantServiceImplTest extends BaseMockitoUnitTest {

    @InjectMocks
    private OAuth2GrantServiceImpl oauth2GrantService;

    @Mock
    private OAuth2TokenService oauth2TokenService;
    @Mock
    private OAuth2CodeService oauth2CodeService;
    @Mock
    private AdminAuthService adminAuthService;
    @Mock
    private MfaTokenIssuanceFacade mfaTokenIssuanceFacade;
    @Mock
    private AdminUserService adminUserService;

    private static MfaIssuanceResult allowed(OAuth2AccessTokenDO token) {
        return MfaIssuanceResult.builder()
                .outcome(MfaIssuanceOutcome.ALLOWED)
                .loginStatus(MfaLoginStatus.AUTHENTICATED)
                .accessToken(token)
                .build();
    }

    @Test
    public void testGrantImplicit() {
        Long userId = randomLongId();
        Integer userType = randomEle(UserTypeEnum.values()).getValue();
        String clientId = randomString();
        List<String> scopes = Lists.newArrayList("read", "write");
        OAuth2AccessTokenDO accessTokenDO = randomPojo(OAuth2AccessTokenDO.class);
        when(mfaTokenIssuanceFacade.issueForOAuthGrant(any(), eq(userId), any(), eq(userType),
                eq(clientId), eq(scopes), any())).thenReturn(allowed(accessTokenDO));

        assertPojoEquals(accessTokenDO, oauth2GrantService.grantImplicit(
                userId, userType, clientId, scopes));
    }

    @Test
    public void testGrantAuthorizationCodeForCode() {
        Long userId = randomLongId();
        Integer userType = randomEle(UserTypeEnum.values()).getValue();
        String clientId = randomString();
        List<String> scopes = Lists.newArrayList("read", "write");
        String redirectUri = randomString();
        String state = randomString();
        OAuth2CodeDO codeDO = randomPojo(OAuth2CodeDO.class);
        when(oauth2CodeService.createAuthorizationCode(eq(userId), eq(userType),
                eq(clientId), eq(scopes), eq(redirectUri), eq(state))).thenReturn(codeDO);

        assertEquals(codeDO.getCode(), oauth2GrantService.grantAuthorizationCodeForCode(userId, userType,
                clientId, scopes, redirectUri, state));
    }

    @Test
    public void testGrantAuthorizationCodeForAccessToken() {
        String clientId = randomString();
        String code = randomString();
        List<String> scopes = Lists.newArrayList("read", "write");
        String redirectUri = randomString();
        String state = randomString();
        OAuth2CodeDO codeDO = randomPojo(OAuth2CodeDO.class, o -> {
            o.setClientId(clientId);
            o.setRedirectUri(redirectUri);
            o.setState(state);
            o.setScopes(scopes);
        });
        when(oauth2CodeService.consumeAuthorizationCode(eq(code))).thenReturn(codeDO);
        OAuth2AccessTokenDO accessTokenDO = randomPojo(OAuth2AccessTokenDO.class);
        when(mfaTokenIssuanceFacade.issueForOAuthGrant(any(), eq(codeDO.getUserId()), any(),
                eq(codeDO.getUserType()), eq(codeDO.getClientId()), eq(codeDO.getScopes()), any()))
                .thenReturn(allowed(accessTokenDO));

        assertPojoEquals(accessTokenDO, oauth2GrantService.grantAuthorizationCodeForAccessToken(
                clientId, code, redirectUri, state));
    }

    @Test
    public void testGrantPassword() {
        String username = randomString();
        String password = randomString();
        String clientId = randomString();
        List<String> scopes = Lists.newArrayList("read", "write");
        AdminUserDO user = randomPojo(AdminUserDO.class);
        when(adminAuthService.authenticate(eq(username), eq(password))).thenReturn(user);
        OAuth2AccessTokenDO accessTokenDO = randomPojo(OAuth2AccessTokenDO.class);
        when(mfaTokenIssuanceFacade.issueForOAuthGrant(any(), eq(user.getId()), eq(user.getTenantId()),
                eq(UserTypeEnum.ADMIN.getValue()), eq(clientId), eq(scopes), any()))
                .thenReturn(allowed(accessTokenDO));

        assertPojoEquals(accessTokenDO, oauth2GrantService.grantPassword(
                username, password, clientId, scopes));
    }

    @Test
    public void testGrantRefreshToken() {
        String refreshToken = randomString();
        String clientId = randomString();
        OAuth2AccessTokenDO accessTokenDO = randomPojo(OAuth2AccessTokenDO.class);
        when(mfaTokenIssuanceFacade.refresh(any(), eq(refreshToken), eq(clientId)))
                .thenReturn(allowed(accessTokenDO));

        assertPojoEquals(accessTokenDO, oauth2GrantService.grantRefreshToken(
                refreshToken, clientId));
    }

    @Test
    public void testGrantClientCredentials() {
        String clientId = randomString();
        List<String> scopes = Lists.newArrayList("read");
        OAuth2AccessTokenDO accessTokenDO = randomPojo(OAuth2AccessTokenDO.class);
        when(mfaTokenIssuanceFacade.issueClientCredentials(eq(clientId), eq(scopes)))
                .thenReturn(accessTokenDO);
        assertPojoEquals(accessTokenDO, oauth2GrantService.grantClientCredentials(clientId, scopes));
    }

    @Test
    public void testRevokeToken_clientIdError() {
        String clientId = randomString();
        String accessToken = randomString();
        OAuth2AccessTokenDO accessTokenDO = randomPojo(OAuth2AccessTokenDO.class);
        when(oauth2TokenService.getAccessToken(eq(accessToken))).thenReturn(accessTokenDO);

        assertFalse(oauth2GrantService.revokeToken(clientId, accessToken));
    }

}
