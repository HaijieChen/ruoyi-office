package cn.iocoder.yudao.module.system.dal.dataobject.mfa;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * MFA 控制面单例状态（ADR-MFA-v2 §4）：system_mfa_control_state，id 固定为 1。
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
    /**
     * 生命周期：UNINITIALIZED / ARMED / DEGRADED_CLOSED
     */
    private String lifecycleState;
    /**
     * 全局策略版本门闩（单调递增）
     */
    private Long policyVersion;
    private LocalDateTime armedAt;
    private String checksum;

}
