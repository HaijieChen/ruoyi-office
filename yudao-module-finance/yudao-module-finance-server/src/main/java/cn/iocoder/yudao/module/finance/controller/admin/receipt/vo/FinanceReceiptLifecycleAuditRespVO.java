package cn.iocoder.yudao.module.finance.controller.admin.receipt.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 银行到款生命周期审计响应")
@Data
public class FinanceReceiptLifecycleAuditRespVO {

    private Long id;
    private Long receiptId;
    private Integer action;
    @Schema(description = "操作人编号", example = "1")
    private Long operatorId;
    @Schema(description = "操作人姓名", example = "系统管理员")
    private String operatorName;
    private LocalDateTime actionTime;
    private String reason;
}
