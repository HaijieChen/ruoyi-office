package cn.iocoder.yudao.module.system.service.dept;

import cn.hutool.core.util.ObjectUtil;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.DEPT_IMPORT_BUSY;

/**
 * 租户级组织写锁：导入与手工 create/update/delete 共用，避免并发产生同路径重复节点。
 * <p>
 * 生产环境使用 Redisson；单测无 Redisson 时回退到进程内 {@link ReentrantLock}（可重入）。
 */
@Component
@Slf4j
public class DeptMutationLock {

    private static final String LOCK_KEY_PATTERN = "system:dept:mutation:%s";
    /** 等待获取锁的最长时间 */
    private static final long WAIT_SECONDS = 3L;
    /** 锁租约（看门狗续期前的持有时间上限），覆盖大批量导入 */
    private static final long LEASE_SECONDS = 120L;

    @Resource
    private ObjectProvider<RedissonClient> redissonClientProvider;

    private final ConcurrentHashMap<Long, ReentrantLock> localLocks = new ConcurrentHashMap<>();

    public void execute(Runnable action) {
        execute(() -> {
            action.run();
            return null;
        });
    }

    public <T> T execute(Callable<T> action) {
        Long tenantId = ObjectUtil.defaultIfNull(TenantContextHolder.getTenantId(), 0L);
        RedissonClient redissonClient = redissonClientProvider.getIfAvailable();
        if (redissonClient == null) {
            return executeLocal(tenantId, action);
        }
        return executeRedisson(redissonClient, tenantId, action);
    }

    private <T> T executeLocal(Long tenantId, Callable<T> action) {
        ReentrantLock lock = localLocks.computeIfAbsent(tenantId, id -> new ReentrantLock());
        boolean locked;
        try {
            locked = lock.tryLock(WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw exception(DEPT_IMPORT_BUSY);
        }
        if (!locked) {
            throw exception(DEPT_IMPORT_BUSY);
        }
        try {
            return callQuietly(action);
        } finally {
            lock.unlock();
        }
    }

    private <T> T executeRedisson(RedissonClient redissonClient, Long tenantId, Callable<T> action) {
        String lockKey = String.format(LOCK_KEY_PATTERN, tenantId);
        RLock lock = redissonClient.getLock(lockKey);
        boolean locked;
        try {
            locked = lock.tryLock(WAIT_SECONDS, LEASE_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw exception(DEPT_IMPORT_BUSY);
        }
        if (!locked) {
            throw exception(DEPT_IMPORT_BUSY);
        }
        try {
            return callQuietly(action);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private static <T> T callQuietly(Callable<T> action) {
        try {
            return action.call();
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("组织写操作失败", ex);
        }
    }

}
