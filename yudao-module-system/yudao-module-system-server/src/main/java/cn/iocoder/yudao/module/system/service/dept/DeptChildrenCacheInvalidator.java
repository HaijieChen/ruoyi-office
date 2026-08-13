package cn.iocoder.yudao.module.system.service.dept;

import cn.iocoder.yudao.module.system.dal.redis.RedisKeyConstants;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

/**
 * 组织子树缓存 {@link RedisKeyConstants#DEPT_CHILDREN_ID_LIST} 清空入口。
 * <p>
 * 由 {@link DeptMutationLock} 在<strong>最外层事务成功提交之后</strong>调用（G2），
 * 勿在嵌套 {@code createDept} 方法返回时调用。
 */
@Component
@Slf4j
public class DeptChildrenCacheInvalidator {

    @Resource
    private ObjectProvider<CacheManager> cacheManagerProvider;

    /** 立即清空全部组织子树缓存条目 */
    public void evictNow() {
        CacheManager cacheManager = cacheManagerProvider.getIfAvailable();
        if (cacheManager == null) {
            return;
        }
        Cache cache = cacheManager.getCache(RedisKeyConstants.DEPT_CHILDREN_ID_LIST);
        if (cache == null) {
            return;
        }
        cache.clear();
        log.debug("[evictNow] cleared {}", RedisKeyConstants.DEPT_CHILDREN_ID_LIST);
    }

}
