package cn.iocoder.yudao.module.bpm.api.task;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.bpm.enums.ApiConstants;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 当前登录用户是否为该 processInstance 的引擎真实候选/办理人或历史办理人。
 * 身份只来自认证上下文；调用方必须传入库内 processInstanceId，不能用客户端任意 processId 换权。
 */
@FeignClient(name = ApiConstants.NAME, contextId = "bpmProcessParticipantApi", primary = false)
public interface BpmProcessParticipantApi {

    @GetMapping(ApiConstants.PREFIX + "/process-participant/can-read")
    CommonResult<Boolean> canReadProcess(@RequestParam("processInstanceId") String processInstanceId);
}
