package cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.process;

import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.model.BpmModelMetaInfoVO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - 流程定义 Response VO")
@Data
public class BpmProcessDefinitionRespVO extends BpmModelMetaInfoVO {

    @Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private String id;

    @Schema(description = "版本", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer version;

    @Schema(description = "流程名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "宇擎")
    private String name;

    @Schema(description = "流程标识", requiredMode = Schema.RequiredMode.REQUIRED, example = "youdao")
    private String key;

    @Schema(description = "流程分类", example = "1")
    private String category;
    @Schema(description = "流程分类名字", example = "请假")
    private String categoryName;

    @Schema(description = "流程模型的类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "10")
    private Integer modelType; // 参见 BpmModelTypeEnum 枚举类

    @Schema(description = "流程模型的编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "ABC")
    private String modelId;

    @Schema(description = "表单的配置-JSON 字符串。在表单类型为 {@link BpmModelFormTypeEnum#CUSTOM} 时，必须非空", requiredMode = Schema.RequiredMode.REQUIRED)
    private String formConf;
    @Schema(description = "表单项的数组-JSON 字符串的数组。在表单类型为 {@link BpmModelFormTypeEnum#CUSTOM} 时，必须非空", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<String> formFields;
    @Schema(description = "表单名字", example = "请假表单")
    private String formName;

    @Schema(description = "中断状态-参见 SuspensionState 枚举", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer suspensionState; // 参见 SuspensionState 枚举

    @Schema(description = "部署时间")
    private LocalDateTime deploymentTime; // 需要从对应的 Deployment 读取，非必须返回

    @Schema(description = "BPMN XML")
    private String bpmnXml; // 需要从对应的 BpmnModel 读取，非必须返回

    @Schema(description = "SIMPLE 设计器模型数据 json 格式")
    private String simpleModel; // 非必须返回

    @Schema(description = "流程定义排序", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long sort;

    // ========== 嵌入式流程发起资格（后端权威，前端勿硬编码权限真相） ==========

    @Schema(description = "当前用户是否可发起该流程（列表仅返回 canStart=true；详情/深链用于友好空态）", example = "true")
    private Boolean canStart;

    @Schema(description = "嵌入式流程要求的业务权限标识；非嵌入式为 null", example = "finance:payment-application:create")
    private String requiredStartPermission;

    @Schema(description = "不可发起时的友好原因（直接展示，勿再弹通用 403）", example = "无付款发起权限，请联系管理员分配付款发起角色")
    private String cannotStartReason;

    @Schema(description = "BPMN UserTask 用户任务")
    @Data
    public static class UserTask {

        @Schema(description = "任务标识", requiredMode = Schema.RequiredMode.REQUIRED, example = "sudo")
        private String id;

        @Schema(description = "任务名", requiredMode = Schema.RequiredMode.REQUIRED, example = "王五")
        private String name;

    }

}
