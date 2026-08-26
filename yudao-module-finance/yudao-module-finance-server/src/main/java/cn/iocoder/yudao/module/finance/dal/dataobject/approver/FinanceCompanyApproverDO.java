package cn.iocoder.yudao.module.finance.dal.dataobject.approver;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * 主体公司财务审批人映射。不改组织树，仅用于流程选人。
 */
@TableName("finance_company_approver")
@KeySequence("finance_company_approver_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinanceCompanyApproverDO extends TenantBaseDO {

    @TableId
    private Long id;
    /** 主体公司 = system_dept.id（orgType=公司） */
    private Long entityCompanyDeptId;
    private Long userId;
}
