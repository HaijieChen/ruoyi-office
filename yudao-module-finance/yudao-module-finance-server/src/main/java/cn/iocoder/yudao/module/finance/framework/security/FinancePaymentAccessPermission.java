package cn.iocoder.yudao.module.finance.framework.security;

import cn.iocoder.yudao.module.finance.service.payment.FinancePaymentApplicationService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

/**
 * F3：详情 GET 允许本人或当前 process active 任务候选人/办理人（对齐 financeContractAccess）。
 */
@Component("financePaymentAccess")
public class FinancePaymentAccessPermission {

    @Resource
    private FinancePaymentApplicationService paymentApplicationService;

    public boolean canTaskContextOrOwnerRead(Long id) {
        Long userId = getLoginUserId();
        if (userId == null || id == null) {
            return false;
        }
        return paymentApplicationService.canAccessDetail(id, userId);
    }
}
