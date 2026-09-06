package cn.iocoder.yudao.module.bpm.dal.dataobject.oa;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * OA 加班/补卡额度稳定行锁。主键即 UK：tenant + user + type + period。
 */
@TableName("bpm_oa_quota_lock")
@Data
@EqualsAndHashCode(callSuper = true)
public class BpmOAQuotaLockDO extends TenantBaseDO {

    private Long userId;
    private String quotaType;
    private String period;
}
