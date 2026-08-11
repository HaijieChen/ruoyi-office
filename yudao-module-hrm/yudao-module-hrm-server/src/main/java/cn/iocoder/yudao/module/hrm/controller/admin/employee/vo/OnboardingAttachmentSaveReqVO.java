package cn.iocoder.yudao.module.hrm.controller.admin.employee.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 入职资料附件保存 VO（客户端不得自报 businessType/businessId/size/type）
 * <ul>
 *   <li>已有附件：传 id（common_attachment 主键）</li>
 *   <li>新附件：传 fileId（infra_file 权威 claim，由 /infra/file/upload-detail 返回）</li>
 * </ul>
 */
@Schema(description = "管理后台 - 员工入职资料附件保存 VO")
@Data
public class OnboardingAttachmentSaveReqVO {

    @Schema(description = "附件记录 ID（已绑定到当前员工时传入）", example = "10")
    private Long id;

    @Schema(description = "文件服务权威 ID（新上传必须）", example = "1024")
    private Long fileId;

    @Schema(description = "排序", example = "1")
    private Integer sortOrder;

    @Schema(description = "备注")
    private String remark;

}
