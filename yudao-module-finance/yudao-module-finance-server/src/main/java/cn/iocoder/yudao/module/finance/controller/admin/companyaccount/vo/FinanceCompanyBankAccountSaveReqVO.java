package cn.iocoder.yudao.module.finance.controller.admin.companyaccount.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 公司银行账户保存 Request VO")
@Data
public class FinanceCompanyBankAccountSaveReqVO {

    @Schema(description = "编号（更新时必填）")
    private Long id;

    @Schema(description = "主体公司组织部门编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "主体公司不能为空")
    private Long entityCompanyDeptId;

    @Schema(description = "账户名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "账户名称不能为空")
    private String accountName;

    @Schema(description = "开户行", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "开户行不能为空")
    private String bankName;

    @Schema(description = "户名", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "户名不能为空")
    private String accountHolder;

    @Schema(description = "银行账号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "银行账号不能为空")
    private String accountNo;

    @Schema(description = "账户类型 BASIC/GENERAL/SPECIAL")
    private String accountType;

    @Schema(description = "币种 CNY/USD/HKD", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "币种不能为空")
    private String currency;

    @Schema(description = "状态 0 启用 1 停用")
    private Integer status;

    @Schema(description = "备注")
    private String remark;

}
