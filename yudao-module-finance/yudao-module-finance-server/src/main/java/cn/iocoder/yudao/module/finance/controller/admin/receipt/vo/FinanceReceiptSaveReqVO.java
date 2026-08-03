package cn.iocoder.yudao.module.finance.controller.admin.receipt.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@Schema(description = "管理后台 - 银行到款新增/修改 Request VO")
@Data
public class FinanceReceiptSaveReqVO {

    @Schema(description = "编号，修改时必填")
    private Long id;

    @Schema(description = "主体公司组织部门编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "主体公司不能为空")
    private Long entityCompanyDeptId;

    @Schema(description = "银行账户", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "银行账户不能为空")
    private String bankAccount;

    @Schema(description = "交易日期", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "交易日期不能为空")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime transactionDate;

    @Schema(description = "付款方名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "付款方名称不能为空")
    private String payerName;

    @Schema(description = "付款方账号")
    private String payerAccount;

    @Schema(description = "交易金额", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "交易金额不能为空")
    @DecimalMin(value = "0.01", message = "交易金额必须大于 0")
    private BigDecimal transactionAmount;

    @Schema(description = "摘要/附言")
    private String summary;

    @Schema(description = "银行流水号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "银行流水号不能为空")
    private String bankSerialNo;

    @Schema(description = "是否业务款", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "是否业务款不能为空")
    private Boolean businessFund;

    @Schema(description = "款项类型备注（字典 finance_fund_type_remark，如利息收入/往来款项）")
    @Size(max = 255, message = "款项类型备注长度不能超过 255")
    private String fundTypeRemark;

}
