package cn.iocoder.yudao.module.finance.controller.admin.payment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 可引用采购流程实例（付款前置）")
@Data
public class FinancePurchaseInstanceRespVO {

    @Schema(description = "流程实例编号")
    private String processInstanceId;

    @Schema(description = "流程定义 key")
    private String processDefinitionKey;

    @Schema(description = "流程标题/名称")
    private String name;

    @Schema(description = "发起人用户编号")
    private Long startUserId;

    @Schema(description = "发起时间")
    private LocalDateTime startTime;

    @Schema(description = "结束时间")
    private LocalDateTime endTime;

    @Schema(description = "摘要（标题+时间等，供付款快照）")
    private String summary;

}
