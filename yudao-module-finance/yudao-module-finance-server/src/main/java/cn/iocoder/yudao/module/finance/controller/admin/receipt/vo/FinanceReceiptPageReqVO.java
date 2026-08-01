package cn.iocoder.yudao.module.finance.controller.admin.receipt.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY;
import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@Schema(description = "管理后台 - 银行到款分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class FinanceReceiptPageReqVO extends PageParam {

    @Schema(description = "到款流水号")
    private String receiptNo;

    @Schema(description = "银行账户")
    private String bankAccount;

    @Schema(description = "主体公司组织部门编号")
    private Long entityCompanyDeptId;

    @Schema(description = "交易日期")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] transactionDate;

    @Schema(description = "付款方名称")
    private String payerName;

    @Schema(description = "付款方账号")
    private String payerAccount;

    @Schema(description = "银行流水号")
    private String bankSerialNo;

    @Schema(description = "导入日期")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY)
    private LocalDate[] importDate;

    @Schema(description = "认领状态（0-待认领，1-部分认领，2-完全认领，3-已关闭）")
    private Integer claimStatus;

}
