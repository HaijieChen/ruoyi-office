package cn.iocoder.yudao.module.finance.controller.admin.business.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;

@Schema(description = "管理后台 - 商务单新增/修改 Request VO")
@Data
public class FinanceBusinessOrderSaveReqVO {

    @Schema(description = "编号")
    private Long id;

    @Schema(description = "商务单号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "商务单号不能为空")
    private String orderNo;

    @Schema(description = "业务主体/客户", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "业务主体不能为空")
    private String businessSubject;

    @Schema(description = "业务类型", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "业务类型不能为空")
    private String businessType;

    @Schema(description = "合同引用")
    private String contractRef;

    @Schema(description = "项目引用")
    private String projectRef;

    @Schema(description = "应收金额", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "应收金额不能为空")
    @DecimalMin(value = "0.00", message = "应收金额不能为负数")
    private BigDecimal receivableAmount;

    @Schema(description = "应付金额", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "应付金额不能为空")
    @DecimalMin(value = "0.00", message = "应付金额不能为负数")
    private BigDecimal payableAmount;

    @Schema(description = "币种", requiredMode = Schema.RequiredMode.REQUIRED, example = "CNY")
    @NotBlank(message = "币种不能为空")
    @Pattern(regexp = "^[A-Z]{3}$", message = "币种必须为 3 位大写字母")
    private String currency;

    @Schema(description = "状态", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    @NotNull(message = "状态不能为空")
    private Integer status;

    @Schema(description = "备注")
    private String remark;

}
