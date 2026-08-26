package cn.iocoder.yudao.module.finance.controller.admin.invoice.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Schema(description = "管理后台 - 开票红冲 createAndStart")
@Data
public class FinanceInvoiceRedflushCreateAndStartReqVO {

    @NotNull(message = "必须选择前置开票申请")
    private Long predecessorApplicationId;

    @NotBlank(message = "红冲原因不能为空")
    private String reason;

    private String specialNote;

    @Schema(description = "客户端金额；若传入必须与原单一致")
    private BigDecimal totalAmount;

    private Map<String, List<Long>> startUserSelectAssignees;

    private Long startCompanyDeptId;
}
