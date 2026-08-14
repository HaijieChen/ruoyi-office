package cn.iocoder.yudao.module.finance.controller.admin.companyaccount.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 公司银行账户启停 Request VO")
@Data
public class FinanceCompanyBankAccountUpdateStatusReqVO {

    @NotNull(message = "编号不能为空")
    private Long id;

    @NotNull(message = "状态不能为空")
    private Integer status;

}
