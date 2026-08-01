package cn.iocoder.yudao.module.finance.controller.admin.business.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "管理后台 - 商务签单新增/修改 Request VO")
@Data
public class FinanceBusinessOrderSaveReqVO {

    @Schema(description = "编号")
    private Long id;

    @Schema(description = "主体公司组织部门编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "主体公司不能为空")
    private Long entityCompanyDeptId;

    @Schema(description = "合同审批流程编号（legacy 脏文本，新写勿仅填此项）")
    private String contractProcessId;

    @Schema(description = "合同签约申请编号（正式关联，新建必填）")
    private Long contractApplicationId;

    @Schema(description = "下单日期", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "下单日期不能为空")
    private LocalDate orderDate;

    @Schema(description = "产品名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "产品名称不能为空")
    private String productName;

    @Schema(description = "对接人", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "对接人不能为空")
    private String contactPerson;

    @Schema(description = "执行开始日", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "执行开始日不能为空")
    private LocalDate executionStartDate;

    @Schema(description = "执行截止日", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "执行截止日不能为空")
    private LocalDate executionEndDate;

    @Schema(description = "付款方名称")
    private String payerName;

    @Schema(description = "签单执行金额", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "签单执行金额不能为空")
    @DecimalMin(value = "0", inclusive = false, message = "签单执行金额必须大于 0")
    private BigDecimal signedExecutionAmount;

    @Schema(description = "折扣率；留空按 0 处理")
    @DecimalMin(value = "0", message = "折扣率不能小于 0")
    @DecimalMax(value = "1", message = "折扣率不能大于 1")
    private BigDecimal discountRate;

    @Schema(description = "备注")
    private String remark;

}
