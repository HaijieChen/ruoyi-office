package cn.iocoder.yudao.module.finance.controller.admin.business.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY;

@Schema(description = "管理后台 - 商务签单分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class FinanceBusinessOrderPageReqVO extends PageParam {

    @Schema(description = "商务签单号")
    private String orderNo;
    @Schema(description = "主体公司组织部门编号")
    private Long entityCompanyDeptId;
    @Schema(description = "合同审批流程编号（legacy）")
    private String contractProcessId;
    @Schema(description = "合同业务单号（正式关联 application_no）")
    private String contractApplicationNo;
    @Schema(description = "产品名称")
    private String productName;
    @Schema(description = "付款方名称")
    private String payerName;
    @Schema(description = "导入人编号")
    private Long importerId;
    @Schema(description = "导入日期范围")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY)
    private LocalDate[] importDate;
    @Schema(description = "下单日期范围")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY)
    private LocalDate[] orderDate;
    @Schema(description = "仅可开余额 > 0 的商务单（settlement - invoiced_occupied > 0）")
    private Boolean onlyOpenable;

}
