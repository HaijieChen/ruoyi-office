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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.DEPT_IMPORT_BUSY;

/**
 * 租户级组织写锁 + 事务边界：
 * <p>
 * <b>F1</b>：先锁 → 再开事务 → 业务 → commit/rollback → 最后 unlock。
 * <p>
 * <b>G2</b>：仅当本层开启了<strong>新事务</strong>且成功提交后（{@code TransactionTemplate.execute}
 * 正常返回之后、unlock 之前）清空 {@code DEPT_CHILDREN_ID_LIST}。
 * 嵌套 {@code PROPAGATION_REQUIRED} join 外层时 {@code isNewTransaction=false}，
 * 内层返回<strong>不会</strong> evict，避免提交前清缓存被并发读回填旧快照。
 * 回滚抛错时不执行 evict。
 */
@Component
@Slf4j
public class DeptMutationLock {

    private static final String LOCK_KEY_PATTERN = "system:dept:mutation:%s";
    private static final long WAIT_SECONDS = 3L;
    private static final long LEASE_SECONDS = 120L;

    @Resource
    private ObjectProvider<RedissonClient> redissonClientProvider;
    @Resource
    private PlatformTransactionManager transactionManager;
    @Resource
    private DeptChildrenCacheInvalidator deptChildrenCacheInvalidator;

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
            AtomicBoolean openedNewTransaction = new AtomicBoolean(false);
            T result = transactionTemplate.execute(status -> {
                if (status.isNewTransaction()) {
                    openedNewTransaction.set(true);
                }
                return callQuietly(action);
            });
            // 此处 TransactionTemplate 已完成 commit（异常回滚则不会到此）
            // 仅最外层新事务在成功提交后失效子树缓存（G2）
            if (openedNewTransaction.get()) {
                deptChildrenCacheInvalidator.evictNow();
            }
            return result;
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
