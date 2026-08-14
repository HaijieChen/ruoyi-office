package cn.iocoder.yudao.module.system.dal.dataobject.mfa;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * 租户 MFA 策略。
 */
@TableName("system_mfa_tenant_policy")
@KeySequence("system_mfa_tenant_policy_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TenantIgnore // 由业务按 tenantId 过滤；控制面扫描需跨租户
public class MfaTenantPolicyDO extends BaseDO {

    @TableId
    private Long id;
    private Long tenantId;
    /**
     * INHERIT / OFF / OPTIONAL / REQUIRED
     */
    private String mode;
    private String allowedFactors;
    private Long policyVersion;
    private String checksum;
    private Boolean confirmed;

}
