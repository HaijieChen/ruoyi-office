package cn.iocoder.yudao.module.finance.controller.admin.customer.vo;

import cn.idev.excel.annotation.ExcelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FinanceCustomerCompanyImportExcelVO {
    @ExcelProperty("名称")
    private String name;
    @ExcelProperty("纳税人识别号")
    private String taxNo;
    @ExcelProperty("是否客户")
    private String isCustomerText;
    @ExcelProperty("是否供应商")
    private String isSupplierText;
    @ExcelProperty("开户银行")
    private String bankName;
    @ExcelProperty("银行账号")
    private String bankAccount;
    @ExcelProperty("邮寄地址")
    private String address;
    @ExcelProperty("联系电话")
    private String phone;
    @ExcelProperty("联系人")
    private String contactName;
    @ExcelProperty("联系邮箱")
    private String email;
}
