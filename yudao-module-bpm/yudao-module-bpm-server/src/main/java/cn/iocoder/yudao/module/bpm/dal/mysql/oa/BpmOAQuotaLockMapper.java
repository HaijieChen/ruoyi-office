package cn.iocoder.yudao.module.bpm.dal.mysql.oa;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.bpm.dal.dataobject.oa.BpmOAQuotaLockDO;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 额度锁：INSERT ODKU no-op 取得 InnoDB 行锁（含首次插入与已有行）。
 * tenant_id 由调用方 {@code TenantContextHolder.getRequiredTenantId()} 显式传入。
 */
@Mapper
public interface BpmOAQuotaLockMapper extends BaseMapperX<BpmOAQuotaLockDO> {

    String OVERTIME_DAY = "OVERTIME_DAY";
    String PUNCH_MONTH = "PUNCH_MONTH";

    @InterceptorIgnore(tenantLine = "true")
    @Insert("""
            INSERT INTO bpm_oa_quota_lock (tenant_id, user_id, quota_type, period)
            VALUES (#{tenantId}, #{userId}, #{quotaType}, #{period})
            ON DUPLICATE KEY UPDATE quota_type = quota_type
            """)
    int upsertLock(@Param("tenantId") Long tenantId,
                   @Param("userId") Long userId,
                   @Param("quotaType") String quotaType,
                   @Param("period") String period);
}
