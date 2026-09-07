package cn.iocoder.yudao.module.bpm.service.task;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import lombok.extern.slf4j.Slf4j;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmProcessDefinitionInfoDO;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.FlowableUtils;
import cn.iocoder.yudao.module.bpm.service.definition.BpmProcessDefinitionService;
import jakarta.annotation.Resource;
import org.flowable.common.engine.impl.interceptor.CommandConfig;
import org.flowable.engine.ManagementService;
import org.flowable.task.api.Task;
import org.flowable.task.service.impl.persistence.entity.TaskEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.bpm.enums.BpmInitiatorWithdrawErrorCodeConstants.*;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.*;
import static cn.iocoder.yudao.module.bpm.enums.definition.BpmInitiatorWithdrawModeEnum.*;
import static cn.iocoder.yudao.module.bpm.enums.task.BpmnModelConstants.START_USER_NODE_ID;

/**
 * Related mode-1 instances share one InnoDB row per ancestor, locked root-to-leaf in the caller's business transaction.
 * A locking read alone does not refresh an ambient REPEATABLE READ snapshot: compare the
 * pre-write snapshot generation with the current generation, or fail the entire transaction.
 * Never replace this with a JVM lock, process variable, or REQUIRES_NEW business mutation.
 */
@Service
@Slf4j
public class BpmInitiatorWithdrawPolicyService {
    @Resource private DataSource dataSource;
    @Resource private org.flowable.engine.TaskService taskService;
    @Resource private ManagementService managementService;
    @Resource private BpmProcessDefinitionService bpmProcessDefinitionService;
    private final ThreadLocal<Scope> scope = new ThreadLocal<>();

    private static final class Scope {
        final long tenant;
        final String instanceId;
        final int mode;
        final boolean guarded;
        final List<String> guardedInstanceIds;
        boolean humanResult;
        Scope(long tenant, String instanceId, int mode, List<String> guardedInstanceIds) {
            this.tenant = tenant;
            this.instanceId = instanceId;
            this.mode = mode;
            this.guardedInstanceIds = guardedInstanceIds;
            this.guarded = !guardedInstanceIds.isEmpty();
        }
    }

    private record InstanceIdentity(String id, String definitionId, String superExecutionId,
                                    String parentInstanceId, int suspensionState) { }

    private InstanceIdentity readIdentity(long tenant, String instanceId, boolean currentRead) {
        List<InstanceIdentity> rows = jdbc().query("SELECT e.ID_,e.PROC_DEF_ID_,e.SUPER_EXEC_,p.PROC_INST_ID_,e.SUSPENSION_STATE_ "
                        + "FROM ACT_RU_EXECUTION e LEFT JOIN ACT_RU_EXECUTION p ON p.ID_=e.SUPER_EXEC_ AND p.TENANT_ID_=e.TENANT_ID_ "
                        + "WHERE e.ID_=? AND e.ID_=e.PROC_INST_ID_ AND e.TENANT_ID_=?" + (currentRead ? " FOR UPDATE" : ""),
                (rs, row) -> new InstanceIdentity(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getInt(5)),
                instanceId, Long.toString(tenant));
        return rows.size() == 1 ? rows.get(0) : null;
    }

    /** Immutable locator data only. No engine or policy write/lock until the whole chain is known. */
    private List<InstanceIdentity> readLineage(long tenant, String instanceId) {
        List<InstanceIdentity> lineage = new ArrayList<>();
        Set<String> visited = new LinkedHashSet<>();
        String id = instanceId;
        while (id != null) {
            if (!visited.add(id)) {
                throw exception(INITIATOR_WITHDRAW_CONCURRENT_CHANGE);
            }
            InstanceIdentity node = readIdentity(tenant, id, false);
            if (node == null || node.suspensionState() != 1) {
                throw exception(PROCESS_INSTANCE_NOT_EXISTS);
            }
            if (node.superExecutionId() != null && node.parentInstanceId() == null) {
                throw exception(INITIATOR_WITHDRAW_CONCURRENT_CHANGE);
            }
            lineage.add(node);
            id = node.parentInstanceId();
        }
        Collections.reverse(lineage);
        return lineage;
    }

    public static int resolveMode(BpmProcessDefinitionInfoDO definition) {
        if (definition == null) {
            throw exception(PROCESS_DEFINITION_NOT_EXISTS);
        }
        Integer mode = definition.getInitiatorWithdrawMode();
        if (mode == null) {
            return Boolean.FALSE.equals(definition.getAllowWithdrawTask()) ? DISABLED.getMode() : RUNNING_ALLOWED.getMode();
        }
        return mode == NO_HUMAN_RESULTS.getMode() || mode == RUNNING_ALLOWED.getMode() ? mode : DISABLED.getMode();
    }

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public <T> T withInstanceLock(String processInstanceId, Supplier<T> action) {
        return withLock(processInstanceId, false, action);
    }

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void withWithdrawalLock(String processInstanceId, Runnable action) {
        withLock(processInstanceId, true, () -> { action.run(); return null; });
    }

    /** Locate only an id before locking; entities are always re-read inside the guard. */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void withTaskLock(String taskId, Runnable action) {
        Long tenant = requireTenant();
        List<String> ids = jdbc().query("SELECT PROC_INST_ID_ FROM ACT_RU_TASK WHERE ID_=? AND TENANT_ID_=?",
                (rs, row) -> rs.getString(1), taskId, tenant.toString());
        if (ids.size() != 1) {
            throw exception(TASK_NOT_EXISTS);
        }
        withInstanceLock(ids.get(0), () -> {
            if (scope.get().guarded) {
                Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
                if (task == null) {
                    throw exception(TASK_NOT_EXISTS);
                }
                validateCurrentTask(task);
            }
            action.run();
            return null;
        });
    }

    /**
     * Trusted TIMER_FIRED only: keep the timer's agenda/entity cache AND its REQUIRED transaction.
     * Flowable 7.2 dispatches this event before executing the trigger agenda and deleting the job.
     * The ordinary guard still uses a fresh command context; only this event entry opts out.
     */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void withTimerJobLock(org.flowable.job.api.Job job, String taskKey, java.util.function.Consumer<Task> action) {
        if (job == null || job.getTenantId() == null || !job.getTenantId().matches("[0-9]+")
                || job.getId() == null || job.getExecutionId() == null || job.getProcessInstanceId() == null
                || org.flowable.common.engine.impl.context.Context.getCommandContext() == null) {
            throw exception(PROCESS_INSTANCE_ACCESS_DENIED);
        }
        try {
            Long.parseLong(job.getTenantId());
        } catch (NumberFormatException invalidTenant) {
            throw exception(PROCESS_INSTANCE_ACCESS_DENIED);
        }
        FlowableUtils.execute(job.getTenantId(), () -> withLock(job.getProcessInstanceId(), false, true, () -> {
            // No lookup by process/key alone: a withdrawn/resubmitted instance can reuse both.
            List<String> parents = jdbc().query("SELECT e.PARENT_ID_ FROM ACT_RU_EXECUTION e "
                            + "JOIN ACT_RU_JOB j ON j.EXECUTION_ID_=e.ID_ AND j.PROCESS_INSTANCE_ID_=e.PROC_INST_ID_ "
                            + "AND j.TENANT_ID_=e.TENANT_ID_ WHERE j.ID_=? AND e.ID_=? AND e.PROC_INST_ID_=? "
                            + "AND e.TENANT_ID_=? FOR UPDATE",
                    (rs, row) -> rs.getString(1), job.getId(), job.getExecutionId(), job.getProcessInstanceId(), job.getTenantId());
            if (parents.size() != 1 || parents.get(0) == null) {
                throw exception(INITIATOR_WITHDRAW_CONCURRENT_CHANGE);
            }
            // A boundary on a multi-instance user task belongs to its MI root, which has no task.
            // Snapshot only tasks in that exact original execution scope. Never sweep by task key:
            // sequential completion can create another task with the same key (and execution id).
            List<String> taskIds = jdbc().query("SELECT t.ID_ FROM ACT_RU_EXECUTION owner "
                            + "JOIN ACT_RU_EXECUTION e ON (e.ID_=owner.ID_ OR (owner.IS_MI_ROOT_=1 AND e.PARENT_ID_=owner.ID_)) "
                            + "AND e.PROC_INST_ID_=owner.PROC_INST_ID_ AND e.TENANT_ID_=owner.TENANT_ID_ "
                            + "JOIN ACT_RU_TASK t ON t.EXECUTION_ID_=e.ID_ AND t.PROC_INST_ID_=e.PROC_INST_ID_ "
                            + "AND t.TENANT_ID_=e.TENANT_ID_ WHERE owner.ID_=? AND owner.PROC_INST_ID_=? "
                            + "AND owner.TENANT_ID_=? AND t.TASK_DEF_KEY_=? ORDER BY t.ID_ FOR UPDATE",
                    (rs, row) -> rs.getString(1), parents.get(0), job.getProcessInstanceId(), job.getTenantId(), taskKey);
            if (taskIds.isEmpty()) {
                throw exception(INITIATOR_WITHDRAW_CONCURRENT_CHANGE);
            }
            List<Task> tasks = new ArrayList<>();
            for (String taskId : taskIds) {
                Task task = taskService.createTaskQuery().taskId(taskId).taskTenantId(job.getTenantId()).singleResult();
                if (task == null) {
                    throw exception(INITIATOR_WITHDRAW_CONCURRENT_CHANGE);
                }
                validateCurrentTask(task);
                tasks.add(task);
            }
            for (Task task : tasks) {
                // An earlier approval may satisfy the MI completion condition; rejection may end
                // the process. Those original sibling tasks are then already cancelled by Flowable.
                if (taskService.createTaskQuery().taskId(task.getId()).singleResult() != null) {
                    action.accept(task);
                }
            }
            return null;
        }));
    }

    private <T> T withLock(String instanceId, boolean withdrawal, Supplier<T> action) {
        return withLock(instanceId, withdrawal, false, false, action);
    }

    private <T> T withLock(String instanceId, boolean withdrawal, boolean timerCommandContext, Supplier<T> action) {
        return withLock(instanceId, withdrawal, timerCommandContext, false, action);
    }

    private <T> T withLock(String instanceId, boolean withdrawal, boolean timerCommandContext,
                           boolean postCompletion, Supplier<T> action) {
        long tenant = requireTenant();
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Initiator withdrawal guard requires the caller transaction");
        }
        Scope enclosing = scope.get();
        if (enclosing != null) {
            if (enclosing.tenant != tenant || !enclosing.instanceId.equals(instanceId)) {
                throw exception(INITIATOR_WITHDRAW_CONCURRENT_CHANGE);
            }
            checkWithdrawal(withdrawal, enclosing);
            return action.get();
        }
        List<InstanceIdentity> lineage = readLineage(tenant, instanceId);
        Map<String, Integer> modes = new LinkedHashMap<>();
        for (InstanceIdentity node : lineage) {
            modes.put(node.id(), resolveMode(bpmProcessDefinitionService.getProcessDefinitionInfo(node.definitionId())));
        }
        int mode = modes.get(instanceId);
        if (withdrawal && mode == DISABLED.getMode()) {
            throw exception(INITIATOR_WITHDRAW_DISABLED);
        }
        List<String> participants = lineage.stream().map(InstanceIdentity::id)
                .filter(id -> modes.get(id) == NO_HUMAN_RESULTS.getMode()).toList();
        Scope current = new Scope(tenant, instanceId, mode, participants);
        boolean guarded = current.guarded;
        if (guarded) {
            // Read ALL generations before ANY INSERT: own writes must not hide an old RR snapshot.
            Map<String, List<Long>> snapshots = new LinkedHashMap<>();
            for (String participant : participants) {
                snapshots.put(participant, jdbc().query("SELECT generation FROM bpm_initiator_withdraw_state "
                                + "WHERE tenant_id=? AND process_instance_id=?", (rs, row) -> rs.getLong(1), tenant, participant));
            }
            // Related mode-1 ancestors only, deterministic root -> leaf. No execution locks yet.
            for (String participant : participants) {
                int inserted = jdbc().update("INSERT IGNORE INTO bpm_initiator_withdraw_state "
                        + "(tenant_id,process_instance_id,human_result,generation) VALUES (?,?,0,0)", tenant, participant);
                long[] locked = jdbc().queryForObject("SELECT generation,human_result FROM bpm_initiator_withdraw_state "
                                + "WHERE tenant_id=? AND process_instance_id=? FOR UPDATE",
                        (rs, row) -> new long[]{rs.getLong(1), rs.getLong(2)}, tenant, participant);
                if (participant.equals(instanceId)) {
                    current.humanResult = locked[1] != 0;
                    checkWithdrawal(withdrawal, current);
                }
                List<Long> snapshot = snapshots.get(participant);
                if ((snapshot.isEmpty() && inserted != 1) || (!snapshot.isEmpty() && snapshot.get(0) != locked[0])) {
                    throw exception(INITIATOR_WITHDRAW_CONCURRENT_CHANGE);
                }
            }
            // All policy locks precede every engine lock, then current-read the exact tenant/definition/parent chain.
            for (InstanceIdentity node : lineage) {
                if (!node.equals(readIdentity(tenant, node.id(), true))) {
                    throw exception(INITIATOR_WITHDRAW_CONCURRENT_CHANGE);
                }
            }
        }
        scope.set(current);
        try {
            // New Flowable entity cache, same Spring/DataSource transaction. No engine-wide isolation changes.
            // Post-completion (all withdraw modes) and guarded mode-1 both need a fresh Flowable
            // cache in the same Spring transaction. TIMER_FIRED keeps the original command.
            boolean freshCommandContext = !timerCommandContext && (postCompletion || guarded);
            T result = freshCommandContext ? managementService.executeCommand(new CommandConfig(false).transactionRequired(),
                    context -> action.get()) : action.get();
            if (guarded) {
                for (String participant : participants) {
                    jdbc().update("UPDATE bpm_initiator_withdraw_state SET generation=generation+1 "
                            + "WHERE tenant_id=? AND process_instance_id=?", tenant, participant);
                }
            }
            return result;
        } finally {
            scope.remove();
        }
    }

    private void checkWithdrawal(boolean withdrawal, Scope current) {
        if (withdrawal && current.mode == DISABLED.getMode()) {
            throw exception(INITIATOR_WITHDRAW_DISABLED);
        }
        if (withdrawal && current.humanResult) {
            throw exception(INITIATOR_WITHDRAW_HUMAN_RESULT);
        }
    }

    /** Current-read fence for a root-owned callActivity scope selected for withdrawal. */
    public void validateCurrentExecution(org.flowable.engine.runtime.Execution execution) {
        Scope current = scope.get();
        if (current == null || !current.guarded) {
            return;
        }
        List<String[]> rows = jdbc().query("SELECT ACT_ID_,PARENT_ID_ FROM ACT_RU_EXECUTION "
                        + "WHERE ID_=? AND PROC_INST_ID_=? AND TENANT_ID_=? FOR UPDATE",
                (rs, row) -> new String[]{rs.getString(1), rs.getString(2)},
                execution.getId(), current.instanceId, Long.toString(current.tenant));
        if (rows.size() != 1 || !Objects.equals(rows.get(0)[0], execution.getActivityId())
                || !Objects.equals(rows.get(0)[1], execution.getParentId())) {
            throw exception(INITIATOR_WITHDRAW_CONCURRENT_CHANGE);
        }
    }

    /** Current-read fence also rejects a task deleted/reassigned by an engine-originated writer. */
    public void validateCurrentTask(Task task) {
        Scope current = scope.get();
        if (current == null || !current.guarded) {
            return;
        }
        List<Boolean> matches = jdbc().query("SELECT REV_,ASSIGNEE_,OWNER_,TASK_DEF_KEY_,EXECUTION_ID_ "
                        + "FROM ACT_RU_TASK WHERE ID_=? AND PROC_INST_ID_=? AND TENANT_ID_=? FOR UPDATE",
                (rs, row) -> task instanceof TaskEntity entity && rs.getInt(1) == entity.getRevision()
                        && Objects.equals(rs.getString(2), task.getAssignee())
                        && Objects.equals(rs.getString(3), task.getOwner())
                        && Objects.equals(rs.getString(4), task.getTaskDefinitionKey())
                        && Objects.equals(rs.getString(5), task.getExecutionId()),
                task.getId(), current.instanceId, Long.toString(current.tenant));
        if (matches.size() != 1 || !matches.get(0)) {
            throw exception(INITIATOR_WITHDRAW_CONCURRENT_CHANGE);
        }
    }

    /** Called only by the private HUMAN branch, after validation and before every early return. */
    public void markHumanResult(Task task) {
        Scope current = scope.get();
        if (current == null || !current.guarded || START_USER_NODE_ID.equals(task.getTaskDefinitionKey())) {
            return;
        }
        for (String participant : current.guardedInstanceIds) {
            jdbc().update("UPDATE bpm_initiator_withdraw_state SET human_result=1 "
                    + "WHERE tenant_id=? AND process_instance_id=?", current.tenant, participant);
        }
        current.humanResult = current.guardedInstanceIds.contains(current.instanceId);
    }

    /** Trusted engine-event tenant is bound BEFORE the proxy opens its new transaction. */
    public void runAfterCompletion(String instanceId, String eventTenantId, String taskId, Runnable action) {
        if (cn.hutool.core.util.StrUtil.isBlank(eventTenantId)
                || !eventTenantId.matches("[0-9]+")) {
            throw exception(PROCESS_INSTANCE_ACCESS_DENIED);
        }
        FlowableUtils.execute(eventTenantId, () -> {
            for (int attempt = 1; attempt <= 3; attempt++) {
                try {
                    // Retry outside the proxy so every attempt starts with a fresh database snapshot.
                    cn.hutool.extra.spring.SpringUtil.getBean(getClass())
                            .runAfterCompletionInTransaction(instanceId, action);
                    return;
                } catch (ServiceException failure) {
                    if (!Objects.equals(failure.getCode(), INITIATOR_WITHDRAW_CONCURRENT_CHANGE.getCode())) {
                        throw failure;
                    }
                    if (attempt == 3) {
                        log.error("[runAfterCompletion][generation retries exhausted tenantId={} instanceId={} taskId={}]",
                                eventTenantId, instanceId, taskId, failure);
                        throw failure;
                    }
                }
            }
        });
    }

    /** Only the post-completion wrapper calls this; normal submit never splits its transaction. */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void runAfterCompletionInTransaction(String instanceId, Runnable action) {
        List<String> ids = jdbc().query("SELECT ID_ FROM ACT_RU_EXECUTION "
                        + "WHERE ID_=? AND PROC_INST_ID_=ID_ AND TENANT_ID_=?",
                (rs, row) -> rs.getString(1), instanceId, requireTenant().toString());
        if (ids.isEmpty()) {
            return;
        }
        withLock(instanceId, false, false, true, () -> { action.run(); return null; });
    }

    /**
     * A committed read-only locator cannot mistake an ambient RR false-null for "create new".
     * The separate connection does no mutations or locking; all business/BPM writes remain REQUIRED.
     * The ambient lookup additionally sees this transaction's own uncommitted instance.
     */
    public String findActiveInstanceId(String definitionKey, String businessKey) {
        if (cn.hutool.core.util.StrUtil.isBlank(businessKey)) {
            return null;
        }
        String tenant = requireTenant().toString();
        List<String> committed = new ArrayList<>();
        String sql = "SELECT e.ID_ FROM ACT_RU_EXECUTION e JOIN ACT_RE_PROCDEF d ON d.ID_=e.PROC_DEF_ID_ "
                + "WHERE e.ID_=e.PROC_INST_ID_ AND e.TENANT_ID_=? AND d.KEY_=? "
                + "AND e.BUSINESS_KEY_=? AND e.SUSPENSION_STATE_=1";
        try (Connection connection = dataSource.getConnection()) {
            connection.setReadOnly(true);
            connection.setAutoCommit(true);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, tenant);
                statement.setString(2, definitionKey);
                statement.setString(3, businessKey);
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        committed.add(result.getString(1));
                    }
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot locate current process instance", e);
        }
        List<String> ambient = jdbc().query(sql, (rs, row) -> rs.getString(1), tenant, definitionKey, businessKey);
        List<String> ids = new ArrayList<>(committed);
        ambient.stream().filter(id -> !ids.contains(id)).forEach(ids::add);
        if (ids.size() > 1) {
            throw exception(INITIATOR_WITHDRAW_CONCURRENT_CHANGE);
        }
        return ids.isEmpty() ? null : ids.get(0);
    }

    private Long requireTenant() {
        Long tenant = TenantContextHolder.getTenantId();
        if (tenant == null) {
            throw exception(PROCESS_INSTANCE_ACCESS_DENIED);
        }
        return tenant;
    }

    private JdbcTemplate jdbc() {
        return new JdbcTemplate(dataSource);
    }
}
