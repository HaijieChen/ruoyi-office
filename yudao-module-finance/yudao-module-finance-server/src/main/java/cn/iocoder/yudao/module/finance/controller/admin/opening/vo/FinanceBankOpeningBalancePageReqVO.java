package cn.iocoder.yudao.module.finance.controller.admin.opening.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 银行期初余额分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class FinanceBankOpeningBalancePageReqVO extends PageParam {

    @Schema(description = "公司银行账户编号")
    private Long accountId;

    @Schema(description = "币种")
    private String currency;

}
