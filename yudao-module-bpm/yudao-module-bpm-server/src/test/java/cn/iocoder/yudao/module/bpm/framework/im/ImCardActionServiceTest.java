package cn.iocoder.yudao.module.bpm.framework.im;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task.BpmTaskApproveReqVO;
import cn.iocoder.yudao.module.bpm.service.task.BpmTaskService;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.enums.permission.RoleCodeEnum;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ImCardActionServiceTest extends BaseMockitoUnitTest {

    @InjectMocks
    private ImCardActionService actionService;
    @Mock
    private BpmTaskService bpmTaskService;
    @Mock
    private PermissionApi permissionApi;

    @Test
    void approveWhenLegal() {
        when(permissionApi.hasAnyRoles(eq(8L), eq(RoleCodeEnum.SUPER_ADMIN.getCode())))
                .thenReturn(success(false));
        ImCardActionResult result = actionService.handle(8L, ImCardActionService.APPROVE, legal(), "e1");
        assertEquals(ImCardActionResult.DONE, result.getOutcome());
        verify(bpmTaskService).approveTask(eq(8L), any(BpmTaskApproveReqVO.class));
    }

    @Test
    void reasonRequireDoesNotApprove() {
        when(permissionApi.hasAnyRoles(eq(8L), eq(RoleCodeEnum.SUPER_ADMIN.getCode())))
                .thenReturn(success(false));
        ImCardTaskSnapshot snap = legal();
        snap.setReasonRequire(true);
        ImCardActionResult result = actionService.handle(8L, ImCardActionService.APPROVE, snap, "e2");
        assertEquals(ImCardActionResult.OPEN_APP, result.getOutcome());
        verify(bpmTaskService, never()).approveTask(anyLong(), any());
    }

    @Test
    void withdrawCallsStartNode() {
        when(permissionApi.hasAnyRoles(eq(3L), eq(RoleCodeEnum.SUPER_ADMIN.getCode())))
                .thenReturn(success(false));
        ImCardActionResult result = actionService.handle(3L, ImCardActionService.WITHDRAW, legal(), "e3");
        assertEquals(ImCardActionResult.DONE, result.getOutcome());
        verify(bpmTaskService).withdrawProcessToStart(eq(3L), eq("p1"), anyString());
    }

    @Test
    void superAdminForbidden() {
        when(permissionApi.hasAnyRoles(eq(8L), eq(RoleCodeEnum.SUPER_ADMIN.getCode())))
                .thenReturn(success(true));
        ImCardActionResult result = actionService.handle(8L, ImCardActionService.APPROVE, legal(), "e4");
        assertEquals(ImCardActionResult.FORBIDDEN, result.getOutcome());
        verify(bpmTaskService, never()).approveTask(anyLong(), any());
    }

    @Test
    void duplicateEventDoesNotApproveTwice() {
        when(permissionApi.hasAnyRoles(eq(8L), eq(RoleCodeEnum.SUPER_ADMIN.getCode())))
                .thenReturn(success(false));
        actionService.handle(8L, ImCardActionService.APPROVE, legal(), "same");
        ImCardActionResult second = actionService.handle(8L, ImCardActionService.APPROVE, legal(), "same");
        assertEquals(ImCardActionResult.DUPLICATE, second.getOutcome());
        verify(bpmTaskService, times(1)).approveTask(eq(8L), any());
    }

    private static ImCardTaskSnapshot legal() {
        ImCardTaskSnapshot snap = new ImCardTaskSnapshot();
        snap.setTaskId("t1");
        snap.setProcessInstanceId("p1");
        snap.setAssigneeUserId(8L);
        snap.setStartUserId(3L);
        snap.setSignEnable(false);
        snap.setReasonRequire(false);
        snap.setApproveButtonEnabled(true);
        snap.setProcessRunning(true);
        return snap;
    }
}
