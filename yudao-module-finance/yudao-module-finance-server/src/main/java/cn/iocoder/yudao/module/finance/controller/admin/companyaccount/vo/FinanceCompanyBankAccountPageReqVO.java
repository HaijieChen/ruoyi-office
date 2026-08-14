package cn.iocoder.yudao.module.finance.controller.admin.companyaccount.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 公司银行账户分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class FinanceCompanyBankAccountPageReqVO extends PageParam {

    @Schema(description = "主体公司组织部门编号")
    private Long entityCompanyDeptId;

    @Schema(description = "账户名称")
    private String accountName;

    @Schema(description = "开户行")
    private String bankName;

    @Schema(description = "账号")
    private String accountNo;

    @Schema(description = "状态 0 启用 1 停用")
    private Integer status;

    @Schema(description = "币种")
    private String currency;

}
