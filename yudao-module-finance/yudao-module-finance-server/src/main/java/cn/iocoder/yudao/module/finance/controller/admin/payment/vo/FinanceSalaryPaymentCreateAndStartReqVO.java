package cn.iocoder.yudao.module.finance.controller.admin.payment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Schema(description = "薪资付款申请创建并启动 Request VO")
@Data
public class FinanceSalaryPaymentCreateAndStartReqVO {

    @NotEmpty(message = "支付时效不能为空")
    private String paymentTiming;

    @Schema(description = "薪资期间", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "薪资期间不能为空")
    private String periodLabel;

    @Schema(description = "币种 CNY/USD/HKD", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "币种不能为空")
    private String currency;

    @Schema(description = "特殊说明")
    private String specialNote;

    @Schema(description = "依据附件 URL 列表（员工明细附件受控可选；可空）")
    private List<String> evidenceFileUrls;

    @Valid
    @NotEmpty(message = "薪资明细不能为空")
    private List<FinancePaymentSalaryLineReqVO> lines;

    @Schema(description = "发起人自选节点审批人")
    private Map<String, List<Long>> startUserSelectAssignees;

    @Schema(description = "发起时选择的任职公司")
    private Long startCompanyDeptId;

    @Schema(description = "发起时选择的任职部门")
    private Long startDeptId;

}
