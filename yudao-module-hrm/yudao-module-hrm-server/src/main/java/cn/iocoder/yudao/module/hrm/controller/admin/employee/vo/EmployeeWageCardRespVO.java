package cn.iocoder.yudao.module.hrm.controller.admin.employee.vo;

import cn.iocoder.yudao.module.hrm.dal.dataobject.employee.EmployeeDO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 员工工资卡（发起报销预填）")
@Data
public class EmployeeWageCardRespVO {

    @Schema(description = "档案姓名，作收款户名")
    private String name = "";

    @Schema(description = "工资开户行")
    private String bankName = "";

    @Schema(description = "工资卡账户")
    private String bankAccount = "";

    public static EmployeeWageCardRespVO fromArchive(EmployeeDO row) {
        EmployeeWageCardRespVO vo = new EmployeeWageCardRespVO();
        if (row == null) {
            return vo;
        }
        if (row.getName() != null) {
            vo.setName(row.getName());
        }
        if (row.getBankName() != null) {
            vo.setBankName(row.getBankName());
        }
        if (row.getBankAccount() != null) {
            vo.setBankAccount(row.getBankAccount());
        }
        return vo;
    }
}
