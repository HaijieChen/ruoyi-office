package cn.iocoder.yudao.module.finance.service.companyaccount;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.finance.controller.admin.companyaccount.vo.FinanceCompanyBankAccountPageReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.companyaccount.vo.FinanceCompanyBankAccountSaveReqVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.companyaccount.FinanceCompanyBankAccountDO;

import java.util.List;

public interface FinanceCompanyBankAccountService {

    Long create(FinanceCompanyBankAccountSaveReqVO reqVO);

    void update(FinanceCompanyBankAccountSaveReqVO reqVO);

    void updateStatus(Long id, Integer status);

    FinanceCompanyBankAccountDO get(Long id);

    /**
     * 支付用：账户须启用，且归属指定主体公司。
     */
    FinanceCompanyBankAccountDO requireEnabledForEntityCompany(Long accountId, Long entityCompanyDeptId);

    PageResult<FinanceCompanyBankAccountDO> getPage(FinanceCompanyBankAccountPageReqVO reqVO);

    /**
     * 出纳选择：指定主体公司下启用账户。
     */
    List<FinanceCompanyBankAccountDO> listEnabledByEntityCompany(Long entityCompanyDeptId);

    /** 账号脱敏展示，如 ****1234 */
    static String maskAccountNo(String accountNo) {
        if (accountNo == null || accountNo.isBlank()) {
            return "";
        }
        String t = accountNo.trim();
        if (t.length() <= 4) {
            return "****" + t;
        }
        return "****" + t.substring(t.length() - 4);
    }

}
