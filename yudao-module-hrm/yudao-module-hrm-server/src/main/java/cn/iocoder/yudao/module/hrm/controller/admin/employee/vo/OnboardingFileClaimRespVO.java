package cn.iocoder.yudao.module.hrm.controller.admin.employee.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 入职资料上传 claim 响应：不返回可公开直链 URL/path/configId。
 */
@Schema(description = "入职资料上传 claim 响应")
@Data
public class OnboardingFileClaimRespVO {

    @Schema(description = "一次性 claim token", requiredMode = Schema.RequiredMode.REQUIRED)
    private String claimToken;

    @Schema(description = "文件名")
    private String fileName;

    @Schema(description = "文件大小（字节）")
    private Long fileSize;

    @Schema(description = "扩展名")
    private String fileExtension;

    @Schema(description = "过期时间（毫秒时间戳）")
    private Long expireTime;

}
