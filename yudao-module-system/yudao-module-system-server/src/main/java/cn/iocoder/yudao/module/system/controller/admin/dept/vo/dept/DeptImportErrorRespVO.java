package cn.iocoder.yudao.module.system.controller.admin.dept.vo.dept;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "管理后台 - 组织导入行级错误")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeptImportErrorRespVO {

    @Schema(description = "Excel 行号（含表头，数据从第 2 行起）", example = "3")
    private Integer rowNumber;

    @Schema(description = "组织完整路径", example = "文枢科技/研发中心")
    private String orgPath;

    @Schema(description = "出错字段", example = "functionalCurrency")
    private String field;

    @Schema(description = "错误码", example = "CURRENCY_REQUIRED")
    private String code;

    @Schema(description = "错误说明", example = "公司节点必须填写记账本位币")
    private String message;

}
