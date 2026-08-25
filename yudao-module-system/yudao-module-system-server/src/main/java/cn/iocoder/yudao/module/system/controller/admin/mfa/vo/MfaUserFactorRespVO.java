package cn.iocoder.yudao.module.system.controller.admin.mfa.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 个人 MFA 因子 Response VO")
@Data
public class MfaUserFactorRespVO {
    private String id;
    private String type;
    private String status;
    private String label;
    private String maskedTarget;
}
