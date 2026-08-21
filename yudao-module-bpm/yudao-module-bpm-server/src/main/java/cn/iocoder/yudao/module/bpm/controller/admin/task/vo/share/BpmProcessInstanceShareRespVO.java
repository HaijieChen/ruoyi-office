package cn.iocoder.yudao.module.bpm.controller.admin.task.vo.share;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 流程实例分享 Response VO")
@Data
public class BpmProcessInstanceShareRespVO {

    @Schema(description = "编号")
    private Long id;
    @Schema(description = "流程实例编号")
    private String processInstanceId;
    @Schema(description = "流程名")
    private String processInstanceName;
    @Schema(description = "发起人编号")
    private Long startUserId;
    @Schema(description = "被分享人编号")
    private Long recipientUserId;
    @Schema(description = "分享时间")
    private LocalDateTime createTime;
}
