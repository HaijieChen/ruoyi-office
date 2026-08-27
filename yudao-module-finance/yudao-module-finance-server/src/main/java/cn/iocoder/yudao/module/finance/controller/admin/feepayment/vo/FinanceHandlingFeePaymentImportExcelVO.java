package cn.iocoder.yudao.module.finance.controller.admin.feepayment.vo;

import cn.idev.excel.annotation.ExcelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FinanceHandlingFeePaymentImportExcelVO {

    @ExcelProperty("付款日期")
    private LocalDate feeDate;

    @ExcelProperty("金额")
    private BigDecimal amount;

    @ExcelProperty("币种")
    private String currency;

    @ExcelProperty("主体公司名称")
    private String entityCompanyName;

    @ExcelProperty("银行账号")
    private String accountNo;

}
