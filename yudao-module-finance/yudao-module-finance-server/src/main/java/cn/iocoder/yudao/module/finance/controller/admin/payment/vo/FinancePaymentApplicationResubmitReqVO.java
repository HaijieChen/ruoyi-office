package cn.iocoder.yudao.module.finance.controller.admin.payment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 付款申请 resubmit Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class FinancePaymentApplicationResubmitReqVO extends FinancePaymentApplicationCreateAndStartReqVO {
}
