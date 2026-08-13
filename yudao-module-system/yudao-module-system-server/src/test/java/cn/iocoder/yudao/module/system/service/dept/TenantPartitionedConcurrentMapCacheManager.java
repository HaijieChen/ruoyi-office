package cn.iocoder.yudao.module.system.service.dept;

import cn.hutool.core.util.ObjectUtil;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import org.springframework.cache.Cache;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

/**
 * 测试用：模拟 {@code TenantRedisCacheManager} 对 cacheName 追加 {@code :tenantId}。
 * 使用动态 cache 创建，以便 {@code name:tenantId} 可即时建立。
 */
public class TenantPartitionedConcurrentMapCacheManager extends ConcurrentMapCacheManager {

    public TenantPartitionedConcurrentMapCacheManager(String... ignoredLogicalNames) {
        // 无预置 names → dynamic=true，任意 name:tenantId 可创建
        super();
    }

    @Override
    public Cache getCache(String name) {
        Long tenantId = ObjectUtil.defaultIfNull(TenantContextHolder.getTenantId(), 0L);
        return super.getCache(name + ":" + tenantId);
    }

}
