package cn.iocoder.yudao.module.finance.controller.admin.allocation.vo;

import cn.idev.excel.annotation.ExcelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FinanceDeptCostAllocationImportExcelVO {
    @ExcelProperty("期间")
    private String period;
    @ExcelProperty("部门名称")
    private String deptName;
    @ExcelProperty("部门ID")
    private String deptId;
    @ExcelProperty("金额")
    private String amount;
    @ExcelProperty("备注")
    private String remark;
}
