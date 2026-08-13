package cn.iocoder.yudao.module.system.service.dept;

import cn.hutool.core.util.ObjectUtil;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.DEPT_IMPORT_BUSY;

/**
 * 租户级组织写锁 + 事务边界：
 * <p>
 * <b>先锁 → 再开事务 → 业务 → commit/rollback → 最后 unlock</b>，
 * 避免「锁已释放、事务尚未提交」窗口导致并发同路径重复节点。
 * <p>
 * 导入与手工 create/update/delete 共用；嵌套调用（import → createDept）依赖锁可重入
 * 与事务 PROPAGATION_REQUIRED 挂起到外层同一事务。
 * <p>
 * 生产用 Redisson；单测无 Redisson 时回退进程内 {@link ReentrantLock}。
 */
@Component
@Slf4j
public class DeptMutationLock {

    private static final String LOCK_KEY_PATTERN = "system:dept:mutation:%s";
    /** 等待获取锁的最长时间 */
    private static final long WAIT_SECONDS = 3L;
    /**
     * Redisson 锁租约。大批量导入可能超过 120s 时有过期风险（审查残余项）；
     * 不使用 -1 watchdog 以避免依赖 Redisson 线程在部分环境异常时永久持锁。
     */
    private static final long LEASE_SECONDS = 120L;

    @Resource
    private ObjectProvider<RedissonClient> redissonClientProvider;
    @Resource
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;

    private final ConcurrentHashMap<Long, ReentrantLock> localLocks = new ConcurrentHashMap<>();

    @PostConstruct
    void initTransactionTemplate() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
    }

    public void execute(Runnable action) {
        execute(() -> {
            action.run();
            return null;
        });
    }

    /**
     * 持租户写锁并在事务内执行。返回后事务已提交或已回滚，锁才释放。
     */
    public <T> T execute(Callable<T> action) {
        Long tenantId = ObjectUtil.defaultIfNull(TenantContextHolder.getTenantId(), 0L);
        HeldLock held = acquire(tenantId);
        try {
            // 锁内开启/加入事务；TransactionTemplate 在 lambda 返回后 commit，再进入 finally unlock
            return transactionTemplate.execute(status -> callQuietly(action));
        } finally {
            held.unlock();
        }
    }

    private HeldLock acquire(Long tenantId) {
        RedissonClient redissonClient = redissonClientProvider.getIfAvailable();
        if (redissonClient == null) {
            return acquireLocal(tenantId);
        }
        return acquireRedisson(redissonClient, tenantId);
    }

    private HeldLock acquireLocal(Long tenantId) {
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
        return lock::unlock;
    }

    private HeldLock acquireRedisson(RedissonClient redissonClient, Long tenantId) {
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
        return () -> {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        };
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

    @FunctionalInterface
    private interface HeldLock {
        void unlock();
    }

}
