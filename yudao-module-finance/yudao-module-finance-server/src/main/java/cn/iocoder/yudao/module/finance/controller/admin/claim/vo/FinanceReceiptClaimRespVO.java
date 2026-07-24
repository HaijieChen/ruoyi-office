package cn.iocoder.yudao.module.finance.controller.admin.claim.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - 到款认领 Response VO")
@Data
public class FinanceReceiptClaimRespVO {

    @Schema(description = "认领单编号")
    private Long id;
    @Schema(description = "认领人编号")
    private Long claimantId;
    @Schema(description = "状态")
    private Integer status;
    @Schema(description = "认领总金额")
    private BigDecimal totalClaimAmount;
    @Schema(description = "说明")
    private String remark;
    @Schema(description = "驳回原因")
    private String rejectReason;
    @Schema(description = "复核人编号")
    private Long reviewerId;
    @Schema(description = "复核时间")
    private LocalDateTime reviewTime;
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
    @Schema(description = "明细")
    private List<Item> items;

    @Data
    public static class Item {

        @Schema(description = "明细编号")
        private Long id;
        @Schema(description = "银行到款编号")
        private Long receiptId;
        @Schema(description = "到款流水号")
        private String receiptNo;
        @Schema(description = "付款方名称")
        private String payerName;
        @Schema(description = "银行流水号")
        private String bankSerialNo;
        @Schema(description = "商务单编号")
        private Long businessOrderId;
        @Schema(description = "商务单号")
        private String businessOrderNo;
        @Schema(description = "产品名称")
        private String productName;
        @Schema(description = "认领金额")
        private BigDecimal claimAmount;

    }

}
