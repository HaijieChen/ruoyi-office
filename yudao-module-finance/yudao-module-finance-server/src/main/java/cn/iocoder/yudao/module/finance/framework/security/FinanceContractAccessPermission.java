package cn.iocoder.yudao.module.finance.framework.security;

import cn.iocoder.yudao.module.finance.service.contract.FinanceContractApplicationService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

/**
 * CS-R5 / C30：详情 GET 的 SpEL 辅助。
 * 在无静态 query 时，允许本人或当前 process 上 active 任务候选人/办理人进入接口；
 * 最终范围仍由 {@link FinanceContractApplicationService#getApplication(Long, Long, boolean)} 裁定。
 */
@Component("financeContractAccess")
public class FinanceContractAccessPermission {

    @Resource
    private FinanceContractApplicationService contractApplicationService;

    /**
     * @param id 合同申请主键
     * @return 当前登录用户是否可因「本人 / 任务语境」读详情（不含 FA manageAll，后者走 query）
     */
    public boolean canTaskContextOrOwnerRead(Long id) {
        Long userId = getLoginUserId();
        if (userId == null || id == null) {
            return false;
        }
        return contractApplicationService.canAccessDetail(id, userId);
    }

    public boolean canReadViaAttachingBill(Long id) {
        return canTaskContextOrOwnerRead(id);
    }
}
