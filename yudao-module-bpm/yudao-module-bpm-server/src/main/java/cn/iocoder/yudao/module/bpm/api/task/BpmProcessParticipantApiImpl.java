package cn.iocoder.yudao.module.bpm.api.task;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.framework.security.OaBillAccessPermission;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Primary;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Primary
public class BpmProcessParticipantApiImpl implements BpmProcessParticipantApi {

    @Resource
    private OaBillAccessPermission oaBillAccessPermission;

    @Override
    @PreAuthorize("isAuthenticated()")
    public CommonResult<Boolean> canReadProcess(String processInstanceId) {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (userId == null || TenantContextHolder.getTenantId() == null || StrUtil.isBlank(processInstanceId)) {
            return CommonResult.success(false);
        }
        return CommonResult.success(
                oaBillAccessPermission.isActiveTaskCandidateOrAssignee(processInstanceId, userId)
                        || oaBillAccessPermission.isHistoricTaskAssignee(processInstanceId, userId));
        // task owner 含在上述两个查询的 taskOwner 分支，不是业务 creator
    }
}
