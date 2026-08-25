package cn.iocoder.yudao.module.system.dal.dataobject.mfa;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * 用户 MFA assurance 权威（ADR-MFA-v3 §5）：{@code assurance_epoch} 唯一替代 factorVersion。
 */
@TableName("system_mfa_user_assurance")
@KeySequence("system_mfa_user_assurance_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TenantIgnore
public class MfaUserAssuranceDO extends BaseDO {

    @TableId
    private Long id;
    private Long tenantId;
    private Long userId;
    /** OPTIONAL 下用户是否启用 MFA */
    private Boolean enabled;
    /** NONE / PENDING / COMPLETED */
    private String enrollmentState;
    private Long preferredFactorId;
    /** 权威 epoch；缺行不得按 0 猜测 */
    private Long assuranceEpoch;

}
