package cn.iocoder.yudao.module.hrm.controller.admin.employee.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Schema(description = "管理后台 - 文枢花名册导入 Response VO")
@Data
@Builder
public class EmployeeRosterImportRespVO {

    @Schema(description = "新建成功的员工姓名数组", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<String> createNames;

    @Schema(description = "更新成功的员工姓名数组", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<String> updateNames;

    @Schema(description = "导入失败的行，key 为 Excel 数据行号（含表头偏移），value 为失败原因",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Map<Integer, String> failureRows;

}
