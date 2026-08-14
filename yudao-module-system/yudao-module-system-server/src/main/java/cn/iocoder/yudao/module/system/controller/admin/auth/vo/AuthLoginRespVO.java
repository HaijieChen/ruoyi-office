package cn.iocoder.yudao.module.system.controller.admin.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 登录 Response VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthLoginRespVO {

    @Schema(description = "用户编号", example = "1024")
    private Long userId;

    @Schema(description = "登录状态：AUTHENTICATED / MFA_REQUIRED / MFA_ENROLLMENT_REQUIRED 等。"
            + "缺省或 AUTHENTICATED 时与历史兼容（返回 access/refresh token）",
            example = "AUTHENTICATED")
    private String loginStatus;

    @Schema(description = "访问令牌（仅 AUTHENTICATED）", example = "happy")
    private String accessToken;

    @Schema(description = "刷新令牌（仅 AUTHENTICATED）", example = "nice")
    private String refreshToken;

    @Schema(description = "过期时间（access token 或 challenge）")
    private LocalDateTime expiresTime;

    @Schema(description = "MFA/绑定挑战句柄（非业务 Bearer）")
    private String challengeToken;

    @Schema(description = "挑战过期秒数", example = "300")
    private Integer expiresIn;

}
