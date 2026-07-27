package cn.iocoder.yudao.module.finance.controller.admin.claim.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@Schema(description = "管理后台 - 到款认领分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class FinanceReceiptClaimPageReqVO extends PageParam {

    @Schema(description = "状态")
    private Integer status;

    @Schema(description = "认领人编号，复核分页可选")
    private Long claimantId;

    @Schema(description = "创建时间范围")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;

}
