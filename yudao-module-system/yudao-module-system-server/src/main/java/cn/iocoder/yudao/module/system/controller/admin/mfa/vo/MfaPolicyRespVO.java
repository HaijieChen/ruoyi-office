package cn.iocoder.yudao.module.system.controller.admin.mfa.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Schema(description = "管理后台 - MFA 全局策略 Response VO")
@Data
public class MfaPolicyRespVO {

    @Schema(description = "生命周期：UNINITIALIZED/ARMED/DEGRADED_CLOSED")
    private String lifecycleState;

    @Schema(description = "全局模式：OFF/OPTIONAL/REQUIRED")
    private String mode;

    @Schema(description = "允许因子")
    private List<String> allowedFactors = new ArrayList<>();

    @Schema(description = "策略是否可用")
    private Boolean usable;

    @Schema(description = "不可用原因")
    private String unusableReason;

    @Schema(description = "全局策略 epoch")
    private Long globalPolicyEpoch;

    @Schema(description = "最低可接受 epoch")
    private Long globalMinAcceptedEpoch;
}
