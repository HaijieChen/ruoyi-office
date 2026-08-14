package cn.iocoder.yudao.module.system.controller.admin.auth.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理后台登录响应 — ADR-MFA-v3 §7 canonical 判别联合。
 * <p>
 * AUTHENTICATED：仅 access/refresh/expires；中间态：仅嵌套 {@link #flow}，无旧别名。
 */
@Schema(description = "管理后台 - 登录 Response VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthLoginRespVO {

    @Schema(description = "用户编号", example = "1024")
    private Long userId;

    @Schema(description = "登录状态：AUTHENTICATED / MFA_REQUIRED / MFA_ENROLLMENT_REQUIRED / MFA_RECOVERY_REQUIRED",
            example = "AUTHENTICATED")
    private String loginStatus;

    @Schema(description = "访问令牌（仅 AUTHENTICATED）", example = "happy")
    private String accessToken;

    @Schema(description = "刷新令牌（仅 AUTHENTICATED）", example = "nice")
    private String refreshToken;

    @Schema(description = "access token 过期时间（仅 AUTHENTICATED）")
    private LocalDateTime expiresTime;

    /**
     * 中间态唯一 flow 载荷（canonical nested union）。
     */
    @Schema(description = "MFA 中间态 flow 载荷（仅 MFA_* 状态）")
    private FlowPayload flow;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FlowPayload {
        @Schema(description = "opaque flowToken", example = "dGhpcy1pcy1ub3QtYS1yZWFsLXRva2Vu")
        private String flowToken;
        @Schema(description = "PRE_AUTH / ENROLLMENT / RECOVERY", example = "PRE_AUTH")
        private String tokenClass;
        @Schema(description = "过期秒数", example = "300")
        private Integer expiresIn;
        @Schema(description = "允许动作")
        private List<String> allowedActions;
        @Schema(description = "可用因子")
        private List<FactorRef> factors;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class FactorRef {
        private String id;
        private String type;
        private String label;
        private String maskedTarget;
    }
}
