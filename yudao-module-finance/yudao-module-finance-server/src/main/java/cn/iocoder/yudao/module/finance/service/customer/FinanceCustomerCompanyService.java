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
     * 开票/合同对方：必须存在、启用、且含客户角色。
     */
    FinanceCustomerCompanyDO getEnabledCustomerCompany(Long id);

    /**
     * 付款收款方：必须存在、启用、含供应商角色、银行齐全。
     */
    FinanceCustomerCompanyDO getEnabledSupplierCompany(Long id);

    PageResult<FinanceCustomerCompanyDO> getCustomerCompanyPage(FinanceCustomerCompanyPageReqVO pageReqVO);

    /**
     * 默认客户角色精简列表（开票/合同兼容）。
     */
    List<FinanceCustomerCompanyDO> getEnabledSimpleList();

    /**
     * @param role CUSTOMER（默认）或 SUPPLIER（供应商且银行齐全）
     */
    List<FinanceCustomerCompanyDO> getEnabledSimpleList(String role);

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
