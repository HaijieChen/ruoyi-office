package cn.iocoder.yudao.module.finance.controller.admin.invoice.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 开票申请 updateIssueProgress Request VO（一行一票）")
@Data
public class FinanceInvoiceApplicationUpdateIssueProgressReqVO {

    @Schema(description = "开票申请编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "开票申请编号不能为空")
    private Long applicationId;

    @Schema(description = "明细行编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "明细行编号不能为空")
    private Long lineId;

    @Schema(description = "发票号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "发票号不能为空")
    private String invoiceNo;

    @Schema(description = "发票附件 URL")
    private String fileUrl;

    @Schema(description = "开票时间；为空时服务端取当前时间")
    private LocalDateTime issuedAt;

}
