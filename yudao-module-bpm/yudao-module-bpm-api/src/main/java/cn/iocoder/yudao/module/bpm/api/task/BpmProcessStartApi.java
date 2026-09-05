package cn.iocoder.yudao.module.bpm.api.task;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.bpm.enums.ApiConstants;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** 统一新单发起的只读资格校验；身份与租户只能来自认证上下文。 */
@FeignClient(name = ApiConstants.NAME, contextId = "bpmProcessStartApi", primary = false)
public interface BpmProcessStartApi {
    @GetMapping(ApiConstants.PREFIX + "/process-start/validate")
    CommonResult<Boolean> validateStart(@RequestParam("processDefinitionKey") String processDefinitionKey);
}
