package cn.iocoder.yudao.framework.common.util.rpc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RpcServiceIdentityTokensTest {

    private static final String SECRET = "prod-injected-rpc-service-identity-key-9f3a";
    private static final String AUDIENCE = RpcServiceIdentityConstants.AUDIENCE_BPM_CREATE_BY_BUSINESS;

    @Test
    void signAndVerify_financeServer_ok() {
        String token = RpcServiceIdentityTokens.sign(
                RpcServiceIdentityConstants.FINANCE_SERVER, AUDIENCE, SECRET);
        assertTrue(RpcServiceIdentityTokens.verify(
                RpcServiceIdentityConstants.FINANCE_SERVER, AUDIENCE, token, SECRET));
    }

    @Test
    void forgedToken_rejected() {
        assertFalse(RpcServiceIdentityTokens.verify(
                RpcServiceIdentityConstants.FINANCE_SERVER, AUDIENCE, "deadbeef", SECRET));
    }

    @Test
    void wrongServiceName_rejected() {
        String token = RpcServiceIdentityTokens.sign(
                RpcServiceIdentityConstants.FINANCE_SERVER, AUDIENCE, SECRET);
        assertFalse(RpcServiceIdentityTokens.verify("crm-server", AUDIENCE, token, SECRET));
    }

    @Test
    void wrongAudience_rejected_crossRouteReplay() {
        // 其他路由截获的 token 不能授权 create-by-business
        String otherAudience = "POST /rpc-api/bpm/process-instance/create";
        String token = RpcServiceIdentityTokens.sign(
                RpcServiceIdentityConstants.FINANCE_SERVER, otherAudience, SECRET);
        assertFalse(RpcServiceIdentityTokens.verify(
                RpcServiceIdentityConstants.FINANCE_SERVER, AUDIENCE, token, SECRET));
        // 系统调用路径
        String systemAudience = "POST /rpc-api/system/oauth2/token";
        String sysToken = RpcServiceIdentityTokens.sign(
                RpcServiceIdentityConstants.FINANCE_SERVER, systemAudience, SECRET);
        assertFalse(RpcServiceIdentityTokens.verify(
                RpcServiceIdentityConstants.FINANCE_SERVER, AUDIENCE, sysToken, SECRET));
    }

    @Test
    void blankInputs_rejected() {
        assertFalse(RpcServiceIdentityTokens.verify(null, AUDIENCE, "x", "s"));
        assertFalse(RpcServiceIdentityTokens.verify("finance-server", null, "x", "s"));
        assertFalse(RpcServiceIdentityTokens.verify("finance-server", AUDIENCE, null, "s"));
        assertFalse(RpcServiceIdentityTokens.verify("finance-server", AUDIENCE, "x", null));
    }
}
