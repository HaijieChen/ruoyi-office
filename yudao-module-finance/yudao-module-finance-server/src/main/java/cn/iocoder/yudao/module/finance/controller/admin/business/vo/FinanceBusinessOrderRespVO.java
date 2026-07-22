package cn.iocoder.yudao.module.finance.controller.admin.business.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - 商务单 Response VO")
@Data
public class FinanceBusinessOrderRespVO {

    @Schema(description = "编号")
    private Long id;
    @Schema(description = "商务单号")
    private String orderNo;
    @Schema(description = "业务主体/客户")
    private String businessSubject;
    @Schema(description = "业务类型")
    private String businessType;
    @Schema(description = "合同引用")
    private String contractRef;
    @Schema(description = "项目引用")
    private String projectRef;
    @Schema(description = "应收金额")
    private BigDecimal receivableAmount;
    @Schema(description = "应付金额")
    private BigDecimal payableAmount;
    @Schema(description = "币种")
    private String currency;
    @Schema(description = "负责人编号")
    private Long ownerId;
    @Schema(description = "负责人名称")
    private String ownerName;
    @Schema(description = "状态")
    private Integer status;
    @Schema(description = "备注")
    private String remark;
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}
