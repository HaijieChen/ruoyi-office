package cn.iocoder.yudao.module.finance.controller.admin.customer.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Schema(description = "管理后台 - 客户公司创建/更新 Request VO")
@Data
public class FinanceCustomerCompanySaveReqVO {

    @Schema(description = "编号（更新时必填）")
    private Long id;

    @Schema(description = "客户公司名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "客户公司名称不能为空")
    private String name;

    @Schema(description = "纳税人识别号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "纳税人识别号不能为空")
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

    @Schema(description = "是否客户角色；创建未传默认 true（仅客户）")
    private Boolean isCustomer;

    @Schema(description = "是否供应商角色；创建未传默认 false")
    private Boolean isSupplier;

    @Schema(description = "状态：0启用 1停用；创建默认 0")
    private Integer status;

}
