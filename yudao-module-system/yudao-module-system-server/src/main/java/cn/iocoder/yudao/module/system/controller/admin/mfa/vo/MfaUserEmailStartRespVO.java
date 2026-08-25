package cn.iocoder.yudao.module.system.controller.admin.mfa.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 个人邮箱 MFA 开始绑定 Response VO")
@Data
public class MfaUserEmailStartRespVO {
    private String factorId;
    private String maskedEmail;
}
