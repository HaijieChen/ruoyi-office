package cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.datasource;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Schema(description = "管理后台 - BPM 表单数据源运行时执行 Request VO")
@Data
public class BpmFormDataSourceExecuteReqVO {

    @Schema(description = "引用当前数据源的表单编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "表单编号不能为空")
    @Positive(message = "表单编号必须大于 0")
    private Long formId;

    @Schema(description = "发起流程时的流程定义编号；与任务编号二选一", example = "oa-seal:1:100")
    @Size(max = 64, message = "流程定义编号长度不能超过 64 个字符")
    private String processDefinitionId;

    @Schema(description = "审批时的当前活动任务编号；与流程定义编号二选一", example = "task-200")
    @Size(max = 64, message = "任务编号长度不能超过 64 个字符")
    private String taskId;

    @Schema(description = "数据源参数")
    private Map<String, Object> params = new LinkedHashMap<>();

}
