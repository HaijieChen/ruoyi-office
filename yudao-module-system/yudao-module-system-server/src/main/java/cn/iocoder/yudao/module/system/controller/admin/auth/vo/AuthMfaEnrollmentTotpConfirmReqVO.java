package cn.iocoder.yudao.module.system.controller.admin.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Schema(description = "管理后台 - MFA TOTP 绑定确认 Request VO")
@Data
public class AuthMfaEnrollmentTotpConfirmReqVO {

    @Schema(description = "ENROLLMENT flowToken", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    private String flowToken;

    @Schema(description = "PENDING 因子 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    private String factorId;

    @Schema(description = "TOTP 验证码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    private String code;
}
