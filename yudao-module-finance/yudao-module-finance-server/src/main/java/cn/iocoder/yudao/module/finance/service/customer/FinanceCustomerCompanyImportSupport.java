package cn.iocoder.yudao.module.finance.service.customer;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.finance.controller.admin.customer.vo.FinanceCustomerCompanyImportExcelVO;
import cn.iocoder.yudao.module.finance.controller.admin.customer.vo.FinanceCustomerCompanySaveReqVO;

final class FinanceCustomerCompanyImportSupport {

    private FinanceCustomerCompanyImportSupport() {
    }

    record ParsedRow(FinanceCustomerCompanySaveReqVO saveReq, String taxNo) {
    }

    static String validateAndParse(FinanceCustomerCompanyImportExcelVO row, ParsedRow[] out) {
        if (row == null) {
            return "导入行不能为空";
        }
        if (StrUtil.isBlank(row.getName())) {
            return "名称不能为空";
        }
        if (StrUtil.isBlank(row.getTaxNo())) {
            return "纳税人识别号不能为空";
        }
        Boolean isCustomer;
        Boolean isSupplier;
        try {
            isCustomer = parseOptionalYesNo(row.getIsCustomerText(), true);
            isSupplier = parseOptionalYesNo(row.getIsSupplierText(), false);
        } catch (IllegalArgumentException ex) {
            return ex.getMessage();
        }
        if (!Boolean.TRUE.equals(isCustomer) && !Boolean.TRUE.equals(isSupplier)) {
            return "至少选择客户或供应商角色之一";
        }
        String bankName = trimToNull(row.getBankName());
        String bankAccount = trimToNull(row.getBankAccount());
        if (Boolean.TRUE.equals(isSupplier) && (bankName == null || bankAccount == null)) {
            return "供应商角色启用时开户银行与银行账号不能为空";
        }
        FinanceCustomerCompanySaveReqVO save = new FinanceCustomerCompanySaveReqVO();
        save.setName(row.getName().trim());
        save.setTaxNo(row.getTaxNo().trim());
        save.setIsCustomer(isCustomer);
        save.setIsSupplier(isSupplier);
        save.setBankName(bankName);
        save.setBankAccount(bankAccount);
        save.setAddress(trimToNull(row.getAddress()));
        save.setPhone(trimToNull(row.getPhone()));
        save.setContactName(trimToNull(row.getContactName()));
        save.setEmail(trimToNull(row.getEmail()));
        out[0] = new ParsedRow(save, save.getTaxNo());
        return null;
    }

    static Boolean parseOptionalYesNo(String raw, boolean defaultValue) {
        if (StrUtil.isBlank(raw)) {
            return defaultValue;
        }
        String v = raw.trim();
        if ("是".equals(v)) {
            return true;
        }
        if ("否".equals(v)) {
            return false;
        }
        throw new IllegalArgumentException("是否客户/是否供应商只允许填写是或否");
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }
}
