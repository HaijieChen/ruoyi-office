package cn.iocoder.yudao.module.system.dal.dataobject.mfa;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

@TableName("system_mfa_factor")
@KeySequence("system_mfa_factor_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TenantIgnore
public class MfaFactorDO extends BaseDO {

    @TableId
    private Long id;
    private Long tenantId;
    private Long userId;
    /** 对外因子 ID（API factorId） */
    private String factorKey;
    private String type;
    private String status;
    private String label;
    private String destinationHash;
    private String destinationMasked;
    private String secretCiphertext;
    private String keyId;
    private Long lastUsedStep;
    private LocalDateTime verifiedAt;
}
