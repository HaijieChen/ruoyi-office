package cn.iocoder.yudao.module.system.controller.admin.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Schema(description = "管理后台 - MFA 发送验证码 Request VO")
@Data
public class AuthMfaCodeSendReqVO {

    @Schema(description = "flowToken", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    private String flowToken;

    @Schema(description = "因子 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    private String factorId;
}
