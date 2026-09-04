package cn.iocoder.yudao.module.bpm.framework.im;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.system.api.social.SocialUserApi;
import cn.iocoder.yudao.module.system.api.social.dto.SocialUserRespDTO;
import cn.iocoder.yudao.module.system.enums.social.SocialTypeEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ImCardSignedActionServiceTest extends BaseMockitoUnitTest {

    @InjectMocks
    private ImCardSignedActionService signedActionService;
    @Mock
    private ImProperties imProperties;
    @Mock
    private SocialUserApi socialUserApi;
    @Mock
    private ImCardActionService imCardActionService;

    @BeforeEach
    void secret() {
        when(imProperties.getCardSecret()).thenReturn("card-secret");
    }

    @Test
    void badSignatureForbidden() {
        ImCardActionResult result = signedActionService.handle(SocialTypeEnum.WECHAT_ENTERPRISE.getType(),
                "wo", System.currentTimeMillis(), "deadbeef", "APPROVE", "e1", snap());
        assertEquals(ImCardActionResult.FORBIDDEN, result.getOutcome());
        verify(imCardActionService, never()).handle(anyLong(), anyString(), any(), anyString());
    }

    @Test
    void unboundForbidden() {
        long ts = System.currentTimeMillis();
        String sig = ImCardSignature.sign("card-secret", ts, "e1",
                SocialTypeEnum.WECHAT_ENTERPRISE.getType(), "wo", "APPROVE");
        when(socialUserApi.getSocialUserByOpenid(eq(UserTypeEnum.ADMIN.getValue()),
                eq(SocialTypeEnum.WECHAT_ENTERPRISE.getType()), eq("wo")))
                .thenReturn(success(null));
        ImCardActionResult result = signedActionService.handle(SocialTypeEnum.WECHAT_ENTERPRISE.getType(),
                "wo", ts, sig, "APPROVE", "e1", snap());
        assertEquals(ImCardActionResult.FORBIDDEN, result.getOutcome());
        verify(imCardActionService, never()).handle(anyLong(), anyString(), any(), anyString());
    }

    @Test
    void boundMapsToOaUser() {
        long ts = System.currentTimeMillis();
        String sig = ImCardSignature.sign("card-secret", ts, "e1",
                SocialTypeEnum.WECHAT_ENTERPRISE.getType(), "wo", "APPROVE");
        when(socialUserApi.getSocialUserByOpenid(eq(UserTypeEnum.ADMIN.getValue()),
                eq(SocialTypeEnum.WECHAT_ENTERPRISE.getType()), eq("wo")))
                .thenReturn(success(new SocialUserRespDTO("wo", "n", "a", 8L)));
        when(imCardActionService.handle(eq(8L), eq("APPROVE"), any(), eq("e1")))
                .thenReturn(ImCardActionResult.done());
        ImCardActionResult result = signedActionService.handle(SocialTypeEnum.WECHAT_ENTERPRISE.getType(),
                "wo", ts, sig, "APPROVE", "e1", snap());
        assertEquals(ImCardActionResult.DONE, result.getOutcome());
        verify(imCardActionService).handle(eq(8L), eq("APPROVE"), any(), eq("e1"));
    }

    private static ImCardTaskSnapshot snap() {
        ImCardTaskSnapshot snap = new ImCardTaskSnapshot();
        snap.setTaskId("t1");
        snap.setProcessInstanceId("p1");
        snap.setAssigneeUserId(8L);
        return snap;
    }
}
