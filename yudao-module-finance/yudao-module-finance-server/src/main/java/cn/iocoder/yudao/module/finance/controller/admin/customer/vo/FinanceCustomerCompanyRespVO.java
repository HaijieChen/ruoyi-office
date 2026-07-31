package cn.iocoder.yudao.module.finance.controller.admin.customer.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 客户公司 Response VO")
@Data
public class FinanceCustomerCompanyRespVO {

    @Schema(description = "编号")
    private Long id;

    @Schema(description = "编码")
    private String code;

    @Schema(description = "名称")
    private String name;

    @Schema(description = "纳税人识别号")
    private String taxNo;

    @Schema(description = "开户银行")
    private String bankName;

    @Schema(description = "银行账号")
    private String bankAccount;

    @Schema(description = "邮寄地址")
    private String address;

    @Schema(description = "联系电话")
    private String phone;

    @Schema(description = "联系人")
    private String contactName;

    @Schema(description = "联系邮箱")
    private String email;

    @Schema(description = "类型")
    private String partyType;

    @Schema(description = "状态：0启用 1停用")
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}
