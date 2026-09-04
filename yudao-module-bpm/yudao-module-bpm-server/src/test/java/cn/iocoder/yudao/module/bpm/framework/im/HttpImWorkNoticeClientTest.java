package cn.iocoder.yudao.module.bpm.framework.im;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.system.enums.social.SocialTypeEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class HttpImWorkNoticeClientTest extends BaseMockitoUnitTest {

    @InjectMocks
    private HttpImWorkNoticeClient client;
    @Mock
    private ImHttpPoster imHttpPoster;
    @Mock
    private ImProperties imProperties;

    @BeforeEach
    void creds() {
        ImProperties.Wecom wecom = new ImProperties.Wecom();
        wecom.setCorpId("ww1");
        wecom.setSecret("sec");
        wecom.setAgentId(100);
        ImProperties.Dingtalk ding = new ImProperties.Dingtalk();
        ding.setAppKey("dk");
        ding.setAppSecret("ds");
        ding.setAgentId(200L);
        ImProperties.Feishu feishu = new ImProperties.Feishu();
        feishu.setAppId("cli");
        feishu.setAppSecret("fs");
        lenient().when(imProperties.getWecom()).thenReturn(wecom);
        lenient().when(imProperties.getDingtalk()).thenReturn(ding);
        lenient().when(imProperties.getFeishu()).thenReturn(feishu);
    }

    @Test
    void wecomPostsOfficialSend() {
        when(imHttpPoster.get(contains("qyapi.weixin.qq.com/cgi-bin/gettoken")))
                .thenReturn("{\"access_token\":\"tok\"}");
        client.send(SocialTypeEnum.WECHAT_ENTERPRISE, "wo-user", "请假待办", "/im/x");
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(imHttpPoster).postJson(contains("qyapi.weixin.qq.com/cgi-bin/message/send"), body.capture());
        assertTrue(body.getValue().contains("wo-user"));
        assertTrue(body.getValue().contains("/im/x"));
    }

    @Test
    void dingtalkPostsOfficialSend() {
        when(imHttpPoster.get(contains("oapi.dingtalk.com/gettoken")))
                .thenReturn("{\"access_token\":\"tok\"}");
        client.send(SocialTypeEnum.DINGTALK, "ding-user", "请假待办", "/im/x");
        verify(imHttpPoster).postJson(contains("oapi.dingtalk.com/topapi/message/corpconversation/asyncsend_v2"),
                contains("ding-user"));
    }

    @Test
    void feishuPostsOfficialSend() {
        when(imHttpPoster.postJson(contains("open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal"), anyString()))
                .thenReturn("{\"tenant_access_token\":\"tok\"}");
        client.send(SocialTypeEnum.FEISHU, "ou_user", "请假待办", "/im/x");
        verify(imHttpPoster).postJson(contains("open.feishu.cn/open-apis/im/v1/messages"),
                contains("ou_user"), eq("Bearer tok"));
    }

    @Test
    void skipWhenWecomCredsMissing() {
        when(imProperties.getWecom()).thenReturn(new ImProperties.Wecom());
        client.send(SocialTypeEnum.WECHAT_ENTERPRISE, "wo-user", "t", "u");
        verify(imHttpPoster, never()).postJson(anyString(), anyString());
    }
}
