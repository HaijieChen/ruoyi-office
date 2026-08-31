package cn.iocoder.yudao.module.finance.controller.admin.invoice.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "管理后台 - 开票申请整单办票 complete-issue Request VO")
@Data
public class FinanceInvoiceApplicationCompleteIssueReqVO {

    @Schema(description = "开票申请编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "开票申请编号不能为空")
    private Long applicationId;

    @Schema(description = "本次办票发票（追加，不覆盖历史）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "至少上传一个发票附件")
    @Valid
    private List<FileItem> files;

    @Data
    public static class FileItem {

        @Schema(description = "附件 URL", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty(message = "附件 URL 不能为空")
        private String url;

        @Schema(description = "文件名")
        private String name;

        @Schema(description = "发票金额（开票办票必填；红冲可不填）")
        private BigDecimal amount;

        @Schema(description = "发票号")
        private String invoiceNo;

        @Schema(description = "开票日期")
        private LocalDate invoiceDate;

    }

}
