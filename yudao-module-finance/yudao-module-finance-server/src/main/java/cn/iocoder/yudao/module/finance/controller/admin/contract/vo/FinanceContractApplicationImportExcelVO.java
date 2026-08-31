package cn.iocoder.yudao.module.finance.controller.admin.contract.vo;

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
public class FinanceContractApplicationImportExcelVO {
    @ExcelProperty("合同业务单号")
    private String applicationNo;
    @ExcelProperty("申请人账号")
    private String applicantUsername;
    @ExcelProperty("签约主体")
    private String entityCompanyName;
    @ExcelProperty("对方客商")
    private String counterpartyName;
    @ExcelProperty("合同类型")
    private String fileType;
    @ExcelProperty("产品类型")
    private String productType;
    @ExcelProperty("金额是否适用")
    private String amountApplicableText;
    @ExcelProperty("合同金额")
    private BigDecimal contractAmount;
    @ExcelProperty("返点比例")
    private String rebateRatio;
    @ExcelProperty("结算方式")
    private String settlementMethod;
    @ExcelProperty("文件名称")
    private String fileName;
    @ExcelProperty("起始日期")
    private LocalDate startDate;
    @ExcelProperty("结束日期")
    private LocalDate endDate;
}
