package cn.iocoder.yudao.module.finance.controller.admin.companyaccount.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 公司银行账户 Response VO")
@Data
public class FinanceCompanyBankAccountRespVO {

    private Long id;
    private Long entityCompanyDeptId;
    /** 主体公司名称（解析自组织，非账户表字段） */
    private String entityCompanyName;
    private String accountName;
    private String bankName;
    private String accountHolder;
    /** 完整账号：仅维护权限可见；出纳 simple-list 为脱敏 */
    private String accountNo;
    private String accountNoMasked;
    private String accountType;
    private String currency;
    private Integer status;
    private String remark;
    private LocalDateTime createTime;

}
