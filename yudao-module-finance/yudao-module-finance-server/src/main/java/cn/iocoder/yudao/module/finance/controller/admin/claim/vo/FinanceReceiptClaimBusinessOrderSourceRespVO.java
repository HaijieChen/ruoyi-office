package cn.iocoder.yudao.module.finance.controller.admin.claim.vo;

import cn.iocoder.yudao.module.finance.controller.admin.business.vo.FinanceBusinessOrderRespVO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Schema(description = "管理后台 - 到款认领可选商务签单 Response VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class FinanceReceiptClaimBusinessOrderSourceRespVO extends FinanceBusinessOrderRespVO {

    @Schema(description = "剩余可认领金额")
    public BigDecimal getRemainingClaimableAmount() {
        return getSettlementAmount().subtract(getConfirmedClaimedAmount());
    }

}
