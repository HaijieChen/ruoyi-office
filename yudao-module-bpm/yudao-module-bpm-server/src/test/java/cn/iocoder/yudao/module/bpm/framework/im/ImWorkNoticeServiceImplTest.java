package cn.iocoder.yudao.module.bpm.framework.im;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.bpm.service.message.dto.BpmMessageSendWhenTaskCreatedReqDTO;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.social.SocialUserApi;
import cn.iocoder.yudao.module.system.api.social.dto.SocialUserRespDTO;
import cn.iocoder.yudao.module.system.enums.permission.RoleCodeEnum;
import cn.iocoder.yudao.module.system.enums.social.SocialTypeEnum;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ImWorkNoticeServiceImplTest extends BaseMockitoUnitTest {

    @InjectMocks
    private ImWorkNoticeServiceImpl noticeService;
    @Mock
    private PermissionApi permissionApi;
    @Mock
    private SocialUserApi socialUserApi;
    @Mock
    private ImWorkNoticeClient imWorkNoticeClient;

    @Test
    void skipSuperAdmin() {
        BpmMessageSendWhenTaskCreatedReqDTO req = sample(1L, 2L);
        when(permissionApi.hasAnyRoles(eq(1L), eq(RoleCodeEnum.SUPER_ADMIN.getCode())))
                .thenReturn(success(true));

        assertEquals(0, noticeService.notifyTaskAssigned(req));
        verify(imWorkNoticeClient, never()).send(any(), any(), any(), any());
    }

    @Test
    void skipUnbound() {
        BpmMessageSendWhenTaskCreatedReqDTO req = sample(1L, 1L);
        when(permissionApi.hasAnyRoles(eq(1L), eq(RoleCodeEnum.SUPER_ADMIN.getCode())))
                .thenReturn(success(false));
        when(socialUserApi.getSocialUserByUserId(anyInt(), anyLong(), anyInt()))
                .thenReturn(success(null));

        assertEquals(0, noticeService.notifyTaskAssigned(req));
        verify(imWorkNoticeClient, never()).send(any(), any(), any(), any());
    }

    @Test
    void sendWhenBoundWeCom() {
        BpmMessageSendWhenTaskCreatedReqDTO req = sample(1L, 1L);
        when(permissionApi.hasAnyRoles(eq(1L), eq(RoleCodeEnum.SUPER_ADMIN.getCode())))
                .thenReturn(success(false));
        when(socialUserApi.getSocialUserByUserId(eq(UserTypeEnum.ADMIN.getValue()), eq(1L),
                eq(SocialTypeEnum.WECHAT_ENTERPRISE.getType())))
                .thenReturn(success(new SocialUserRespDTO("wx-openid", "n", "a", 1L)));
        when(socialUserApi.getSocialUserByUserId(eq(UserTypeEnum.ADMIN.getValue()), eq(1L),
                eq(SocialTypeEnum.DINGTALK.getType()))).thenReturn(success(null));
        when(socialUserApi.getSocialUserByUserId(eq(UserTypeEnum.ADMIN.getValue()), eq(1L),
                eq(SocialTypeEnum.FEISHU.getType()))).thenReturn(success(null));

        assertEquals(1, noticeService.notifyTaskAssigned(req));
        verify(imWorkNoticeClient).send(eq(SocialTypeEnum.WECHAT_ENTERPRISE), eq("wx-openid"),
                eq("请假：经理审批"), eq("/im/approval/silent-login?id=pi-1&taskId=t-1"));
    }

    private static BpmMessageSendWhenTaskCreatedReqDTO sample(Long assignee, Long starter) {
        BpmMessageSendWhenTaskCreatedReqDTO req = new BpmMessageSendWhenTaskCreatedReqDTO();
        req.setProcessInstanceId("pi-1");
        req.setProcessInstanceName("请假");
        req.setTaskId("t-1");
        req.setTaskName("经理审批");
        req.setAssigneeUserId(assignee);
        req.setStartUserId(starter);
        req.setStartUserNickname("张三");
        return req;
    }
}
