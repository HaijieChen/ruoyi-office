package cn.iocoder.yudao.module.finance.dal.dataobject.companyaccount;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * 公司银行账户主数据。
 * <p>主体公司仅通过 {@link #entityCompanyDeptId} 关联组织架构公司（system_dept），
 * 禁止再建公司主体表或双写公司名称等主体字段。
 */
@TableName("finance_company_bank_account")
@KeySequence("finance_company_bank_account_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinanceCompanyBankAccountDO extends TenantBaseDO {

    public static final int STATUS_ENABLE = 0;
    public static final int STATUS_DISABLE = 1;

    @TableId
    private Long id;
    /** 主体公司 = system_dept.id（orgType=公司） */
    private Long entityCompanyDeptId;
    private String accountName;
    private String bankName;
    private String accountHolder;
    private String accountNo;
    private String accountType;
    private String currency;
    /** 0 启用 / 1 停用 */
    private Integer status;
    private String remark;

}
