package cn.iocoder.yudao.module.finance.controller.admin.opening.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "管理后台 - 银行期初余额保存 Request VO")
@Data
public class FinanceBankOpeningBalanceSaveReqVO {

    @Schema(description = "公司银行账户编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "公司银行账户不能为空")
    private Long accountId;

    @Schema(description = "期初日期", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "期初日期不能为空")
    private LocalDate asOfDate;

    @Schema(description = "期初金额", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "期初金额不能为空")
    private BigDecimal amount;

    @Schema(description = "币种 CNY/USD/HKD", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "币种不能为空")
    private String currency;

}
