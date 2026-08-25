package cn.iocoder.yudao.module.system.dal.dataobject.mfa;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

@TableName("system_mfa_auth_flow")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TenantIgnore
public class MfaAuthFlowDO extends BaseDO {

    @TableId(type = IdType.INPUT)
    private String id;
    private String flowTokenHash;
    private String tokenClass;
    private String state;
    private Long tenantId;
    private Long userId;
    private String clientId;
    private Long globalPolicyEpoch;
    private Long tenantPolicyEpoch;
    private Long assuranceEpoch;
    private String allowedActions;
    private String allowedFactorIds;
    private Integer attempts;
    private LocalDateTime expiresAt;
}
