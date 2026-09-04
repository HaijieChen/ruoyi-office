package cn.iocoder.yudao.module.bpm.framework.im;

import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task.BpmTaskApproveReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task.BpmTaskRejectReqVO;
import cn.iocoder.yudao.module.bpm.service.task.BpmTaskService;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.enums.permission.RoleCodeEnum;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ImCardActionService {

    public static final String APPROVE = "APPROVE";
    public static final String REJECT = "REJECT";
    public static final String WITHDRAW = "WITHDRAW";

    private final Set<String> doneKeys = ConcurrentHashMap.newKeySet();

    @Resource
    private BpmTaskService bpmTaskService;
    @Resource
    private PermissionApi permissionApi;

    public ImCardActionResult handle(Long actorUserId, String action, ImCardTaskSnapshot snapshot, String eventId) {
        if (actorUserId == null || snapshot == null) {
            return ImCardActionResult.forbidden("无法识别办理人");
        }
        Boolean superAdmin = permissionApi.hasAnyRoles(actorUserId, RoleCodeEnum.SUPER_ADMIN.getCode()).getData();
        if (Boolean.TRUE.equals(superAdmin)) {
            return ImCardActionResult.forbidden("超级管理员请在电脑上审批");
        }
        String idempotencyKey = (eventId == null ? "" : eventId) + ":" + snapshot.getTaskId() + ":" + action;
        if (!doneKeys.add(idempotencyKey)) {
            return ImCardActionResult.duplicate();
        }
        try {
            if (APPROVE.equals(action)) {
                if (!ImCardGate.canApprove(snapshot, actorUserId)) {
                    doneKeys.remove(idempotencyKey);
                    return ImCardActionResult.openApp();
                }
                BpmTaskApproveReqVO req = new BpmTaskApproveReqVO();
                req.setId(snapshot.getTaskId());
                bpmTaskService.approveTask(actorUserId, req);
                return ImCardActionResult.done();
            }
            if (REJECT.equals(action)) {
                if (!ImCardGate.canReject(snapshot, actorUserId)) {
                    doneKeys.remove(idempotencyKey);
                    return ImCardActionResult.openApp();
                }
                BpmTaskRejectReqVO req = new BpmTaskRejectReqVO();
                req.setId(snapshot.getTaskId());
                req.setReason("IM卡片拒绝");
                bpmTaskService.rejectTask(actorUserId, req);
                return ImCardActionResult.done();
            }
            if (WITHDRAW.equals(action)) {
                if (!ImCardGate.canWithdraw(snapshot, actorUserId)) {
                    doneKeys.remove(idempotencyKey);
                    return ImCardActionResult.openApp();
                }
                bpmTaskService.withdrawProcessToStart(actorUserId, snapshot.getProcessInstanceId(), "IM卡片撤回");
                return ImCardActionResult.done();
            }
            doneKeys.remove(idempotencyKey);
            return ImCardActionResult.openApp();
        } catch (RuntimeException ex) {
            doneKeys.remove(idempotencyKey);
            throw ex;
        }
    }
}
