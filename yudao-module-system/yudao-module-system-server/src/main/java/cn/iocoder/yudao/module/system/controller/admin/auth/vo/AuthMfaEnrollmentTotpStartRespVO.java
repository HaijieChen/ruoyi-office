package cn.iocoder.yudao.module.system.controller.admin.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "管理后台 - MFA TOTP 绑定开始 Response VO（secret 仅首次返回）")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthMfaEnrollmentTotpStartRespVO {

    @Schema(description = "PENDING 因子 ID")
    private String factorId;

    @Schema(description = "otpauth:// URI")
    private String otpauthUri;

    @Schema(description = "手工录入密钥（仅本响应返回一次）")
    private String secretManual;

    @Schema(description = "绑定用 ENROLLMENT flowToken")
    private String flowToken;
}
