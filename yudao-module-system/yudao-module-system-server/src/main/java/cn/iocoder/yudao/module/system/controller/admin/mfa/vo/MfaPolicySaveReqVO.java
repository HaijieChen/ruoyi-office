package cn.iocoder.yudao.module.system.controller.admin.mfa.vo;

import cn.iocoder.yudao.module.system.service.mfa.enums.MfaMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.LinkedHashSet;
import java.util.Set;

@Schema(description = "管理后台 - MFA 全局策略更新 Request VO")
@Data
public class MfaPolicySaveReqVO {

    @Schema(description = "全局模式：OFF/OPTIONAL/REQUIRED", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "模式不能为空")
    private MfaMode mode;

    @Schema(description = "允许因子，空表示不限制")
    private Set<String> allowedFactors = new LinkedHashSet<>();

    @AssertTrue(message = "全局策略不能为 INHERIT")
    public boolean isGlobalModeValid() {
        return mode != MfaMode.INHERIT;
    }
}
