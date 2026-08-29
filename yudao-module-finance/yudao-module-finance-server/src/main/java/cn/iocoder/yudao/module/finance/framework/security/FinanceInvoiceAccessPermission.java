package cn.iocoder.yudao.module.finance.framework.security;

import cn.iocoder.yudao.module.finance.dal.dataobject.invoice.FinanceInvoiceApplicationDO;
import cn.iocoder.yudao.module.finance.dal.mysql.invoice.FinanceInvoiceApplicationMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Component("financeInvoiceAccess")
public class FinanceInvoiceAccessPermission {

    @Resource
    private FinanceInvoiceApplicationMapper invoiceApplicationMapper;
    @Resource
    private FinanceProcessParticipantSupport processParticipantSupport;

    public boolean canTaskContextOrOwnerRead(Long id) {
        Long userId = getLoginUserId();
        if (userId == null || id == null) {
            return false;
        }
        FinanceInvoiceApplicationDO application = invoiceApplicationMapper.selectById(id);
        if (application == null) {
            return false;
        }
        return processParticipantSupport.canReadBill(
                userId, application.getApplicantUserId(), application.getProcessInstanceId());
    }
}
