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
    void wrongAudience_path_rejected() {
        String other = "POST /rpc-api/bpm/process-instance/create@bpm-server";
        String token = RpcServiceIdentityTokens.sign(
                RpcServiceIdentityConstants.FINANCE_SERVER, other, SECRET);
        assertFalse(RpcServiceIdentityTokens.verify(
                RpcServiceIdentityConstants.FINANCE_SERVER, AUDIENCE, token, SECRET));
    }

    @Test
    void wrongAudience_target_rejected() {
        // 他服务同路径不得得到可授权令牌
        String otherTarget = "POST /rpc-api/bpm/process-instance/create-by-business@system-server";
        String token = RpcServiceIdentityTokens.sign(
                RpcServiceIdentityConstants.FINANCE_SERVER, otherTarget, SECRET);
        assertFalse(RpcServiceIdentityTokens.verify(
                RpcServiceIdentityConstants.FINANCE_SERVER, AUDIENCE, token, SECRET));
    }

    @Test
    void exactPathNormalization() {
        assertTrue(RpcServiceIdentityConstants.isExactPrivilegedCreateByBusiness(
                "POST", "/rpc-api/bpm/process-instance/create-by-business"));
        assertTrue(RpcServiceIdentityConstants.isExactPrivilegedCreateByBusiness(
                "post", "/rpc-api/bpm/process-instance/create-by-business/"));
        assertFalse(RpcServiceIdentityConstants.isExactPrivilegedCreateByBusiness(
                "POST", "/rpc-api/bpm/process-instance/create-by-business/extra"));
        assertFalse(RpcServiceIdentityConstants.isExactPrivilegedCreateByBusiness(
                "POST", "/rpc-api/bpm/process-instance/create"));
        assertFalse(RpcServiceIdentityConstants.isExactPrivilegedCreateByBusiness(
                "GET", "/rpc-api/bpm/process-instance/create-by-business"));
    }

    @Test
    void blankInputs_rejected() {
        assertFalse(RpcServiceIdentityTokens.verify(null, AUDIENCE, "x", "s"));
        assertFalse(RpcServiceIdentityTokens.verify("finance-server", null, "x", "s"));
        assertFalse(RpcServiceIdentityTokens.verify("finance-server", AUDIENCE, null, "s"));
        assertFalse(RpcServiceIdentityTokens.verify("finance-server", AUDIENCE, "x", null));
    }
}
