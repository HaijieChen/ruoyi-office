package cn.iocoder.yudao.module.finance.controller.admin.contract.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - 合同签约 resubmit Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class FinanceContractApplicationResubmitReqVO extends FinanceContractApplicationCreateAndStartReqVO {
}
