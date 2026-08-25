package cn.iocoder.yudao.module.system.controller.admin.mfa.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Schema(description = "管理后台 - 个人 MFA TOTP 确认 Request VO")
@Data
public class MfaUserTotpConfirmReqVO {
    @NotBlank
    private String factorId;
    @NotBlank
    private String code;
}
