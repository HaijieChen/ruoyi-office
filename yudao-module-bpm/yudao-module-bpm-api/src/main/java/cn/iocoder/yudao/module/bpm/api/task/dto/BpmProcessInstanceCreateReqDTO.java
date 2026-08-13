package cn.iocoder.yudao.module.bpm.api.task.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.Map;

@Schema(description = "RPC 服务 - 流程实例的创建 Request DTO")
@Data
public class BpmProcessInstanceCreateReqDTO {

    @Schema(description = "流程定义的标识", requiredMode = Schema.RequiredMode.REQUIRED, example = "leave")
    @NotEmpty(message = "流程定义的标识不能为空")
    private String processDefinitionKey;

    @Schema(description = "变量实例", requiredMode = Schema.RequiredMode.REQUIRED)
    private Map<String, Object> variables;

    @Schema(description = "业务的唯一标识", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "业务的唯一标识不能为空")
    private String businessKey; // 例如说，请假申请的编号。通过它，可以查询到对应的实例

    /**
     * 发起人自选审批人 Map
     *
     * key：taskKey 任务编码
     * value：审批人的数组
     * 例如：{ taskKey1 :[1, 2] }，则表示 taskKey1 这个任务，提前设定了，由 userId 为 1,2 的用户进行审批
     */
    @Schema(description = "发起人自选审批人 Map")
    private Map<String, List<Long>> startUserSelectAssignees;

    /**
     * 任务审批信息
     */
    @Schema(description = "任务审批信息")
    private BpmTaskApproveReqDTO taskApproveReqDTO;

    /**
     * 可信业务通道标记（内部 RPC 专用）。
     * <p>
     * 仅 Finance 等领域服务经独立菜单 API 发起薪资/税金等「菜单专属」流程时置 true，
     * 以绕过通用 BPM 发起目录/直启上的 hide+deny；通用 HTTP create 不得设置。
     * null/false 一律按通用通道处理。
     */
    @Schema(description = "可信业务通道（内部领域服务启动；通用入口禁止）", hidden = true)
    private Boolean trustedBusinessStart;

}
