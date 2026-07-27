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
    private Long operatorId;
    private LocalDateTime actionTime;
    private String reason;
}
