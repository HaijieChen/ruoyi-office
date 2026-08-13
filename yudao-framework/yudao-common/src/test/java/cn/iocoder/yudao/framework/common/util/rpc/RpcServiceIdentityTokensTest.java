package cn.iocoder.yudao.framework.common.util.rpc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RpcServiceIdentityTokensTest {

    @Test
    void signAndVerify_financeServer_ok() {
        String secret = "test-secret";
        String token = RpcServiceIdentityTokens.sign(RpcServiceIdentityConstants.FINANCE_SERVER, secret);
        assertTrue(RpcServiceIdentityTokens.verify(
                RpcServiceIdentityConstants.FINANCE_SERVER, token, secret));
    }

    @Test
    void forgedToken_rejected() {
        String secret = "test-secret";
        assertFalse(RpcServiceIdentityTokens.verify(
                RpcServiceIdentityConstants.FINANCE_SERVER, "deadbeef", secret));
    }

    @Test
    void wrongServiceName_rejected() {
        String secret = "test-secret";
        String token = RpcServiceIdentityTokens.sign(RpcServiceIdentityConstants.FINANCE_SERVER, secret);
        assertFalse(RpcServiceIdentityTokens.verify("crm-server", token, secret));
    }

    @Test
    void blankInputs_rejected() {
        assertFalse(RpcServiceIdentityTokens.verify(null, "x", "s"));
        assertFalse(RpcServiceIdentityTokens.verify("finance-server", null, "s"));
        assertFalse(RpcServiceIdentityTokens.verify("finance-server", "x", null));
    }
}
