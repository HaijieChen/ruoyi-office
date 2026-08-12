package cn.iocoder.yudao.module.hrm.controller.admin.employee.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 入职资料附件响应；下载请走鉴权接口，不直接暴露公开 file URL。
 */
@Schema(description = "管理后台 - 员工入职资料附件 Response VO")
@Data
public class OnboardingAttachmentRespVO {

    @Schema(description = "附件记录 ID")
    private Long id;

    @Schema(description = "文件名")
    private String fileName;

    @Schema(description = "文件大小")
    private Long fileSize;

    @Schema(description = "扩展名")
    private String fileExtension;

    @Schema(description = "MIME")
    private String fileType;

    @Schema(description = "排序")
    private Integer sortOrder;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "上传时间")
    private LocalDateTime uploadTime;

    @Schema(description = "鉴权下载相对路径（前端拼 admin-api 前缀）",
            example = "/hrm/employee-archive/onboarding-attachment/download?employeeId=1&attachmentId=2")
    private String downloadPath;

}
