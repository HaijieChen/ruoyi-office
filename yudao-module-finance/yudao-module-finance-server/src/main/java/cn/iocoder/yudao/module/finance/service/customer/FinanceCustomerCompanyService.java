package cn.iocoder.yudao.module.finance.service.customer;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.finance.controller.admin.customer.vo.FinanceCustomerCompanyPageReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.customer.vo.FinanceCustomerCompanySaveReqVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.customer.FinanceCustomerCompanyDO;

import java.util.List;

public interface FinanceCustomerCompanyService {

    Long createCustomerCompany(FinanceCustomerCompanySaveReqVO createReqVO);

    void updateCustomerCompany(FinanceCustomerCompanySaveReqVO updateReqVO);

    void updateStatus(Long id, Integer status);

    FinanceCustomerCompanyDO getCustomerCompany(Long id);

    /**
     * 开票选用：必须存在且启用。
     */
    FinanceCustomerCompanyDO getEnabledCustomerCompany(Long id);

    PageResult<FinanceCustomerCompanyDO> getCustomerCompanyPage(FinanceCustomerCompanyPageReqVO pageReqVO);

    List<FinanceCustomerCompanyDO> getEnabledSimpleList();

    /**
     * 合成开票 buyer 快照字段。
     */
    static String joinNonEmpty(String... parts) {
        if (parts == null || parts.length == 0) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part == null) {
                continue;
            }
            String t = part.trim();
            if (t.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(t);
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    static String composeBuyerAddressPhone(FinanceCustomerCompanyDO company) {
        return joinNonEmpty(company.getAddress(), company.getPhone());
    }

    static String composeBuyerBankAccount(FinanceCustomerCompanyDO company) {
        return joinNonEmpty(company.getBankName(), company.getBankAccount());
    }

}
