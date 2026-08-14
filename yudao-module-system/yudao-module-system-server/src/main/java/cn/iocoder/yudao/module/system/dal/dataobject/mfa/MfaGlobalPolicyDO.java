package cn.iocoder.yudao.module.system.dal.dataobject.mfa;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * 全局 MFA 策略（类型化 + 版本）。
 */
@TableName("system_mfa_global_policy")
@KeySequence("system_mfa_global_policy_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TenantIgnore
public class MfaGlobalPolicyDO extends BaseDO {

    public static final long SINGLETON_ID = 1L;

    @TableId
    private Long id;
    /**
     * OFF / OPTIONAL / REQUIRED
     */
    private String mode;
    /**
     * 逗号分隔因子类型，如 TOTP,SMS,EMAIL
     */
    private String allowedFactors;
    private Long policyVersion;
    private String checksum;
    /**
     * 是否已确认生效
     */
    private Boolean confirmed;

}
