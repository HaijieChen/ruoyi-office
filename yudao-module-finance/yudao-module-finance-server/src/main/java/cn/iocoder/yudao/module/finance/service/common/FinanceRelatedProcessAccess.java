package cn.iocoder.yudao.module.finance.service.common;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.finance.dal.dataobject.contract.FinanceContractApplicationDO;
import cn.iocoder.yudao.module.finance.framework.rpc.FinanceBpmProcessInstanceApi;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * 关联流程可见范围：发起人或未收回分享。不含部门数据权限。
 */
@Component
public class FinanceRelatedProcessAccess {

    private final FinanceBpmProcessInstanceApi processInstanceApi;

    public FinanceRelatedProcessAccess(FinanceBpmProcessInstanceApi processInstanceApi) {
        this.processInstanceApi = processInstanceApi;
    }

    public boolean canAccessRelated(Long userId, String processInstanceId) {
        if (userId == null || StrUtil.isBlank(processInstanceId)) {
            return false;
        }
        Boolean ok = processInstanceApi.canAccessRelated(userId, processInstanceId.trim()).getCheckedData();
        return Boolean.TRUE.equals(ok);
    }

    public Set<String> listSharedInstanceIds(Long userId) {
        if (userId == null) {
            return Set.of();
        }
        Set<String> ids = processInstanceApi.listSharedInstanceIds(userId).getCheckedData();
        return ids == null ? Set.of() : ids;
    }

    public boolean canAccessContract(Long userId, FinanceContractApplicationDO contract) {
        if (userId == null || contract == null) {
            return false;
        }
        if (Objects.equals(userId, contract.getApplicantUserId())) {
            return true;
        }
        return canAccessRelated(userId, contract.getProcessInstanceId());
    }

    public static Set<String> orEmpty(Set<String> ids) {
        return CollUtil.isEmpty(ids) ? Collections.emptySet() : ids;
    }
}
