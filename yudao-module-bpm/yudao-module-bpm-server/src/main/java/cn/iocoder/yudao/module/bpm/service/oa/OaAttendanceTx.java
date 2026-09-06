package cn.iocoder.yudao.module.bpm.service.oa;

import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.FlowableUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 两 attendance key 终态：原 TX afterCommit 后再 REQUIRES_NEW 持久化。rollback 不派发。
 * 租户走 {@link FlowableUtils#execute(String, Runnable)}：空/NO_TENANT 保持调用线程；数值含 0 显式恢复。
 */
public final class OaAttendanceTx {

    private OaAttendanceTx() {
    }

    public static void dispatchAfterCommit(PlatformTransactionManager txManager, String tenantRaw, Runnable persist) {
        Runnable inner = () -> runRequiresNew(txManager, tenantRaw, persist);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    inner.run();
                }
            });
            return;
        }
        inner.run();
    }

    static void runRequiresNew(PlatformTransactionManager txManager, String tenantRaw, Runnable persist) {
        TransactionTemplate template = new TransactionTemplate(txManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        template.executeWithoutResult(status -> FlowableUtils.execute(tenantRaw, persist));
    }
}
