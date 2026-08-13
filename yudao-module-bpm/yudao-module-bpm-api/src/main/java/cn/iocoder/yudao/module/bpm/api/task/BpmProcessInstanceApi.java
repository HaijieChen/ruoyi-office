package cn.iocoder.yudao.module.bpm.api.task;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.yudao.module.bpm.enums.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.validation.Valid;
import java.util.Collection;

@FeignClient(name = ApiConstants.NAME) // TODO 芋艿：fallbackFactory =
@Tag(name = "RPC 服务 - 流程实例")
public interface BpmProcessInstanceApi {

    String PREFIX = ApiConstants.PREFIX + "/process-instance";

    @PostMapping(PREFIX + "/create")
    @Operation(summary = "创建流程实例（通用内部 RPC），返回实例编号；薪税菜单专属 key 恒 deny")
    @Parameter(name = "userId", description = "用户编号", required = true, example = "1")
    CommonResult<String> createProcessInstance(@RequestParam("userId") Long userId,
                                               @Valid @RequestBody BpmProcessInstanceCreateReqDTO reqDTO);

    /**
     * 可信业务通道创建：服务端派生信任（ThreadLocal），禁止调用方自报。
     * 仅 Finance 等业务领域服务启动薪税等菜单专属流程时使用。
     */
    @PostMapping(PREFIX + "/create-by-business")
    @Operation(summary = "创建流程实例（可信业务通道，服务端派生信任）")
    @Parameter(name = "userId", description = "用户编号", required = true, example = "1")
    CommonResult<String> createProcessInstanceByBusiness(@RequestParam("userId") Long userId,
                                                         @Valid @RequestBody BpmProcessInstanceCreateReqDTO reqDTO);

    @PostMapping(PREFIX + "/submit")
    @Operation(summary = "智能提交流程实例（提供给内部），如果流程实例不存在则创建，存在则审批发起人任务")
    @Parameter(name = "userId", description = "用户编号", required = true, example = "1")
    CommonResult<String> submitProcessInstance(@RequestParam("userId") Long userId,
                                               @Valid @RequestBody BpmProcessInstanceCreateReqDTO reqDTO);

    @PostMapping(PREFIX + "/cancel-by-start-user")
    @Operation(summary = "发起人取消运行中流程实例（提供给内部）")
    CommonResult<Boolean> cancelProcessInstanceByStartUser(
            @RequestParam("userId") Long userId,
            @RequestParam("processInstanceId") String processInstanceId,
            @RequestParam("reason") String reason,
            @RequestParam(value = "forbiddenTaskDefinitionKeys", required = false)
            Collection<String> forbiddenTaskDefinitionKeys);

}
