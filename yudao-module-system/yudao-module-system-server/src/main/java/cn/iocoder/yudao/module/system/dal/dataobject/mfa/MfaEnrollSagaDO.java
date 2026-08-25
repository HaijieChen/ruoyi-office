package cn.iocoder.yudao.module.system.dal.dataobject.mfa;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

@TableName("system_mfa_enroll_saga")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TenantIgnore
public class MfaEnrollSagaDO extends BaseDO {

    @TableId(type = IdType.INPUT)
    private String flowTokenHash;
    private Long tenantId;
    private Long userId;
    private String factorId;
    private Long totpStep;
    private Long expectedEpoch;
    private String accessToken;
    private String refreshToken;
    private String state;
}
