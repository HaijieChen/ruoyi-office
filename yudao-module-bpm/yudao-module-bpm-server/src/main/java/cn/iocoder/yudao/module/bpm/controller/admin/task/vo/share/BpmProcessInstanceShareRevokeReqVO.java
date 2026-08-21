package cn.iocoder.yudao.module.bpm.controller.admin.task.vo.share;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 收回流程实例分享 Request VO")
@Data
public class BpmProcessInstanceShareRevokeReqVO {

    @Schema(description = "流程实例编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    private String processInstanceId;

    @Schema(description = "被收回的用户编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    private Long recipientUserId;
}
