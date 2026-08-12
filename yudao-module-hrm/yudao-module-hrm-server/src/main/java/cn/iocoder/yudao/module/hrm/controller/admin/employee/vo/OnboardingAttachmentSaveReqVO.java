package cn.iocoder.yudao.module.hrm.controller.admin.employee.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 入职资料附件保存 VO（客户端不得自报 businessType/size/url）
 * <ul>
 *   <li>已有附件：传 id（common_attachment 主键）</li>
 *   <li>新附件：传 claimToken（由 /hrm/employee-archive/onboarding-file/upload 返回）</li>
 * </ul>
 */
@Schema(description = "管理后台 - 员工入职资料附件保存 VO")
@Data
public class OnboardingAttachmentSaveReqVO {

    @Schema(description = "附件记录 ID（已绑定到当前员工时传入）", example = "10")
    private Long id;

    @Schema(description = "一次性 claim token（新上传必须）")
    private String claimToken;

    @Schema(description = "排序", example = "1")
    private Integer sortOrder;

    @Schema(description = "备注")
    private String remark;

}
