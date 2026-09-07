package cn.iocoder.yudao.module.hrm.framework.security;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.exception.ErrorCode;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.datapermission.core.util.DataPermissionUtils;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessParticipantApi;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.function.Function;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

@Component
public class HrmProcessBillReadSupport {

    @Resource
    private BpmProcessParticipantApi processParticipantApi;
    @Resource
    private SecurityFrameworkService securityFrameworkService;

    public <T> T loadForRead(Long id, String queryPermission,
                             Function<Long, T> selectById,
                             Function<T, String> creatorFn,
                             Function<T, String> processInstanceIdFn,
                             ErrorCode notExists,
                             ErrorCode denied) {
        T scoped = selectById.apply(id);
        if (scoped != null) {
            if (securityFrameworkService.hasPermission(queryPermission)
                    || isOwnerOrParticipant(creatorFn.apply(scoped), processInstanceIdFn.apply(scoped))) {
                return scoped;
            }
            throw exception(denied);
        }
        T bill = DataPermissionUtils.executeIgnore(() -> selectById.apply(id));
        if (bill == null) {
            throw exception(notExists);
        }
        if (isOwnerOrParticipant(creatorFn.apply(bill), processInstanceIdFn.apply(bill))) {
            return bill;
        }
        throw exception(denied);
    }

    public boolean isOwnerOrParticipant(String creator, String processInstanceId) {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (userId == null) {
            return false;
        }
        if (Objects.equals(String.valueOf(userId), creator)) {
            return true;
        }
        if (StrUtil.isBlank(processInstanceId)) {
            return false;
        }
        CommonResult<Boolean> result = processParticipantApi.canReadProcess(processInstanceId);
        return Boolean.TRUE.equals(result.getCheckedData());
    }
}
