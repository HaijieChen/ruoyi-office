package cn.iocoder.yudao.module.bpm.controller.admin.task.vo.share;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 分享流程实例 Request VO")
@Data
public class BpmProcessInstanceShareReqVO {

    @Schema(description = "流程实例编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    private String processInstanceId;

    @Schema(description = "被分享用户编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty
    private List<Long> recipientUserIds;
}
