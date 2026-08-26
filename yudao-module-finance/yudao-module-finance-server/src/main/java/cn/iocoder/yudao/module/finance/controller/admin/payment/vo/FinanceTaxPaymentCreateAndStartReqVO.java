package cn.iocoder.yudao.module.finance.controller.admin.payment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Schema(description = "税金付款申请创建并启动 Request VO")
@Data
public class FinanceTaxPaymentCreateAndStartReqVO {

    @NotEmpty(message = "支付时效不能为空")
    private String paymentTiming;

    @Schema(description = "税款所属期/申报期", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "税款所属期不能为空")
    private String periodLabel;

    @Schema(description = "币种 CNY/USD/HKD", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "币种不能为空")
    private String currency;

    @Schema(description = "特殊说明")
    private String specialNote;

    @Schema(description = "税务依据附件（建议必传）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "税金依据附件至少一份")
    private List<String> evidenceFileUrls;

    @Valid
    @NotEmpty(message = "税金明细不能为空")
    private List<FinancePaymentTaxLineReqVO> lines;

    @Schema(description = "发起人自选节点审批人")
    private Map<String, List<Long>> startUserSelectAssignees;

    @Schema(description = "发起时选择的任职公司")
    private Long startCompanyDeptId;

}
