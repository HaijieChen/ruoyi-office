package cn.iocoder.yudao.module.bpm.api.task;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.instance.BpmProcessInstanceCancelReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task.BpmTaskReturnReqVO;
import cn.iocoder.yudao.module.bpm.service.task.BpmProcessInstanceService;
import cn.iocoder.yudao.module.bpm.service.task.BpmProcessInstanceShareService;
import cn.iocoder.yudao.module.bpm.service.task.BpmTaskService;
import org.flowable.bpmn.model.UserTask;
import cn.hutool.core.collection.CollUtil;
import org.springframework.context.annotation.Primary;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * Flowable 流程实例 Api 实现类
 * <p>
 * EXP-87：{@code @Primary} 保证 monorepo 下父类型 {@link BpmProcessInstanceApi} 注入
 * 解析到本基线实现，而非 Finance 专用本地适配器（防 privileged 通道越权）。
 *
 * @author 宇擎源码
 * @author jason
 */
@RestController
@Primary
@Validated
public class BpmProcessInstanceApiImpl implements BpmProcessInstanceApi {

    @Resource
    private BpmProcessInstanceService processInstanceService;
    @Resource
    private BpmProcessInstanceShareService processInstanceShareService;
    @Resource
    private BpmTaskService taskService;

    @Override
    public CommonResult<String> createProcessInstance(Long userId, @Valid BpmProcessInstanceCreateReqDTO reqDTO) {
        // 通用 RPC：服务端不置可信通道；薪税 key 恒 deny（即使 body 夹带任何伪造字段）
        return success(processInstanceService.createProcessInstance(userId, reqDTO));
    }

    @Override
    public CommonResult<String> createProcessInstanceByBusiness(Long userId,
                                                               @Valid BpmProcessInstanceCreateReqDTO reqDTO) {
        // 可信业务通道：信任由服务端 ThreadLocal 派生，非 DTO
        return success(processInstanceService.createProcessInstanceByBusiness(userId, reqDTO));
    }

    @Override
    public CommonResult<String> submitProcessInstance(Long userId, @Valid BpmProcessInstanceCreateReqDTO reqDTO) {
        return success(processInstanceService.submitProcessInstance(userId, reqDTO));
    }

    @Override
    public CommonResult<Boolean> cancelProcessInstanceByStartUser(
            Long userId, String processInstanceId, String reason,
            java.util.Collection<String> forbiddenTaskDefinitionKeys) {
        BpmProcessInstanceCancelReqVO cancelReqVO = new BpmProcessInstanceCancelReqVO();
        cancelReqVO.setId(processInstanceId);
        cancelReqVO.setReason(reason);
        processInstanceService.cancelProcessInstanceByStartUser(
                userId, cancelReqVO, forbiddenTaskDefinitionKeys);
        return success(true);
    }

    @Override
    public CommonResult<Boolean> returnCurrentTaskToStartUserTask(Long userId, String taskId, String reason) {
        java.util.List<UserTask> previous = taskService.getUserTaskListByReturn(taskId);
        if (CollUtil.isEmpty(previous)) {
            return success(false);
        }
        UserTask target = previous.get(previous.size() - 1);
        BpmTaskReturnReqVO req = new BpmTaskReturnReqVO();
        req.setId(taskId);
        req.setTargetTaskDefinitionKey(target.getId());
        req.setReason(reason);
        taskService.returnTask(userId, req);
        return success(true);
    }

    @Override
    public CommonResult<Boolean> canAccessRelated(Long userId, String processInstanceId) {
        return success(processInstanceShareService.canAccessRelated(userId, processInstanceId));
    }

    @Override
    public CommonResult<java.util.Set<String>> listSharedInstanceIds(Long userId) {
        return success(processInstanceShareService.listActiveSharedInstanceIds(userId));
    }

}
