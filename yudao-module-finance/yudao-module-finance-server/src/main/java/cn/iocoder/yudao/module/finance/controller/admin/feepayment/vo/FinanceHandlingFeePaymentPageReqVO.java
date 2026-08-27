package cn.iocoder.yudao.module.finance.controller.admin.feepayment.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY;

@Schema(description = "管理后台 - 手续费付款分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class FinanceHandlingFeePaymentPageReqVO extends PageParam {

    @Schema(description = "付款日期")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY)
    private LocalDate[] feeDate;

    @Schema(description = "主体公司组织部门编号")
    private Long entityCompanyDeptId;

}
