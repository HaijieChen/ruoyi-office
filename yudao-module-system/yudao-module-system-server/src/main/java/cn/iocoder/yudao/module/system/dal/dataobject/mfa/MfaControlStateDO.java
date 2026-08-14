package cn.iocoder.yudao.module.system.dal.dataobject.mfa;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * MFA 控制面单例（ADR-MFA-v3 §5）：全局策略权威并入本表。
 */
@TableName("system_mfa_control_state")
@KeySequence("system_mfa_control_state_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TenantIgnore
public class MfaControlStateDO extends BaseDO {

    public static final long SINGLETON_ID = 1L;

    @TableId
    private Long id;
    /** UNINITIALIZED / ARMED / DEGRADED_CLOSED */
    private String lifecycleState;
    /** OFF / OPTIONAL / REQUIRED */
    private String globalMode;
    private String globalAllowedFactors;
    private Long globalPolicyEpoch;
    private Long globalMinAcceptedEpoch;
    private LocalDateTime armedAt;
    private String checksum;
    /**
     * v2 {@code system_mfa_global_policy} 是否已消费进本 control（F-R4-02/03）。
     * 一旦为 true，迁移脚本不得再按 legacy 回写。
     */
    private Boolean legacyGlobalMerged;

}
