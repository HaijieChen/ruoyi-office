package cn.iocoder.yudao.module.finance.controller.admin.contract.vo;

import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class FinanceContractApplicationExportExcelVO {

    @ExcelProperty("业务单号")
    private String applicationNo;
    @ExcelProperty("审批状态")
    private String approvalStatus;
    @ExcelProperty("对方客商")
    private String counterpartyName;
    @ExcelProperty("签约主体")
    private String entityCompanyName;
    @ExcelProperty("合同类型")
    private String fileType;
    @ExcelProperty("产品类型")
    private String productType;
    @ExcelProperty("合同金额")
    private BigDecimal contractAmount;
    @ExcelProperty("币种")
    private String currency;
    @ExcelProperty("起始日期")
    private LocalDate startDate;
    @ExcelProperty("结束日期")
    private LocalDate endDate;
    @ExcelProperty("文件名称")
    private String fileName;
}
