package cn.iocoder.yudao.module.finance.controller.admin.feepayment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY;

@Schema(description = "管理后台 - 手续费付款保存 Request VO")
@Data
public class FinanceHandlingFeePaymentSaveReqVO {

    @Schema(description = "编号（更新时必填）")
    private Long id;

    @Schema(description = "付款日期", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "付款日期不能为空")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY)
    private LocalDate feeDate;

    @Schema(description = "金额", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "金额不能为空")
    @DecimalMin(value = "0.01", message = "手续费金额必须大于 0")
    private BigDecimal amount;

    @Schema(description = "币种 CNY/USD/HKD", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "币种不能为空")
    private String currency;

    @Schema(description = "主体公司组织部门编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "主体公司不能为空")
    private Long entityCompanyDeptId;

    @Schema(description = "公司银行账户编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "公司银行账户不能为空")
    private Long companyBankAccountId;

}
