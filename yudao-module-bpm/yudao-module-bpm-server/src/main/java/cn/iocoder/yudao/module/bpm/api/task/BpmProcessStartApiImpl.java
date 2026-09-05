package cn.iocoder.yudao.module.bpm.api.task;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmProcessDefinitionInfoDO;
import cn.iocoder.yudao.module.bpm.service.definition.BpmProcessDefinitionService;
import jakarta.annotation.Resource;
import org.flowable.engine.repository.ProcessDefinition;
import org.springframework.context.annotation.Primary;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

/** 不改变历史隐藏流程语义，只对统一新单入口实行 visible + starter 规则。 */
@RestController
@Primary
public class BpmProcessStartApiImpl implements BpmProcessStartApi {
    @Resource
    private BpmProcessDefinitionService processDefinitionService;

    @Override
    @PreAuthorize("isAuthenticated()")
    public CommonResult<Boolean> validateStart(String processDefinitionKey) {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (userId == null || TenantContextHolder.getTenantId() == null) {
            throw new AccessDeniedException("缺少发起人或租户上下文");
        }
        // 此查询由流程定义服务按当前租户限定，并仅返回 active 定义。
        ProcessDefinition definition = processDefinitionService.getActiveProcessDefinition(processDefinitionKey);
        if (definition == null || definition.isSuspended()) {
            throw new AccessDeniedException("流程未发布或已停用");
        }
        BpmProcessDefinitionInfoDO info = processDefinitionService.getProcessDefinitionInfo(definition.getId());
        if (info == null || !Boolean.TRUE.equals(info.getVisible())
                || !processDefinitionService.canUserStartProcessDefinition(info, userId)) {
            throw new AccessDeniedException("流程不可见或不在可发起范围内");
        }
        return CommonResult.success(true);
    }
}
