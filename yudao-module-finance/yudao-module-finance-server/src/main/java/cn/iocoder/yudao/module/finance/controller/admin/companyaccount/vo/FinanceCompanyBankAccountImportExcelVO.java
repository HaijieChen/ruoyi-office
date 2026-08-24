package cn.iocoder.yudao.module.finance.controller.admin.companyaccount.vo;

import cn.idev.excel.annotation.ExcelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FinanceCompanyBankAccountImportExcelVO {

    @ExcelProperty("主体公司")
    private String entityCompanyName;

    @ExcelProperty("账户名称")
    private String accountName;

    @ExcelProperty("开户行")
    private String bankName;

    @ExcelProperty("户名")
    private String accountHolder;

    @ExcelProperty("银行账号")
    private String accountNo;

    @ExcelProperty("账户类型")
    private String accountType;

    @ExcelProperty("币种")
    private String currency;

    @ExcelProperty("状态")
    private String status;

    @ExcelProperty("备注")
    private String remark;
}
