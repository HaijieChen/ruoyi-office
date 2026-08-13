package cn.iocoder.yudao.module.system.controller.admin.dept.vo.dept;

import cn.idev.excel.annotation.ExcelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 组织架构 Excel 导入行（表头与模板「组织架构」sheet 一致）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeptImportExcelVO {

    @ExcelProperty("组织名称*")
    private String name;

    @ExcelProperty("上级组织路径")
    private String parentPath;

    @ExcelProperty("组织类型*")
    private String orgTypeLabel;

    @ExcelProperty("显示顺序*")
    private String sortText;

    @ExcelProperty("状态*")
    private String statusLabel;

    @ExcelProperty("记账本位币")
    private String functionalCurrency;

    @ExcelProperty("负责人登录账号")
    private String leaderUsername;

    @ExcelProperty("联系电话")
    private String phone;

    @ExcelProperty("邮箱")
    private String email;

}
