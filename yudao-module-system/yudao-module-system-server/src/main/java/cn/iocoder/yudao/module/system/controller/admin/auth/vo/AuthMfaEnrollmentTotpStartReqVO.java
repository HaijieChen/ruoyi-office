package cn.iocoder.yudao.module.system.controller.admin.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Schema(description = "管理后台 - MFA TOTP 绑定开始 Request VO")
@Data
public class AuthMfaEnrollmentTotpStartReqVO {

    @Schema(description = "ENROLLMENT flowToken", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    private String flowToken;
}
