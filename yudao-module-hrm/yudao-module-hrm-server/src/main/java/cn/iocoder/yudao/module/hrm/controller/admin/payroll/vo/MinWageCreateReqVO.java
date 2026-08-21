package cn.iocoder.yudao.module.hrm.controller.admin.payroll.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Schema(description = "管理后台 - 最低工资创建")
@Data
public class MinWageCreateReqVO {

    @Schema(description = "金额", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    private BigDecimal amount;

    @Schema(description = "true=下月生效，false=当月生效")
    private Boolean nextMonth;
}
