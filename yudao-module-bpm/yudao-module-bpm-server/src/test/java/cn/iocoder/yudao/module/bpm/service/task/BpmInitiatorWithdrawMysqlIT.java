package cn.iocoder.yudao.module.bpm.service.task;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task.*;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmProcessDefinitionInfoDO;
import cn.iocoder.yudao.module.bpm.enums.task.*;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmnVariableConstants;
import cn.iocoder.yudao.module.bpm.service.definition.BpmModelService;
import cn.iocoder.yudao.module.bpm.service.definition.BpmProcessDefinitionService;
import cn.iocoder.yudao.module.bpm.service.notification.BpmNotificationManager;
import org.flowable.bpmn.model.*;
import org.flowable.engine.*;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

import static cn.iocoder.yudao.module.bpm.enums.BpmInitiatorWithdrawErrorCodeConstants.*;
import static cn.iocoder.yudao.module.bpm.enums.task.BpmnModelConstants.START_USER_NODE_ID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Opt-in, destructive fixtures ONLY in the dedicated bpm_u2_test database. Real engine + Spring + InnoDB. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfSystemProperty(named = "bpm.u2.mysql.url", matches = ".+/bpm_u2_test.*")
class BpmInitiatorWithdrawMysqlIT {
    private final ThreadLocal<Integer> callbackIsolation = new ThreadLocal<>();
    private ProcessEngine engine;
    private JdbcTemplate jdbc;
    private DataSourceTransactionManager txManager;
    private BpmTaskServiceImpl service;
    private BpmInitiatorWithdrawPolicyService policy;
    private BpmProcessInstanceService instances;
    private BpmProcessInstanceServiceImpl submitService;
    private BpmNotificationManager notifications;
    private final Map<String, BpmProcessDefinitionInfoDO> definitions = new ConcurrentHashMap<>();

    @BeforeAll
    void startEngine() {
        DriverManagerDataSource ds = new DriverManagerDataSource(System.getProperty("bpm.u2.mysql.url"),
                "root", System.getProperty("bpm.u2.mysql.password")) {
            @Override public java.sql.Connection getConnection() throws java.sql.SQLException {
                java.sql.Connection connection = super.getConnection();
                if (callbackIsolation.get() != null) connection.setTransactionIsolation(callbackIsolation.get());
                return connection;
            }
        };
        jdbc = new JdbcTemplate(ds);
        txManager = new DataSourceTransactionManager(ds);
        SpringProcessEngineConfiguration cfg = new SpringProcessEngineConfiguration();
        cfg.setDataSource(ds);
        cfg.setTransactionManager(txManager);
        cfg.setDatabaseSchemaUpdate("true");
        cfg.setAsyncExecutorActivate(false);
        cfg.setDisableIdmEngine(true);
        cfg.setDisableEventRegistry(true);
        engine = cfg.buildProcessEngine();
        jdbc.execute("CREATE TABLE IF NOT EXISTS bpm_initiator_withdraw_state (tenant_id BIGINT NOT NULL, "
                + "process_instance_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL, "
                + "human_result TINYINT NOT NULL DEFAULT 0,generation BIGINT NOT NULL DEFAULT 0, "
                + "PRIMARY KEY(tenant_id,process_instance_id)) ENGINE=InnoDB");
        jdbc.execute("CREATE TABLE IF NOT EXISTS u2_business (id VARCHAR(64) PRIMARY KEY, value INT NOT NULL) ENGINE=InnoDB");
        BpmProcessDefinitionService definitionService = mock(BpmProcessDefinitionService.class);
        when(definitionService.getProcessDefinitionInfo(anyString())).thenAnswer(c -> definitions.get(c.getArgument(0)));
        BpmModelService models = mock(BpmModelService.class);
        when(models.getBpmnModelByDefinitionId(anyString())).thenAnswer(c -> engine.getRepositoryService().getBpmnModel(c.getArgument(0)));
        instances = mock(BpmProcessInstanceService.class);
        when(instances.getProcessInstance(anyString())).thenAnswer(c -> engine.getRuntimeService().createProcessInstanceQuery()
                .processInstanceId(c.getArgument(0)).includeProcessVariables().singleResult());
        notifications = mock(BpmNotificationManager.class);
        BpmInitiatorWithdrawPolicyService rawPolicy = new BpmInitiatorWithdrawPolicyService();
        set(rawPolicy, "dataSource", ds);
        set(rawPolicy, "taskService", engine.getTaskService());
        set(rawPolicy, "managementService", engine.getManagementService());
        set(rawPolicy, "bpmProcessDefinitionService", definitionService);
        policy = proxy(rawPolicy);
        BpmTaskServiceImpl rawService = new BpmTaskServiceImpl();
        set(rawService, "initiatorWithdrawPolicyService", policy);
        set(rawService, "runtimeService", engine.getRuntimeService());
        set(rawService, "taskService", engine.getTaskService());
        set(rawService, "historyService", engine.getHistoryService());
        set(rawService, "managementService", engine.getManagementService());
        set(rawService, "processInstanceService", instances);
        set(rawService, "bpmProcessDefinitionService", definitionService);
        set(rawService, "modelService", models);
        set(rawService, "notificationManager", notifications);
        cn.iocoder.yudao.module.system.api.user.AdminUserApi users = mock(cn.iocoder.yudao.module.system.api.user.AdminUserApi.class);
        when(users.getUser(any())).thenAnswer(call -> cn.iocoder.yudao.framework.common.pojo.CommonResult.success(
                new cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO().setId(call.getArgument(0)).setNickname("synthetic-user")));
        when(users.getUserList(any())).thenAnswer(call -> cn.iocoder.yudao.framework.common.pojo.CommonResult.success(
                ((Collection<Long>) call.getArgument(0)).stream().map(id -> new cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO()
                        .setId(id).setNickname("synthetic-user-" + id)).toList()));
        set(rawService, "adminUserApi", users);
        set(rawService, "messageService", mock(cn.iocoder.yudao.module.bpm.service.message.BpmMessageService.class));
        service = proxy(rawService);
        BpmProcessInstanceServiceImpl rawSubmit = new BpmProcessInstanceServiceImpl();
        set(rawSubmit, "initiatorWithdrawPolicyService", policy);
        set(rawSubmit, "runtimeService", engine.getRuntimeService());
        set(rawSubmit, "taskService0", engine.getTaskService());
        set(rawSubmit, "taskService", service);
        submitService = proxy(rawSubmit);
    }

    @AfterAll void closeEngine() { if (engine != null) engine.close(); }
    @BeforeEach void tenant() { TenantContextHolder.setTenantId(1L); reset(notifications); }
    @AfterEach void clearTenant() { TenantContextHolder.clear(); }

    @SuppressWarnings("unchecked")
    private <T> T proxy(T target) {
        ProxyFactory factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(txManager, new AnnotationTransactionAttributeSource()));
        return (T) factory.getProxy();
    }
    private static void set(Object target, String name, Object value) { ReflectionTestUtils.setField(target, name, value); }
    private <T> T transaction(int isolation, Supplier<T> body) {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        tx.setIsolationLevel(isolation);
        return tx.execute(status -> {
            assertEquals(isolation == 2 ? "READ-COMMITTED" : "REPEATABLE-READ",
                    jdbc.queryForObject("SELECT @@transaction_isolation", String.class));
            return body.get();
        });
    }
    private record Fixture(String id, String definition, String task, String businessKey) { }

    private Fixture fixture(int mode) {
        return fixture(mode, model -> { });
    }
    private Fixture fixture(int mode, java.util.function.Consumer<BpmnModel> customize) {
        String key = "u2" + UUID.randomUUID().toString().replace("-", "");
        BpmnModel model = new BpmnModel();
        org.flowable.bpmn.model.Process process = new org.flowable.bpmn.model.Process();
        process.setId(key);
        process.setExecutable(true);
        model.addProcess(process);
        StartEvent start = new StartEvent(); start.setId("start"); process.addFlowElement(start);
        for (String id : List.of(START_USER_NODE_ID, "review", "finalReview")) {
            UserTask task = new UserTask(); task.setId(id); task.setName(id);
            task.setAssignee(id.equals("finalReview") ? "2" : "1");
            process.addFlowElement(task);
        }
        EndEvent end = new EndEvent(); end.setId("end"); process.addFlowElement(end);
        List<String> path = List.of("start", START_USER_NODE_ID, "review", "finalReview", "end");
        for (int i = 0; i < path.size() - 1; i++) process.addFlowElement(new SequenceFlow(path.get(i), path.get(i + 1)));
        customize.accept(model);
        String deployment = engine.getRepositoryService().createDeployment().tenantId("1")
                .addBpmnModel(key + ".bpmn", model).deploy().getId();
        String definition = engine.getRepositoryService().createProcessDefinitionQuery().deploymentId(deployment).singleResult().getId();
        definitions.put(definition, new BpmProcessDefinitionInfoDO().setInitiatorWithdrawMode(mode).setAllowWithdrawTask(true));
        engine.getIdentityService().setAuthenticatedUserId("1");
        String id;
        try {
            id = transaction(4, () -> engine.getRuntimeService().startProcessInstanceById(definition, key,
                    new HashMap<>(Map.of(BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_STATUS, BpmProcessInstanceStatusEnum.RUNNING.getStatus()))).getId());
        } finally { engine.getIdentityService().setAuthenticatedUserId(null); }
        transaction(4, () -> { engine.getTaskService().complete(task(id).getId()); return null; });
        return new Fixture(id, definition, engine.getTaskService().createTaskQuery().processInstanceId(id).list().get(0).getId(), key);
    }
    private Task task(String instance) {
        return engine.getTaskService().createTaskQuery().processInstanceId(instance).includeTaskLocalVariables().singleResult();
    }
    private int marker(String id) {
        return jdbc.queryForObject("SELECT COALESCE(MAX(human_result),0) FROM bpm_initiator_withdraw_state WHERE tenant_id=1 AND process_instance_id=?", Integer.class, id);
    }
    private int stateRows(String id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM bpm_initiator_withdraw_state WHERE process_instance_id=?", Integer.class, id);
    }
    private void approve(String task) { service.approveTask(1L, new BpmTaskApproveReqVO().setId(task).setReason("human")); }
    private void withdraw(String id) { service.withdrawProcessToStart(1L, id, "withdraw"); }
    private void assertStatus(String id, BpmProcessInstanceStatusEnum status) {
        assertEquals(status.getStatus(), engine.getRuntimeService().getVariable(id, BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_STATUS));
    }
    private void assertTaskResult(String task, BpmTaskStatusEnum expected) {
        assertEquals(expected.getStatus(), engine.getHistoryService().createHistoricTaskInstanceQuery().taskId(task)
                .includeTaskLocalVariables().singleResult().getTaskLocalVariables().get(BpmnVariableConstants.TASK_VARIABLE_STATUS));
    }

    @ParameterizedTest @ValueSource(ints = {2, 4})
    void humanWinsAndWithdrawHasExactDenial(int isolation) throws Exception {
        Fixture f = fixture(1);
        race(isolation, () -> approve(f.task), () -> withdraw(f.id), INITIATOR_WITHDRAW_HUMAN_RESULT.getCode());
        assertEquals(1, marker(f.id));
        assertEquals("finalReview", task(f.id).getTaskDefinitionKey());
        assertStatus(f.id, BpmProcessInstanceStatusEnum.RUNNING);
        assertTaskResult(f.task, BpmTaskStatusEnum.APPROVE);
        assertEquals(List.of(BpmCommentTypeEnum.APPROVE.getType()), engine.getTaskService().getProcessInstanceComments(f.id)
                .stream().map(org.flowable.engine.task.Comment::getType).toList());
    }

    @ParameterizedTest @ValueSource(ints = {2, 4})
    void withdrawalWinsAndStaleHumanRollsBack(int isolation) throws Exception {
        Fixture f = fixture(1);
        race(isolation, () -> withdraw(f.id), () -> approve(f.task), INITIATOR_WITHDRAW_CONCURRENT_CHANGE.getCode());
        assertEquals(0, marker(f.id));
        assertEquals(START_USER_NODE_ID, task(f.id).getTaskDefinitionKey());
        assertStatus(f.id, BpmProcessInstanceStatusEnum.NOT_START);
        assertTaskResult(f.task, BpmTaskStatusEnum.WITHDRAW);
        assertEquals(List.of(BpmCommentTypeEnum.WITHDRAW.getType()), engine.getTaskService().getProcessInstanceComments(f.id)
                .stream().map(org.flowable.engine.task.Comment::getType).toList());
    }

    private void race(int isolation, Runnable winner, Runnable loser, int expectedCode) throws Exception {
        CountDownLatch mutationDone = new CountDownLatch(1);
        CountDownLatch allowCommit = new CountDownLatch(1);
        CountDownLatch loserStarted = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> a = executor.submit(() -> {
                TenantContextHolder.setTenantId(1L);
                try { transaction(isolation, () -> { winner.run(); mutationDone.countDown(); await(allowCommit); return null; }); }
                finally { TenantContextHolder.clear(); }
            });
            assertTrue(mutationDone.await(20, TimeUnit.SECONDS), "winner must mutate before loser starts");
            Future<Throwable> b = executor.submit(() -> {
                TenantContextHolder.setTenantId(1L);
                try { return transaction(isolation, () -> {
                    // Establish the ambient snapshot before winner commits; no mocking of guard/engine.
                    jdbc.queryForObject("SELECT COUNT(*) FROM ACT_RU_TASK", Long.class);
                    loserStarted.countDown();
                    loser.run(); return null;
                }); } catch (Throwable e) { return e; } finally { TenantContextHolder.clear(); }
            });
            assertTrue(loserStarted.await(10, TimeUnit.SECONDS));
            org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(5)).until(() ->
                    jdbc.queryForObject("SELECT COUNT(*) FROM performance_schema.data_lock_waits w JOIN performance_schema.data_locks l "
                            + "ON l.ENGINE_LOCK_ID=w.REQUESTING_ENGINE_LOCK_ID WHERE l.OBJECT_SCHEMA=DATABASE()", Long.class) > 0);
            allowCommit.countDown();
            a.get(20, TimeUnit.SECONDS);
            Throwable failure = b.get(20, TimeUnit.SECONDS);
            assertInstanceOf(ServiceException.class, failure);
            assertEquals(expectedCode, ((ServiceException) failure).getCode());
        } finally { allowCommit.countDown(); executor.shutdownNow(); }
    }
    private static void await(CountDownLatch latch) {
        try { if (!latch.await(20, TimeUnit.SECONDS)) throw new AssertionError("barrier timed out"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
    }

    @ParameterizedTest @ValueSource(ints = {2, 4})
    void failedHumanOperationRollsBackMarkerCommentTaskAndBusiness(int isolation) {
        Fixture f = fixture(1);
        jdbc.update("INSERT INTO u2_business(id,value) VALUES (?,0)", f.id);
        doThrow(new IllegalStateException("synthetic notification failure")).when(notifications)
                .sendTaskEventNotification(any(), any(), any(), any(), any());
        assertThrows(IllegalStateException.class, () -> transaction(isolation, () -> {
            jdbc.update("UPDATE u2_business SET value=1 WHERE id=?", f.id);
            approve(f.task); return null;
        }));
        assertEquals(0, marker(f.id));
        assertEquals(0, stateRows(f.id));
        assertEquals(f.task, task(f.id).getId());
        assertEquals(0, engine.getTaskService().getProcessInstanceComments(f.id).size());
        assertEquals(0, jdbc.queryForObject("SELECT value FROM u2_business WHERE id=?", Integer.class, f.id));
        assertStatus(f.id, BpmProcessInstanceStatusEnum.RUNNING);
    }

    @Test void disabledModeAndReentrantGuardCannotBypassPolicy() {
        Fixture f = fixture(0);
        assertEquals(INITIATOR_WITHDRAW_DISABLED.getCode(), assertThrows(ServiceException.class, () -> withdraw(f.id)).getCode());
        assertEquals(INITIATOR_WITHDRAW_DISABLED.getCode(), assertThrows(ServiceException.class, () -> transaction(4,
                () -> policy.withInstanceLock(f.id, () -> { policy.withWithdrawalLock(f.id, () -> fail("bypassed")); return null; }))).getCode());
        assertEquals(f.task, task(f.id).getId());
        assertEquals(0, stateRows(f.id));
    }

    @Test void tenantMismatchCannotMarkOrWithdraw() {
        Fixture f = fixture(1);
        TenantContextHolder.setTenantId(2L);
        assertThrows(ServiceException.class, () -> approve(f.task));
        assertThrows(ServiceException.class, () -> withdraw(f.id));
        TenantContextHolder.setTenantId(1L);
        assertEquals(0, stateRows(f.id));
        assertEquals(f.task, task(f.id).getId());
    }

    @Test void runningAllowedDoesNotTrackAndCanWithdrawAfterHuman() {
        Fixture f = fixture(2);
        approve(f.task);
        withdraw(f.id);
        assertEquals(0, stateRows(f.id));
        assertEquals(START_USER_NODE_ID, task(f.id).getTaskDefinitionKey());
    }

    @Test void humanReturnRemainsIrreversibleThroughSameInstanceStartResubmit() {
        Fixture f = fixture(1);
        service.returnTask(1L, new BpmTaskReturnReqVO().setId(f.task).setTargetTaskDefinitionKey(START_USER_NODE_ID).setReason("return"));
        assertEquals(1, marker(f.id));
        assertEquals(START_USER_NODE_ID, task(f.id).getTaskDefinitionKey());
        approve(task(f.id).getId());
        assertEquals(1, marker(f.id));
        assertEquals(INITIATOR_WITHDRAW_HUMAN_RESULT.getCode(), assertThrows(ServiceException.class, () -> withdraw(f.id)).getCode());
    }

    private String submit(Fixture f) {
        String key = engine.getRepositoryService().getProcessDefinition(f.definition).getKey();
        String prior = org.flowable.common.engine.impl.identity.Authentication.getAuthenticatedUserId();
        org.flowable.common.engine.impl.identity.Authentication.setAuthenticatedUserId("outer-authentication");
        try {
            return submitService.submitProcessInstance(1L,
                    new cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO()
                            .setProcessDefinitionKey(key).setBusinessKey(f.businessKey).setVariables(Map.of("resubmitted", true)));
        } finally {
            assertEquals("outer-authentication", org.flowable.common.engine.impl.identity.Authentication.getAuthenticatedUserId());
            org.flowable.common.engine.impl.identity.Authentication.setAuthenticatedUserId(prior);
        }
    }

    @ParameterizedTest @ValueSource(ints = {2, 4})
    void realSubmitReusesInstanceAndSharesBusinessRollback(int isolation) {
        Fixture f = fixture(1);
        withdraw(f.id);
        jdbc.update("INSERT INTO u2_business(id,value) VALUES (?,0)", f.id);
        assertThrows(IllegalStateException.class, () -> transaction(isolation, () -> {
            jdbc.queryForObject("SELECT value FROM u2_business WHERE id=?", Integer.class, f.id);
            jdbc.update("UPDATE u2_business SET value=1 WHERE id=?", f.id);
            assertEquals(f.id, submit(f));
            throw new IllegalStateException("business rollback AFTER submit");
        }));
        assertEquals(START_USER_NODE_ID, task(f.id).getTaskDefinitionKey());
        assertStatus(f.id, BpmProcessInstanceStatusEnum.NOT_START);
        assertEquals(0, jdbc.queryForObject("SELECT value FROM u2_business WHERE id=?", Integer.class, f.id));
        assertNull(engine.getRuntimeService().getVariable(f.id, "resubmitted"));
        assertEquals(f.id, transaction(isolation, () -> submit(f)));
        assertEquals("review", task(f.id).getTaskDefinitionKey());
        assertStatus(f.id, BpmProcessInstanceStatusEnum.RUNNING);
        assertEquals(0, marker(f.id));
        assertEquals(1, engine.getRuntimeService().createProcessInstanceQuery().processInstanceBusinessKey(f.businessKey).count());
        approve(task(f.id).getId());
        assertEquals(1, marker(f.id));
    }

    @ParameterizedTest @ValueSource(ints = {2, 4})
    void realSubmitAfterWaitingOnWithdrawalRejectsStaleSnapshotWithoutPartialBusinessWrite(int isolation) throws Exception {
        Fixture f = fixture(1);
        jdbc.update("INSERT INTO u2_business(id,value) VALUES (?,0)", f.id);
        race(isolation, () -> withdraw(f.id), () -> {
            jdbc.update("UPDATE u2_business SET value=1 WHERE id=?", f.id);
            submit(f);
        }, INITIATOR_WITHDRAW_CONCURRENT_CHANGE.getCode());
        assertEquals(0, jdbc.queryForObject("SELECT value FROM u2_business WHERE id=?", Integer.class, f.id));
        assertEquals(START_USER_NODE_ID, task(f.id).getTaskDefinitionKey());
        assertStatus(f.id, BpmProcessInstanceStatusEnum.NOT_START);
        assertNull(engine.getRuntimeService().getVariable(f.id, "resubmitted"));
        assertEquals(0, marker(f.id));
    }

    @Test void createdAutomaticCallbackRequeriesAndIgnoresAssigneeAsHumanHeuristic() {
        Fixture f = fixture(1, model -> {
            cn.iocoder.yudao.module.bpm.framework.flowable.core.util.BpmnModelUtils.addExtensionElement(model.getFlowElement("review"),
                    cn.iocoder.yudao.module.bpm.enums.task.BpmnModelConstants.USER_TASK_APPROVE_TYPE,
                    cn.iocoder.yudao.module.bpm.enums.definition.BpmUserTaskApproveTypeEnum.AUTO_APPROVE.getType().toString());
        });
        try (MockedStatic<SpringUtil> beans = mockStatic(SpringUtil.class)) {
            beans.when(() -> SpringUtil.getBean(BpmInitiatorWithdrawPolicyService.class)).thenReturn(policy);
            transaction(4, () -> { service.processTaskCreated(task(f.id)); return null; });
        }
        assertEquals("finalReview", task(f.id).getTaskDefinitionKey());
        assertEquals(0, marker(f.id));
        assertTaskResult(f.task, BpmTaskStatusEnum.APPROVE);
        withdraw(f.id);
    }

    @ParameterizedTest @ValueSource(ints = {2, 4})
    void automaticCallbackRetriesRealGenerationConflictInFreshTransaction(int isolation) throws Exception {
        Fixture f = fixture(1, model -> cn.iocoder.yudao.module.bpm.framework.flowable.core.util.BpmnModelUtils.addExtensionElement(
                model.getFlowElement("review"), cn.iocoder.yudao.module.bpm.enums.task.BpmnModelConstants.USER_TASK_APPROVE_TYPE,
                cn.iocoder.yudao.module.bpm.enums.definition.BpmUserTaskApproveTypeEnum.AUTO_APPROVE.getType().toString()));
        transaction(isolation, () -> policy.withInstanceLock(f.id, () -> null));
        jdbc.update("INSERT INTO u2_business(id,value) VALUES (?,0)", f.id);
        CountDownLatch callbackReady = new CountDownLatch(1);
        CountDownLatch mutationDone = new CountDownLatch(1);
        CountDownLatch allowCommit = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger attempts = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger conflicts = new java.util.concurrent.atomic.AtomicInteger();
        ProxyFactory observed = new ProxyFactory(policy);
        observed.setProxyTargetClass(true);
        observed.addAdvice((org.aopalliance.intercept.MethodInterceptor) invocation -> {
            if (!invocation.getMethod().getName().equals("runAfterCompletionInTransaction")) return invocation.proceed();
            if (attempts.incrementAndGet() == 1) { callbackReady.countDown(); await(mutationDone); }
            try { return invocation.proceed(); }
            catch (ServiceException ex) {
                if (Objects.equals(ex.getCode(), INITIATOR_WITHDRAW_CONCURRENT_CHANGE.getCode())) conflicts.incrementAndGet();
                throw ex;
            }
        });
        BpmInitiatorWithdrawPolicyService observedPolicy = (BpmInitiatorWithdrawPolicyService) observed.getProxy();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> winner = executor.submit(() -> {
                TenantContextHolder.setTenantId(1L);
                try { await(callbackReady); transaction(isolation, () -> policy.withInstanceLock(f.id, () -> {
                    jdbc.update("UPDATE u2_business SET value=value+1 WHERE id=?", f.id);
                    mutationDone.countDown(); await(allowCommit); return null;
                })); } finally { TenantContextHolder.clear(); }
            });
            Future<?> callback = executor.submit(() -> {
                TenantContextHolder.setTenantId(1L);
                callbackIsolation.set(isolation);
                try (MockedStatic<SpringUtil> beans = mockStatic(SpringUtil.class)) {
                    beans.when(() -> SpringUtil.getBean(BpmInitiatorWithdrawPolicyService.class)).thenReturn(observedPolicy);
                    transaction(isolation, () -> { service.processTaskCreated(task(f.id)); return null; });
                } finally { callbackIsolation.remove(); TenantContextHolder.clear(); }
            });
            org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(5)).until(() -> {
                if (callback.isDone()) { callback.get(); fail("callback ended before waiting; attempts=" + attempts.get()); }
                return jdbc.queryForObject("SELECT COUNT(*) FROM performance_schema.data_lock_waits w JOIN performance_schema.data_locks l "
                        + "ON l.ENGINE_LOCK_ID=w.REQUESTING_ENGINE_LOCK_ID WHERE l.OBJECT_SCHEMA=DATABASE()", Long.class) > 0;
            });
            allowCommit.countDown();
            winner.get(20, TimeUnit.SECONDS);
            callback.get(20, TimeUnit.SECONDS);
            assertEquals("finalReview", task(f.id).getTaskDefinitionKey(), "committed automatic task must advance despite one stale generation");
            assertEquals(2, attempts.get());
            assertEquals(1, conflicts.get());
            assertEquals(0, marker(f.id));
            assertEquals(1, jdbc.queryForObject("SELECT value FROM u2_business WHERE id=?", Integer.class, f.id));
            assertTaskResult(f.task, BpmTaskStatusEnum.APPROVE);
            assertEquals(1, engine.getTaskService().createTaskQuery().processInstanceId(f.id).count());
        } finally { allowCommit.countDown(); executor.shutdownNow(); }
    }

    @Test void assignedAutomaticCallbackBindsTenantBeforeReadingAndKeepsMarkerClear() {
        Fixture f = fixture(1, model -> cn.iocoder.yudao.module.bpm.framework.flowable.core.util.BpmnModelUtils.addExtensionElement(
                model.getFlowElement("review"), cn.iocoder.yudao.module.bpm.enums.task.BpmnModelConstants.USER_TASK_ASSIGN_START_USER_HANDLER_TYPE, "2"));
        try (MockedStatic<SpringUtil> beans = mockStatic(SpringUtil.class)) {
            beans.when(() -> SpringUtil.getBean(BpmInitiatorWithdrawPolicyService.class)).thenReturn(policy);
            transaction(4, () -> { service.processTaskAssigned(task(f.id)); TenantContextHolder.clear(); return null; });
            assertNull(TenantContextHolder.getTenantId());
        }
        TenantContextHolder.setTenantId(1L);
        assertEquals("finalReview", task(f.id).getTaskDefinitionKey());
        assertEquals(0, marker(f.id));
        withdraw(f.id);
    }

    @Test void missingCallbackTenantCannotReuseAmbientTenant() {
        Fixture f = fixture(1);
        for (String invalid : Arrays.asList(null, "", " ", "not-a-tenant")) {
            assertThrows(ServiceException.class, () -> policy.runAfterCompletion(f.id, invalid, f.task, () -> fail("must not execute")));
        }
        assertEquals(f.task, task(f.id).getId());
        assertEquals(0, stateRows(f.id));
    }

    @ParameterizedTest @org.junit.jupiter.params.provider.CsvSource({"2,false", "4,false", "4,true"})
    void boundaryTimerDeletingTaskCannotLetOldHumanWriteCommit(int isolation, boolean withdrawal) throws Exception {
        Fixture f = fixture(1, model -> {
            BoundaryEvent boundary = new BoundaryEvent(); boundary.setId("boundary");
            boundary.setAttachedToRef((UserTask) model.getFlowElement("review")); boundary.setCancelActivity(true);
            TimerEventDefinition timer = new TimerEventDefinition(); timer.setTimeDuration("PT1H");
            boundary.addEventDefinition(timer); model.getMainProcess().addFlowElement(boundary);
            model.getMainProcess().addFlowElement(new SequenceFlow("boundary", "finalReview"));
        });
        jdbc.update("INSERT INTO u2_business(id,value) VALUES (?,0)", f.id);
        CountDownLatch snapshotReady = new CountDownLatch(1);
        CountDownLatch timerCommitted = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Throwable> stale = executor.submit(() -> {
                TenantContextHolder.setTenantId(1L);
                try { return transaction(isolation, () -> {
                    task(f.id); snapshotReady.countDown(); await(timerCommitted);
                    jdbc.update("UPDATE u2_business SET value=1 WHERE id=?", f.id);
                    if (withdrawal) withdraw(f.id); else approve(f.task);
                    return null;
                }); } catch (Throwable e) { return e; } finally { TenantContextHolder.clear(); }
            });
            assertTrue(snapshotReady.await(10, TimeUnit.SECONDS));
            String job = engine.getManagementService().createTimerJobQuery().processInstanceId(f.id).singleResult().getId();
            engine.getManagementService().moveTimerToExecutableJob(job);
            engine.getManagementService().executeJob(job);
            timerCommitted.countDown();
            ServiceException conflict = assertInstanceOf(ServiceException.class, stale.get(20, TimeUnit.SECONDS));
            if (withdrawal) assertEquals(INITIATOR_WITHDRAW_CONCURRENT_CHANGE.getCode(), conflict.getCode());
            assertEquals("finalReview", task(f.id).getTaskDefinitionKey());
            assertEquals(0, marker(f.id));
            assertEquals(0, stateRows(f.id));
            assertEquals(0, jdbc.queryForObject("SELECT value FROM u2_business WHERE id=?", Integer.class, f.id));
            assertTrue(engine.getTaskService().getProcessInstanceComments(f.id).isEmpty());
        } finally { timerCommitted.countDown(); executor.shutdownNow(); }
    }

    @Test void delegateAndAfterSignEarlyReturnsStillRecordHumanResult() {
        Fixture delegated = fixture(1);
        service.delegateTask(1L, new BpmTaskDelegateReqVO().setId(delegated.task).setDelegateUserId(2L).setReason("delegate"));
        assertEquals(0, marker(delegated.id));
        service.approveTask(2L, new BpmTaskApproveReqVO().setId(delegated.task).setReason("delegated human"));
        assertEquals(1, marker(delegated.id));
        assertEquals(delegated.task, task(delegated.id).getId());
        assertEquals("1", task(delegated.id).getAssignee());
        assertEquals(INITIATOR_WITHDRAW_HUMAN_RESULT.getCode(), assertThrows(ServiceException.class, () -> withdraw(delegated.id)).getCode());

        Fixture sign = fixture(1);
        service.createSignTask(1L, new BpmTaskSignCreateReqVO().setId(sign.task).setUserIds(Set.of(2L))
                .setType(BpmTaskSignTypeEnum.AFTER.getType()).setReason("after sign"));
        assertEquals(0, marker(sign.id));
        approve(sign.task);
        assertEquals(1, marker(sign.id));
        assertTaskResult(sign.task, BpmTaskStatusEnum.APPROVING);
        assertEquals(INITIATOR_WITHDRAW_HUMAN_RESULT.getCode(), assertThrows(ServiceException.class, () -> withdraw(sign.id)).getCode());
    }

    @Test void transferAndDelegateActionsAloneLeaveWithdrawalWindowOpen() {
        Fixture transferred = fixture(1);
        service.transferTask(1L, new BpmTaskTransferReqVO().setId(transferred.task).setAssigneeUserId(2L).setReason("transfer"));
        assertEquals(0, marker(transferred.id));
        withdraw(transferred.id);
        Fixture delegated = fixture(1);
        service.delegateTask(1L, new BpmTaskDelegateReqVO().setId(delegated.task).setDelegateUserId(2L).setReason("delegate"));
        assertEquals(0, marker(delegated.id));
        withdraw(delegated.id);
    }

    @Test void missingStartTargetRejectsWithoutPartialStateOrComments() {
        Fixture f = fixture(1, model -> {
            FlowElement start = model.getFlowElement(START_USER_NODE_ID);
            model.getMainProcess().removeFlowElement(START_USER_NODE_ID);
            start.setId("differentStart"); model.getMainProcess().addFlowElement(start);
            model.getMainProcess().getFlowElements().stream().filter(SequenceFlow.class::isInstance).map(SequenceFlow.class::cast)
                    .forEach(flow -> {
                        if (START_USER_NODE_ID.equals(flow.getSourceRef())) flow.setSourceRef("differentStart");
                        if (START_USER_NODE_ID.equals(flow.getTargetRef())) flow.setTargetRef("differentStart");
                    });
        });
        assertEquals(cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.TASK_TARGET_NODE_NOT_EXISTS.getCode(),
                assertThrows(ServiceException.class, () -> withdraw(f.id)).getCode());
        assertEquals(f.task, task(f.id).getId());
        assertEquals(0, stateRows(f.id));
        assertTrue(engine.getTaskService().getProcessInstanceComments(f.id).isEmpty());
        assertStatus(f.id, BpmProcessInstanceStatusEnum.RUNNING);
    }

    @Test void forgedVariablesCannotClearAuthoritativeHumanMarker() {
        Fixture f = fixture(1);
        service.approveTask(1L, new BpmTaskApproveReqVO().setId(f.task).setReason("human")
                .setVariables(Map.of("human_result", 0, "initiatorWithdrawMode", 2, "generation", 0)));
        engine.getRuntimeService().setVariables(f.id, Map.of("human_result", 0, "initiatorWithdrawMode", 2));
        assertEquals(INITIATOR_WITHDRAW_HUMAN_RESULT.getCode(), assertThrows(ServiceException.class, () -> withdraw(f.id)).getCode());
    }

    private Fixture oneShotApproveTimeout() {
        return oneShotTimeout(cn.iocoder.yudao.module.bpm.enums.definition.BpmUserTaskTimeoutHandlerTypeEnum.APPROVE.getType(), model -> { });
    }

    private Fixture oneShotTimeout(int handlerType, java.util.function.Consumer<BpmnModel> customize) {
        return fixture(1, model -> {
            BoundaryEvent boundary = new BoundaryEvent();
            boundary.setId("reviewTimeout"); boundary.setAttachedToRef((UserTask) model.getFlowElement("review"));
            boundary.setCancelActivity(false);
            TimerEventDefinition timer = new TimerEventDefinition(); timer.setTimeDuration("PT1H");
            boundary.addEventDefinition(timer);
            cn.iocoder.yudao.module.bpm.framework.flowable.core.util.BpmnModelUtils.addExtensionElement(boundary,
                    BpmnModelConstants.BOUNDARY_EVENT_TYPE,
                    cn.iocoder.yudao.module.bpm.enums.definition.BpmBoundaryEventTypeEnum.USER_TASK_TIMEOUT.getType());
            cn.iocoder.yudao.module.bpm.framework.flowable.core.util.BpmnModelUtils.addExtensionElement(boundary,
                    BpmnModelConstants.USER_TASK_TIMEOUT_HANDLER_TYPE, handlerType);
            model.getMainProcess().addFlowElement(boundary);
            customize.accept(model);
        });
    }

    private void withProductionTimerListener(Runnable body) {
        cn.iocoder.yudao.module.bpm.framework.flowable.core.listener.BpmTaskEventListener listener =
                new cn.iocoder.yudao.module.bpm.framework.flowable.core.listener.BpmTaskEventListener();
        set(listener, "taskService", service);
        BpmModelService models = mock(BpmModelService.class);
        when(models.getBpmnModelByDefinitionId(anyString())).thenAnswer(c -> engine.getRepositoryService().getBpmnModel(c.getArgument(0)));
        set(listener, "modelService", models);
        engine.getRuntimeService().addEventListener(listener,
                org.flowable.common.engine.api.delegate.event.FlowableEngineEventType.TIMER_FIRED);
        try (MockedStatic<SpringUtil> beans = mockStatic(SpringUtil.class)) {
            beans.when(() -> SpringUtil.getBean(BpmInitiatorWithdrawPolicyService.class)).thenReturn(policy);
            beans.when(() -> SpringUtil.getBean(BpmTaskServiceImpl.class)).thenReturn(service);
            body.run();
        } finally { engine.getRuntimeService().removeEventListener(listener); }
    }

    @Test void realOneShotTimeoutFailureMustRetainRetryableJob() {
        Fixture f = oneShotApproveTimeout();
        String timerId = engine.getManagementService().createTimerJobQuery().processInstanceId(f.id).singleResult().getId();
        String jobId = engine.getManagementService().moveTimerToExecutableJob(timerId).getId();
        doThrow(new IllegalStateException("first real timeout notification failure")).doNothing().when(notifications)
                .sendTaskEventNotification(any(), any(), any(), any(), any());
        withProductionTimerListener(() -> {
            assertThrows(RuntimeException.class, () -> engine.getManagementService().executeJob(jobId));
            assertNotNull(engine.getManagementService().createJobQuery().jobId(jobId).singleResult(), "failed timer must remain retryable");
            assertEquals(f.task, task(f.id).getId());
            assertEquals(0, stateRows(f.id));
            assertTrue(engine.getTaskService().getProcessInstanceComments(f.id).isEmpty());
            engine.getManagementService().executeJob(jobId);
            assertNull(engine.getManagementService().createJobQuery().jobId(jobId).singleResult());
            assertEquals("finalReview", task(f.id).getTaskDefinitionKey());
            assertEquals(0, marker(f.id));
            assertEquals(1, engine.getTaskService().getProcessInstanceComments(f.id).size());
        });
        verify(notifications, times(2)).sendTaskEventNotification(any(), any(), any(), any(), any());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void realMiUserTaskTimeoutApprovesCurrentInstancesWithinOriginalScope(boolean sequential) {
        Fixture f = oneShotTimeout(cn.iocoder.yudao.module.bpm.enums.definition.BpmUserTaskTimeoutHandlerTypeEnum.APPROVE.getType(), model -> {
            MultiInstanceLoopCharacteristics loop = new MultiInstanceLoopCharacteristics();
            loop.setSequential(sequential); loop.setLoopCardinality("2");
            ((UserTask) model.getFlowElement("review")).setLoopCharacteristics(loop);
        });
        List<Task> originalTasks = engine.getTaskService().createTaskQuery().processInstanceId(f.id).taskDefinitionKey("review").list();
        assertEquals(sequential ? 1 : 2, originalTasks.size());
        org.flowable.job.api.Job timer = engine.getManagementService().createTimerJobQuery().processInstanceId(f.id).singleResult();
        assertNotNull(timer);
        System.out.println("MI_TIMEOUT_TOPOLOGY sequential=" + sequential + " process=" + f.id
                + " timer=" + timer.getId() + " timerExecution=" + timer.getExecutionId()
                + " executions=" + jdbc.queryForList("SELECT ID_,PARENT_ID_,ACT_ID_,IS_MI_ROOT_,IS_ACTIVE_,IS_SCOPE_ FROM ACT_RU_EXECUTION WHERE PROC_INST_ID_=? ORDER BY ID_", f.id)
                + " tasks=" + jdbc.queryForList("SELECT ID_,EXECUTION_ID_,TASK_DEF_KEY_,TENANT_ID_ FROM ACT_RU_TASK WHERE PROC_INST_ID_=? ORDER BY ID_", f.id));
        org.flowable.job.api.Job job = engine.getManagementService().moveTimerToExecutableJob(timer.getId());
        withProductionTimerListener(() -> assertDoesNotThrow(() -> engine.getManagementService().executeJob(job.getId()),
                "valid MI timeout must not be rejected merely because boundary parent is an MI root"));
        for (Task original : originalTasks) {
            assertEquals(0, engine.getTaskService().createTaskQuery().taskId(original.getId()).count());
            assertTaskResult(original.getId(), BpmTaskStatusEnum.APPROVE);
        }
        assertEquals(sequential ? "review" : "finalReview", task(f.id).getTaskDefinitionKey());
        assertEquals(originalTasks.size(), engine.getTaskService().getProcessInstanceComments(f.id).size());
        assertEquals(0, marker(f.id));
        assertEquals(0, engine.getManagementService().createJobQuery().jobId(job.getId()).count());
    }

    @Test void miTimeoutFailureRollsBackAllOriginalTasksAndCanRetry() {
        Fixture f = oneShotTimeout(cn.iocoder.yudao.module.bpm.enums.definition.BpmUserTaskTimeoutHandlerTypeEnum.APPROVE.getType(), model -> {
            MultiInstanceLoopCharacteristics loop = new MultiInstanceLoopCharacteristics();
            loop.setLoopCardinality("2");
            ((UserTask) model.getFlowElement("review")).setLoopCharacteristics(loop);
        });
        List<String> originalIds = engine.getTaskService().createTaskQuery().processInstanceId(f.id)
                .list().stream().map(Task::getId).sorted().toList();
        String timerId = engine.getManagementService().createTimerJobQuery().processInstanceId(f.id).singleResult().getId();
        String jobId = engine.getManagementService().moveTimerToExecutableJob(timerId).getId();
        doNothing().doThrow(new IllegalStateException("second MI notification fails")).doNothing()
                .when(notifications).sendTaskEventNotification(any(), any(), any(), any(), any());
        withProductionTimerListener(() -> {
            assertThrows(RuntimeException.class, () -> engine.getManagementService().executeJob(jobId));
            assertEquals(originalIds, engine.getTaskService().createTaskQuery().processInstanceId(f.id)
                    .list().stream().map(Task::getId).sorted().toList());
            assertTrue(engine.getTaskService().getProcessInstanceComments(f.id).isEmpty());
            assertEquals(0, stateRows(f.id));
            assertNotNull(engine.getManagementService().createJobQuery().jobId(jobId).singleResult());
            engine.getManagementService().executeJob(jobId);
        });
        assertEquals("finalReview", task(f.id).getTaskDefinitionKey());
        assertEquals(2, engine.getTaskService().getProcessInstanceComments(f.id).size());
        assertEquals(0, marker(f.id));
        assertNull(engine.getManagementService().createJobQuery().jobId(jobId).singleResult());
    }

    private void runNativeJob(org.flowable.job.api.Job job) {
        org.flowable.job.service.JobServiceConfiguration jobs = ((org.flowable.engine.impl.cfg.ProcessEngineConfigurationImpl)
                engine.getProcessEngineConfiguration()).getJobServiceConfiguration();
        new org.flowable.job.service.impl.asyncexecutor.ExecuteAsyncRunnable(job, jobs, jobs.getJobEntityManager(), null,
                org.flowable.job.service.impl.asyncexecutor.JobExecutionObservationProvider.NOOP).run();
    }

    @Test void nativeExecutorRetriesFailedTimerThenCommitsExactlyOnce() {
        Fixture f = oneShotApproveTimeout();
        jdbc.update("INSERT INTO u2_business(id,value) VALUES (?,0)", f.id);
        String timerId = engine.getManagementService().createTimerJobQuery().processInstanceId(f.id).singleResult().getId();
        org.flowable.job.api.Job job = engine.getManagementService().moveTimerToExecutableJob(timerId);
        java.util.concurrent.atomic.AtomicInteger attempts = new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(call -> {
            jdbc.update("UPDATE u2_business SET value=value+1 WHERE id=?", f.id);
            if (attempts.incrementAndGet() == 1) throw new IllegalStateException("native fail once");
            return null;
        }).when(notifications).sendTaskEventNotification(any(), any(), any(), any(), any());
        withProductionTimerListener(() -> {
            runNativeJob(job);
            assertEquals(1, attempts.get());
            assertEquals(f.task, task(f.id).getId());
            assertEquals(0, stateRows(f.id));
            assertEquals(0, jdbc.queryForObject("SELECT value FROM u2_business WHERE id=?", Integer.class, f.id));
            assertTrue(engine.getTaskService().getProcessInstanceComments(f.id).isEmpty());
            org.flowable.job.api.Job retry = engine.getManagementService().createTimerJobQuery().processInstanceId(f.id).singleResult();
            assertNotNull(retry, "native failure handler must persist a retry timer");
            assertEquals(job.getRetries() - 1, retry.getRetries());
            assertTrue(retry.getExceptionMessage().contains("native fail once"));
            runNativeJob(engine.getManagementService().moveTimerToExecutableJob(retry.getId()));
            assertEquals("finalReview", task(f.id).getTaskDefinitionKey());
            assertEquals(2, attempts.get());
            assertEquals(1, jdbc.queryForObject("SELECT value FROM u2_business WHERE id=?", Integer.class, f.id));
            assertEquals(0, marker(f.id));
            assertEquals(1, engine.getTaskService().getProcessInstanceComments(f.id).size());
            assertEquals(0, engine.getManagementService().createJobQuery().processInstanceId(f.id).count());
            assertEquals(0, engine.getManagementService().createTimerJobQuery().processInstanceId(f.id).count());
            assertEquals(0, engine.getRuntimeService().createExecutionQuery().processInstanceId(f.id).activityId("reviewTimeout").count());
        });
    }

    @Test void nativeTimerWaitsForRootPolicyBeforeHoldingAnyEngineRecordLock() throws Exception {
        Fixture parent = parallelCall(oneShotApproveTimeout());
        String child = childAtReview(parent);
        for (String id : List.of(parent.id, child)) {
            jdbc.update("INSERT INTO bpm_initiator_withdraw_state(tenant_id,process_instance_id) VALUES(1,?)", id);
        }
        String timerId = engine.getManagementService().createTimerJobQuery().processInstanceId(child).singleResult().getId();
        org.flowable.job.api.Job job = engine.getManagementService().moveTimerToExecutableJob(timerId);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        java.util.concurrent.atomic.AtomicReference<Future<?>> running = new java.util.concurrent.atomic.AtomicReference<>();
        try {
            transaction(4, () -> {
                jdbc.queryForObject("SELECT generation FROM bpm_initiator_withdraw_state WHERE tenant_id=1 AND process_instance_id=? FOR UPDATE", Long.class, parent.id);
                running.set(executor.submit(() -> withProductionTimerListener(() -> runNativeJob(job))));
                org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(5)).until(() ->
                        !waitingPolicyTransactions(parent.id).isEmpty());
                String waiter = waitingPolicyTransactions(parent.id).get(0);
                assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM performance_schema.data_locks WHERE ENGINE_TRANSACTION_ID=? "
                                + "AND OBJECT_SCHEMA=DATABASE() AND OBJECT_NAME LIKE 'ACT\\_RU\\_%' AND LOCK_TYPE='RECORD' AND LOCK_STATUS='GRANTED'",
                        Integer.class, waiter), "timer must not hold engine records while waiting for policy");
                assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM performance_schema.data_locks WHERE ENGINE_TRANSACTION_ID=? "
                                + "AND OBJECT_SCHEMA=DATABASE() AND OBJECT_NAME='bpm_initiator_withdraw_state' AND LOCK_TYPE='RECORD' AND LOCK_DATA LIKE ?",
                        Integer.class, waiter, "%" + child + "%"), "root policy must precede child policy");
                // Actual reverse-direction probes must not block: the timer owns no engine row yet.
                for (String id : List.of(parent.id, child)) {
                    assertEquals(id, jdbc.queryForObject("SELECT ID_ FROM ACT_RU_EXECUTION WHERE ID_=? FOR UPDATE", String.class, id));
                }
                return null;
            });
            running.get().get(20, TimeUnit.SECONDS);
            assertEquals("finalReview", task(child).getTaskDefinitionKey());
            assertEquals(0, marker(parent.id)); assertEquals(0, marker(child));
            assertEquals(0, engine.getManagementService().createTimerJobQuery().processInstanceId(child).count());
        } finally { executor.shutdownNow(); }
    }

    @ParameterizedTest @ValueSource(ints = {2, 4})
    void oldTimerWaitingOnWithdrawalCannotApproveResubmittedSameId(int isolation) throws Exception {
        Fixture f = oneShotApproveTimeout();
        jdbc.update("INSERT INTO bpm_initiator_withdraw_state(tenant_id,process_instance_id) VALUES(1,?)", f.id);
        String timerId = engine.getManagementService().createTimerJobQuery().processInstanceId(f.id).singleResult().getId();
        org.flowable.job.api.Job oldJob = engine.getManagementService().moveTimerToExecutableJob(timerId);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        java.util.concurrent.atomic.AtomicReference<Future<Throwable>> running = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<String> newTask = new java.util.concurrent.atomic.AtomicReference<>();
        try {
            transaction(isolation, () -> {
                jdbc.queryForObject("SELECT generation FROM bpm_initiator_withdraw_state WHERE tenant_id=1 AND process_instance_id=? FOR UPDATE", Long.class, f.id);
                running.set(executor.submit(() -> {
                    try {
                        withProductionTimerListener(() -> transaction(isolation, () -> {
                            jdbc.queryForObject("SELECT COUNT(*) FROM ACT_RU_TASK", Long.class);
                            engine.getManagementService().executeJob(oldJob.getId());
                            return null;
                        }));
                        return null;
                    } catch (Throwable failure) { return failure; }
                }));
                org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(5)).until(() -> !waitingPolicyTransactions(f.id).isEmpty());
                withdraw(f.id);
                submit(f);
                newTask.set(task(f.id).getId());
                assertNotEquals(f.task, newTask.get());
                return null;
            });
            Throwable failure = running.get().get(20, TimeUnit.SECONDS);
            assertNotNull(failure, "stale timer must fail its original transaction");
            while (!(failure instanceof ServiceException) && failure.getCause() != null) failure = failure.getCause();
            assertEquals(INITIATOR_WITHDRAW_CONCURRENT_CHANGE.getCode(), assertInstanceOf(ServiceException.class, failure).getCode());
            assertEquals(f.id, engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(f.id).singleResult().getId());
            assertEquals(newTask.get(), task(f.id).getId());
            assertEquals("review", task(f.id).getTaskDefinitionKey());
            assertEquals(0, marker(f.id));
            assertEquals(0, engine.getManagementService().createJobQuery().jobId(oldJob.getId()).count());
            assertEquals(0, engine.getRuntimeService().createExecutionQuery().executionId(oldJob.getExecutionId()).count());
            org.flowable.job.api.Job newTimer = engine.getManagementService().createTimerJobQuery().processInstanceId(f.id).singleResult();
            assertNotNull(newTimer); assertNotEquals(oldJob.getExecutionId(), newTimer.getExecutionId());
            assertTrue(engine.getTaskService().getTaskComments(newTask.get()).isEmpty());
            // Re-delivery under a fresh snapshot cannot bypass immutable job/execution binding either.
            int comments = engine.getTaskService().getProcessInstanceComments(f.id).size();
            withProductionTimerListener(() -> assertThrows(RuntimeException.class, () -> engine.getManagementService().executeCommand(context -> {
                org.flowable.engine.impl.cfg.ProcessEngineConfigurationImpl cfg = (org.flowable.engine.impl.cfg.ProcessEngineConfigurationImpl) engine.getProcessEngineConfiguration();
                cfg.getEventDispatcher().dispatchEvent(org.flowable.engine.delegate.event.impl.FlowableEventBuilder.createEntityEvent(
                        org.flowable.common.engine.api.delegate.event.FlowableEngineEventType.TIMER_FIRED, oldJob), cfg.getEngineCfgKey());
                return null;
            })));
            assertEquals(newTask.get(), task(f.id).getId());
            assertEquals(comments, engine.getTaskService().getProcessInstanceComments(f.id).size());
        } finally { executor.shutdownNow(); }
    }

    @ParameterizedTest @ValueSource(strings = {"", "not-a-tenant", "999", "9223372036854775808"})
    void timerRejectsInvalidOrForeignEventTenantWithoutAmbientFallback(String eventTenant) {
        Fixture f = oneShotApproveTimeout();
        String timerId = engine.getManagementService().createTimerJobQuery().processInstanceId(f.id).singleResult().getId();
        org.flowable.job.api.Job job = engine.getManagementService().moveTimerToExecutableJob(timerId);
        org.flowable.engine.delegate.event.AbstractFlowableEngineEventListener corruptEventTenant =
                new org.flowable.engine.delegate.event.AbstractFlowableEngineEventListener() {
                    @Override protected void timerFired(org.flowable.common.engine.api.delegate.event.FlowableEngineEntityEvent event) {
                        ((org.flowable.job.service.impl.persistence.entity.JobEntity) event.getEntity()).setTenantId(eventTenant);
                    }
                };
        engine.getRuntimeService().addEventListener(corruptEventTenant, org.flowable.common.engine.api.delegate.event.FlowableEngineEventType.TIMER_FIRED);
        try {
            withProductionTimerListener(() -> assertThrows(RuntimeException.class, () -> engine.getManagementService().executeJob(job.getId())));
            assertEquals(1L, TenantContextHolder.getTenantId());
            assertEquals(f.task, task(f.id).getId());
            assertEquals(0, stateRows(f.id));
            assertTrue(engine.getTaskService().getProcessInstanceComments(f.id).isEmpty());
            org.flowable.job.api.Job retry = engine.getManagementService().createTimerJobQuery().processInstanceId(f.id).singleResult();
            assertNotNull(retry, "tenant rejection must retain the native retry job");
            assertEquals("1", retry.getTenantId());
            assertEquals(job.getRetries() - 1, retry.getRetries());
            verifyNoInteractions(notifications);
        } finally { engine.getRuntimeService().removeEventListener(corruptEventTenant); }
    }

    private List<String> waitingPolicyTransactions(String instanceId) {
        return jdbc.query("SELECT DISTINCT l.ENGINE_TRANSACTION_ID FROM performance_schema.data_lock_waits w "
                        + "JOIN performance_schema.data_locks l ON l.ENGINE_LOCK_ID=w.REQUESTING_ENGINE_LOCK_ID "
                        + "WHERE l.OBJECT_SCHEMA=DATABASE() AND l.OBJECT_NAME='bpm_initiator_withdraw_state' AND l.LOCK_DATA LIKE ?",
                (rs, row) -> rs.getString(1), "%" + instanceId + "%");
    }

    private void fireTimeout(Fixture f) {
        String timerId = engine.getManagementService().createTimerJobQuery().processInstanceId(f.id).singleResult().getId();
        String jobId = engine.getManagementService().moveTimerToExecutableJob(timerId).getId();
        withProductionTimerListener(() -> engine.getManagementService().executeJob(jobId));
    }

    @Test void automaticCallbackFailureRollsBackTimerTransactionCompletely() {
        Fixture f = oneShotApproveTimeout();
        doThrow(new IllegalStateException("automatic notification failure")).when(notifications)
                .sendTaskEventNotification(any(), any(), any(), any(), any());
        assertThrows(RuntimeException.class, () -> fireTimeout(f));
        verify(notifications).sendTaskEventNotification(any(), any(), any(), any(), any());
        assertEquals(f.task, task(f.id).getId());
        assertEquals(0, stateRows(f.id));
        assertTrue(engine.getTaskService().getProcessInstanceComments(f.id).isEmpty());
        assertStatus(f.id, BpmProcessInstanceStatusEnum.RUNNING);
    }

    @ParameterizedTest @ValueSource(ints = {0, 1, 2})
    void callActivityChildHumanMustDenyParentWithdrawal(int childMode) {
        Fixture parent = serialCall(fixture(childMode));
        // Fixture-only parent transition: the ONLY human outcome is in the independently called instance.
        engine.getTaskService().complete(parent.task);
        String child = engine.getRuntimeService().createProcessInstanceQuery().superProcessInstanceId(parent.id).singleResult().getId();
        assertThrows(ServiceException.class, () -> withdraw(child)); // Existing child-self restriction is retained.
        engine.getTaskService().complete(task(child).getId());
        approve(task(child).getId());
        service.approveTask(2L, new BpmTaskApproveReqVO().setId(task(child).getId()).setReason("child human final"));
        assertEquals(childMode == 1 ? 1 : 0, marker(child));
        assertEquals(1, marker(parent.id));
        assertEquals("finalReview", task(parent.id).getTaskDefinitionKey());
        assertEquals(INITIATOR_WITHDRAW_HUMAN_RESULT.getCode(), assertThrows(ServiceException.class, () -> withdraw(parent.id)).getCode());
    }

    private Fixture serialCall(Fixture childDefinition) { return serialCall(childDefinition, false); }

    private Fixture serialCall(Fixture childDefinition, boolean multiInstance) { return serialCall(childDefinition, multiInstance, false); }

    private Fixture serialCall(Fixture childDefinition, boolean multiInstance, boolean sequential) {
        String childKey = engine.getRepositoryService().getProcessDefinition(childDefinition.definition).getKey();
        return fixture(1, model -> {
            CallActivity call = new CallActivity(); call.setId("call"); call.setCalledElement(childKey);
            if (multiInstance) {
                MultiInstanceLoopCharacteristics loop = new MultiInstanceLoopCharacteristics();
                loop.setSequential(sequential); loop.setLoopCardinality("2"); call.setLoopCharacteristics(loop);
            }
            model.getMainProcess().getFlowElements().stream().filter(SequenceFlow.class::isInstance).map(SequenceFlow.class::cast)
                    .filter(flow -> "review".equals(flow.getSourceRef())).forEach(flow -> flow.setTargetRef("call"));
            model.getMainProcess().addFlowElement(call);
            model.getMainProcess().addFlowElement(new SequenceFlow("call", "finalReview"));
        });
    }

    @Test void parentWaitingOnlyOnCallActivityCanWithdrawWithoutInventingNoTaskPolicy() {
        Fixture parent = serialCall(fixture(2));
        engine.getTaskService().complete(parent.task);
        String child = childAtReview(parent);
        assertNull(task(parent.id));
        withdraw(parent.id);
        assertEquals(START_USER_NODE_ID, task(parent.id).getTaskDefinitionKey());
        assertStatus(parent.id, BpmProcessInstanceStatusEnum.NOT_START);
        assertEquals(0, engine.getTaskService().createTaskQuery().processInstanceId(child).count());
        assertEquals(parent.id, transaction(4, () -> submit(parent)));
        engine.getTaskService().complete(task(parent.id).getId());
        assertNotEquals(child, childAtReview(parent));
        assertEquals(0, marker(parent.id));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void multiInstanceCallWithdrawalProducesOneStartAndNoLiveCalledInstances(boolean sequential) {
        Fixture parent = serialCall(timedChildDefinition(), true, sequential);
        engine.getTaskService().complete(parent.task);
        List<ProcessInstance> children = engine.getRuntimeService().createProcessInstanceQuery().superProcessInstanceId(parent.id).list();
        assertEquals(sequential ? 1 : 2, children.size());
        children.forEach(child -> engine.getTaskService().complete(task(child.getId()).getId()));
        children.forEach(child -> assertEquals(1, engine.getManagementService().createTimerJobQuery().processInstanceId(child.getId()).count()));
        assertEquals("native root-only probe succeeded; rollback fixture", assertThrows(IllegalStateException.class, () -> transaction(4, () -> {
            String scope = engine.getRuntimeService().createExecutionQuery().processInstanceId(parent.id).list().stream()
                    .filter(e -> "call".equals(e.getActivityId()) && parent.id.equals(e.getParentId())).findFirst().orElseThrow().getId();
            engine.getRuntimeService().createChangeActivityStateBuilder().processInstanceId(parent.id)
                    .moveExecutionsToSingleActivityId(List.of(scope), START_USER_NODE_ID).changeState();
            assertEquals(1, engine.getTaskService().createTaskQuery().processInstanceId(parent.id).count());
            assertEquals(0, engine.getRuntimeService().createProcessInstanceQuery().superProcessInstanceId(parent.id).count());
            throw new IllegalStateException("native root-only probe succeeded; rollback fixture");
        })).getMessage());
        jdbc.update("INSERT INTO u2_business(id,value) VALUES (?,0)", parent.id);
        assertThrows(IllegalStateException.class, () -> transaction(4, () -> {
            jdbc.update("UPDATE u2_business SET value=1 WHERE id=?", parent.id); withdraw(parent.id);
            throw new IllegalStateException("business failure after MI subtree move");
        }));
        assertEquals(0, stateRows(parent.id));
        assertNull(task(parent.id));
        assertEquals(0, jdbc.queryForObject("SELECT value FROM u2_business WHERE id=?", Integer.class, parent.id));
        children.forEach(child -> {
            assertEquals("review", task(child.getId()).getTaskDefinitionKey());
            assertEquals(1, engine.getManagementService().createTimerJobQuery().processInstanceId(child.getId()).count());
        });
        withdraw(parent.id);
        assertEquals(1, engine.getTaskService().createTaskQuery().processInstanceId(parent.id).count());
        assertEquals(START_USER_NODE_ID, task(parent.id).getTaskDefinitionKey());
        for (ProcessInstance child : children) {
            assertEquals(0, engine.getTaskService().createTaskQuery().processInstanceId(child.getId()).count());
            assertEquals(0, engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(child.getId()).count());
            assertEquals(0, engine.getManagementService().createTimerJobQuery().processInstanceId(child.getId()).count());
            assertEquals(0, engine.getManagementService().createJobQuery().processInstanceId(child.getId()).count());
        }
        assertStatus(parent.id, BpmProcessInstanceStatusEnum.NOT_START);
    }

    @Test void legitimateChildStarterStillCannotWithdrawChildItself() {
        Fixture childDefinition = fixture(2);
        // Default callActivity has no starter. Seed legitimate fixture metadata BEFORE the engine INSERT (immutable afterwards).
        org.flowable.engine.delegate.event.AbstractFlowableEngineEventListener seedStarter =
                new org.flowable.engine.delegate.event.AbstractFlowableEngineEventListener() {
                    @Override protected void taskCreated(org.flowable.common.engine.api.delegate.event.FlowableEngineEntityEvent event) {
                        Task created = (Task) event.getEntity();
                        org.flowable.engine.impl.util.CommandContextUtil.getExecutionEntityManager()
                                .findById(created.getProcessInstanceId()).setStartUserId("1");
                    }
                };
        engine.getRuntimeService().addEventListener(seedStarter, org.flowable.common.engine.api.delegate.event.FlowableEngineEventType.TASK_CREATED);
        Fixture parent;
        try { parent = parallelCall(childDefinition); }
        finally { engine.getRuntimeService().removeEventListener(seedStarter); }
        String child = childAtReview(parent);
        assertEquals("1", engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(child).singleResult().getStartUserId());
        assertEquals(cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.PROCESS_INSTANCE_CANCEL_CHILD_FAIL_NOT_ALLOW.getCode(),
                assertThrows(ServiceException.class, () -> withdraw(child)).getCode());
        assertEquals(0, stateRows(parent.id));
    }

    private Fixture parallelCall(Fixture childDefinition) {
        String childKey = engine.getRepositoryService().getProcessDefinition(childDefinition.definition).getKey();
        return fixture(1, model -> {
            CallActivity call = new CallActivity(); call.setId("call"); call.setCalledElement(childKey);
            ParallelGateway fork = new ParallelGateway(); fork.setId("fork");
            ParallelGateway join = new ParallelGateway(); join.setId("join");
            model.getMainProcess().getFlowElements().stream().filter(SequenceFlow.class::isInstance).map(SequenceFlow.class::cast)
                    .forEach(flow -> {
                        if (START_USER_NODE_ID.equals(flow.getSourceRef())) flow.setTargetRef("fork");
                        if ("review".equals(flow.getSourceRef())) flow.setTargetRef("join");
                    });
            for (FlowElement element : List.of(call, fork, join, new SequenceFlow("fork", "review"),
                    new SequenceFlow("fork", "call"), new SequenceFlow("call", "join"), new SequenceFlow("join", "finalReview"))) {
                model.getMainProcess().addFlowElement(element);
            }
        });
    }
    private String childAtReview(Fixture parent) {
        String child = engine.getRuntimeService().createProcessInstanceQuery().superProcessInstanceId(parent.id).singleResult().getId();
        engine.getTaskService().complete(task(child).getId());
        return child;
    }

    @ParameterizedTest @ValueSource(ints = {2, 4})
    void childHumanWinsAndParentWithdrawalRollsBack(int isolation) throws Exception {
        Fixture parent = parallelCall(fixture(2));
        String child = childAtReview(parent);
        String childTask = task(child).getId();
        race(isolation, () -> approve(childTask), () -> withdraw(parent.id), INITIATOR_WITHDRAW_HUMAN_RESULT.getCode());
        assertEquals(1, marker(parent.id));
        assertEquals(0, stateRows(child));
        assertEquals("review", task(parent.id).getTaskDefinitionKey());
        assertEquals("finalReview", task(child).getTaskDefinitionKey());
        assertTrue(engine.getTaskService().getProcessInstanceComments(parent.id).isEmpty());
    }

    @ParameterizedTest @ValueSource(ints = {2, 4})
    void parentWithdrawalWinsAndLeavesNoApprovableOldChild(int isolation) throws Exception {
        Fixture parent = parallelCall(fixture(2));
        String child = childAtReview(parent);
        String childTask = task(child).getId();
        race(isolation, () -> withdraw(parent.id), () -> approve(childTask), INITIATOR_WITHDRAW_CONCURRENT_CHANGE.getCode());
        assertEquals(0, marker(parent.id));
        assertEquals(0, stateRows(child));
        assertEquals(START_USER_NODE_ID, task(parent.id).getTaskDefinitionKey());
        assertStatus(parent.id, BpmProcessInstanceStatusEnum.NOT_START);
        assertEquals(0, engine.getTaskService().createTaskQuery().processInstanceId(child).count(), "withdraw must not leave an approvable old child");
        assertEquals(0, engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(child).count());
    }

    @Test void childFailureRollsBackEveryAncestorMarkerAndBusiness() {
        Fixture parent = parallelCall(fixture(1));
        String child = childAtReview(parent);
        String childTask = task(child).getId();
        jdbc.update("INSERT INTO u2_business(id,value) VALUES (?,0)", parent.id);
        doThrow(new IllegalStateException("child notification failure")).when(notifications)
                .sendTaskEventNotification(any(), any(), any(), any(), any());
        assertThrows(IllegalStateException.class, () -> transaction(4, () -> {
            jdbc.update("UPDATE u2_business SET value=1 WHERE id=?", parent.id); approve(childTask); return null;
        }));
        assertEquals(0, stateRows(parent.id));
        assertEquals(0, stateRows(child));
        assertEquals(childTask, task(child).getId());
        assertEquals(0, jdbc.queryForObject("SELECT value FROM u2_business WHERE id=?", Integer.class, parent.id));
        assertTrue(engine.getTaskService().getProcessInstanceComments(child).isEmpty());
    }

    private Fixture timedChildDefinition() {
        return fixture(2, model -> {
            BoundaryEvent boundary = new BoundaryEvent(); boundary.setId("childTimer");
            boundary.setAttachedToRef((UserTask) model.getFlowElement("review")); boundary.setCancelActivity(true);
            TimerEventDefinition timer = new TimerEventDefinition(); timer.setTimeDuration("PT1H");
            boundary.addEventDefinition(timer); model.getMainProcess().addFlowElement(boundary);
            model.getMainProcess().addFlowElement(new SequenceFlow("childTimer", "finalReview"));
        });
    }

    @ParameterizedTest @ValueSource(ints = {2, 4})
    void withdrawFailureRestoresWholeChildTreeAndResubmitCreatesCleanNewChild(int isolation) {
        Fixture parent = parallelCall(timedChildDefinition());
        String child = childAtReview(parent);
        String childTask = task(child).getId();
        String timer = engine.getManagementService().createTimerJobQuery().processInstanceId(child).singleResult().getId();
        jdbc.update("INSERT INTO u2_business(id,value) VALUES (?,0)", parent.id);
        doThrow(new IllegalStateException("withdraw tree notification failure")).when(notifications)
                .sendTaskEventNotification(any(), any(), any(), any(), any());
        assertThrows(IllegalStateException.class, () -> transaction(isolation, () -> {
            jdbc.update("UPDATE u2_business SET value=1 WHERE id=?", parent.id); withdraw(parent.id); return null;
        }));
        assertEquals(parent.task, task(parent.id).getId());
        assertEquals(childTask, task(child).getId());
        assertEquals(timer, engine.getManagementService().createTimerJobQuery().processInstanceId(child).singleResult().getId());
        assertEquals(0, stateRows(parent.id));
        assertEquals(0, jdbc.queryForObject("SELECT value FROM u2_business WHERE id=?", Integer.class, parent.id));
        assertTrue(engine.getTaskService().getProcessInstanceComments(parent.id).isEmpty());
        assertStatus(parent.id, BpmProcessInstanceStatusEnum.RUNNING);
        doNothing().when(notifications).sendTaskEventNotification(any(), any(), any(), any(), any());
        withdraw(parent.id);
        assertEquals(0, engine.getTaskService().createTaskQuery().processInstanceId(child).count());
        assertEquals(0, engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(child).count());
        assertEquals(0, engine.getManagementService().createTimerJobQuery().processInstanceId(child).count());
        assertEquals(0, engine.getManagementService().createJobQuery().processInstanceId(child).count());
        assertStatus(parent.id, BpmProcessInstanceStatusEnum.NOT_START);
        assertTaskResult(parent.task, BpmTaskStatusEnum.WITHDRAW);
        assertEquals(parent.id, transaction(isolation, () -> submit(parent)));
        String freshChild = childAtReview(parent);
        assertNotEquals(child, freshChild);
        assertEquals(1, engine.getManagementService().createTimerJobQuery().processInstanceId(freshChild).count());
        assertEquals(0, marker(parent.id));
        assertEquals(1, engine.getRuntimeService().createProcessInstanceQuery().processInstanceBusinessKey(parent.businessKey).count());
    }

    @Test void locatorSeesOwnUncommittedAndRejectsAmbiguousButIsolatesTenant() {
        transaction(4, () -> {
            Fixture f = fixture(1);
            String key = engine.getRepositoryService().getProcessDefinition(f.definition).getKey();
            assertEquals(f.id, policy.findActiveInstanceId(key, f.businessKey));
            return null;
        });
        Fixture f = fixture(1);
        String key = engine.getRepositoryService().getProcessDefinition(f.definition).getKey();
        engine.getRuntimeService().startProcessInstanceById(f.definition, f.businessKey);
        assertEquals(INITIATOR_WITHDRAW_CONCURRENT_CHANGE.getCode(), assertThrows(ServiceException.class,
                () -> policy.findActiveInstanceId(key, f.businessKey)).getCode());
        TenantContextHolder.setTenantId(2L);
        assertNull(policy.findActiveInstanceId(key, f.businessKey));
        TenantContextHolder.setTenantId(1L);
        assertNull(policy.findActiveInstanceId(key, " \t "));
    }

    @Test void committedLocatorDefeatsAmbientRepeatableReadFalseNull() throws Exception {
        CountDownLatch snapshot = new CountDownLatch(1);
        CountDownLatch committed = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<Fixture> fixture = new java.util.concurrent.atomic.AtomicReference<>();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> reader = executor.submit(() -> {
                TenantContextHolder.setTenantId(1L);
                try { transaction(4, () -> {
                    jdbc.queryForObject("SELECT COUNT(*) FROM ACT_RU_EXECUTION", Long.class);
                    snapshot.countDown(); await(committed);
                    Fixture f = fixture.get();
                    assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM ACT_RU_EXECUTION WHERE ID_=?", Integer.class, f.id));
                    assertEquals(f.id, policy.findActiveInstanceId(engine.getRepositoryService().getProcessDefinition(f.definition).getKey(), f.businessKey));
                    return null;
                }); } finally { TenantContextHolder.clear(); }
            });
            assertTrue(snapshot.await(10, TimeUnit.SECONDS));
            fixture.set(fixture(1)); committed.countDown(); reader.get(20, TimeUnit.SECONDS);
        } finally { committed.countDown(); executor.shutdownNow(); }
    }

    private void withProductionTaskListeners(Runnable body) {
        cn.iocoder.yudao.module.bpm.framework.flowable.core.listener.BpmTaskEventListener listener =
                new cn.iocoder.yudao.module.bpm.framework.flowable.core.listener.BpmTaskEventListener();
        set(listener, "taskService", service);
        engine.getRuntimeService().addEventListener(listener,
                org.flowable.common.engine.api.delegate.event.FlowableEngineEventType.TASK_CREATED,
                org.flowable.common.engine.api.delegate.event.FlowableEngineEventType.TASK_ASSIGNED);
        try (MockedStatic<SpringUtil> beans = mockStatic(SpringUtil.class)) {
            beans.when(() -> SpringUtil.getBean(BpmInitiatorWithdrawPolicyService.class)).thenReturn(policy);
            beans.when(() -> SpringUtil.getBean(BpmTaskServiceImpl.class)).thenReturn(service);
            body.run();
        } finally { engine.getRuntimeService().removeEventListener(listener); }
    }

    @Test void nestedMixedModeHumanMarksAllRelatedModeOneAncestors() {
        Fixture leafDefinition = fixture(2);
        Fixture middleDefinition = parallelCall(leafDefinition);
        Fixture root = parallelCall(middleDefinition);
        String middle = childAtReview(root);
        String leaf = engine.getRuntimeService().createProcessInstanceQuery().superProcessInstanceId(middle).singleResult().getId();
        engine.getTaskService().complete(task(leaf).getId());
        approve(task(leaf).getId());
        assertEquals(1, marker(root.id));
        assertEquals(1, marker(middle));
        assertEquals(0, stateRows(leaf));
        assertEquals(INITIATOR_WITHDRAW_HUMAN_RESULT.getCode(), assertThrows(ServiceException.class, () -> withdraw(root.id)).getCode());
    }

    @Test void nestedWithdrawalCleansEntireCalledTreeAndDeepTimer() {
        Fixture root = parallelCall(parallelCall(timedChildDefinition()));
        String middle = childAtReview(root);
        String leaf = engine.getRuntimeService().createProcessInstanceQuery().superProcessInstanceId(middle).singleResult().getId();
        engine.getTaskService().complete(task(leaf).getId());
        assertEquals(1, engine.getManagementService().createTimerJobQuery().processInstanceId(leaf).count());
        withdraw(root.id);
        assertEquals(START_USER_NODE_ID, task(root.id).getTaskDefinitionKey());
        assertStatus(root.id, BpmProcessInstanceStatusEnum.NOT_START);
        for (String child : List.of(middle, leaf)) {
            assertEquals(0, engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(child).count());
            assertEquals(0, engine.getTaskService().createTaskQuery().processInstanceId(child).count());
            assertEquals(0, engine.getManagementService().createTimerJobQuery().processInstanceId(child).count());
        }
        assertEquals(0, marker(root.id));
    }

    @Test void realChildCompletionListenersLegitimatelyContinueParentWithoutCrossTargetFailure() {
        Fixture parent = parallelCall(fixture(2));
        String child = childAtReview(parent);
        // Complete the sibling fixture task without a human result, so child completion proceeds to parent finalReview.
        engine.getTaskService().complete(parent.task);
        withProductionTaskListeners(() -> {
            approve(task(child).getId());
            service.approveTask(2L, new BpmTaskApproveReqVO().setId(task(child).getId()).setReason("child finishes"));
        });
        assertEquals(0, engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(child).count());
        Task continued = task(parent.id);
        assertEquals("finalReview", continued.getTaskDefinitionKey());
        assertEquals(BpmTaskStatusEnum.RUNNING.getStatus(), continued.getTaskLocalVariables().get(BpmnVariableConstants.TASK_VARIABLE_STATUS));
        assertEquals(1, marker(parent.id));
        verify(notifications, atLeastOnce()).sendTaskEventNotification(any(), argThat(t -> parent.id.equals(t.getProcessInstanceId())), any(), any(), any());
    }

    @Test void realAutomaticChildCreatedAndAssignedCallbacksDoNotMarkAncestors() {
        Fixture childDefinition = fixture(2, model -> cn.iocoder.yudao.module.bpm.framework.flowable.core.util.BpmnModelUtils.addExtensionElement(
                model.getFlowElement("review"), cn.iocoder.yudao.module.bpm.enums.task.BpmnModelConstants.USER_TASK_APPROVE_TYPE, "2"));
        Fixture parent = parallelCall(childDefinition);
        String child = engine.getRuntimeService().createProcessInstanceQuery().superProcessInstanceId(parent.id).singleResult().getId();
        withProductionTaskListeners(() -> engine.getTaskService().complete(task(child).getId()));
        assertEquals("finalReview", task(child).getTaskDefinitionKey());
        assertEquals(0, marker(parent.id));
        assertEquals(0, stateRows(child));
        withdraw(parent.id);
    }

    @Test void rejectInternalReturnKeepsServerChosenHumanOrAutomaticSource() {
        java.util.function.Consumer<BpmnModel> rejectToStart = model -> {
            cn.iocoder.yudao.module.bpm.framework.flowable.core.util.BpmnModelUtils.addExtensionElement(model.getFlowElement("review"),
                    cn.iocoder.yudao.module.bpm.enums.task.BpmnModelConstants.USER_TASK_REJECT_HANDLER_TYPE, "2");
            cn.iocoder.yudao.module.bpm.framework.flowable.core.util.BpmnModelUtils.addExtensionElement(model.getFlowElement("review"),
                    cn.iocoder.yudao.module.bpm.enums.task.BpmnModelConstants.USER_TASK_REJECT_RETURN_TASK_ID, START_USER_NODE_ID);
        };
        Fixture human = fixture(1, rejectToStart);
        service.rejectTask(1L, new BpmTaskRejectReqVO().setId(human.task).setReason("human reject-return"));
        assertEquals(START_USER_NODE_ID, task(human.id).getTaskDefinitionKey());
        assertEquals(1, marker(human.id));
        Fixture automatic = oneShotTimeout(cn.iocoder.yudao.module.bpm.enums.definition.BpmUserTaskTimeoutHandlerTypeEnum.REJECT.getType(), rejectToStart);
        fireTimeout(automatic);
        assertEquals(START_USER_NODE_ID, task(automatic.id).getTaskDefinitionKey());
        assertEquals(0, marker(automatic.id));
    }

    @Test void automaticTimeoutBindsJobTenantAndNeverMarksHuman() {
        Fixture f = oneShotApproveTimeout();
        TenantContextHolder.setTenantId(999L);
        fireTimeout(f);
        assertEquals(999L, TenantContextHolder.getTenantId());
        TenantContextHolder.setTenantId(1L);
        assertEquals("finalReview", task(f.id).getTaskDefinitionKey());
        assertEquals(0, marker(f.id));
        withdraw(f.id);
        assertEquals(START_USER_NODE_ID, task(f.id).getTaskDefinitionKey());
    }
}
