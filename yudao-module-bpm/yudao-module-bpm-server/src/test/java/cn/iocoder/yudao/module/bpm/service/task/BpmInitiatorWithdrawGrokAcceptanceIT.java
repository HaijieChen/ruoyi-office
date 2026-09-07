package cn.iocoder.yudao.module.bpm.service.task;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.event.BpmEventTypeEnum;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.instance.BpmProcessInstanceCancelReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task.*;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmProcessDefinitionInfoDO;
import cn.iocoder.yudao.module.bpm.enums.task.*;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmnVariableConstants;
import cn.iocoder.yudao.module.bpm.service.definition.BpmModelService;
import cn.iocoder.yudao.module.bpm.service.definition.BpmProcessDefinitionService;
import cn.iocoder.yudao.module.bpm.service.notification.BpmNotificationManager;
import cn.iocoder.yudao.module.bpm.service.task.BpmProcessInstanceCopyService;
import org.flowable.bpmn.model.*;
import org.flowable.engine.*;
import org.flowable.job.api.Job;
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

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

import static cn.iocoder.yudao.module.bpm.enums.BpmInitiatorWithdrawErrorCodeConstants.*;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.*;
import static cn.iocoder.yudao.module.bpm.enums.task.BpmnModelConstants.START_USER_NODE_ID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Grok phase2a mysql-isolated NEW cases. Copies MysqlIT engine fixture; does not change product code. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfSystemProperty(named = "bpm.u2.mysql.url", matches = ".+/bpm_u2_test.*")
class BpmInitiatorWithdrawGrokAcceptanceIT {
    private final ThreadLocal<Integer> callbackIsolation = new ThreadLocal<>();
    private ProcessEngine engine;
    private JdbcTemplate jdbc;
    private DataSourceTransactionManager txManager;
    private DriverManagerDataSource ds;
    private BpmTaskServiceImpl service;
    private BpmTaskServiceImpl rawService;
    private BpmInitiatorWithdrawPolicyService policy;
    private BpmInitiatorWithdrawPolicyService rawPolicy;
    private BpmProcessInstanceService instances;
    private BpmProcessInstanceServiceImpl submitService;
    private BpmNotificationManager notifications;
    private BpmProcessDefinitionService definitionService;
    private BpmProcessInstanceCopyService copyService;
    private final Map<String, BpmProcessDefinitionInfoDO> definitions = new ConcurrentHashMap<>();

    @BeforeAll
    void startEngine() {
        ds = new DriverManagerDataSource(System.getProperty("bpm.u2.mysql.url"),
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
        definitionService = mock(BpmProcessDefinitionService.class);
        when(definitionService.getProcessDefinitionInfo(anyString())).thenAnswer(c -> definitions.get(c.getArgument(0)));
        BpmModelService models = mock(BpmModelService.class);
        when(models.getBpmnModelByDefinitionId(anyString())).thenAnswer(c -> engine.getRepositoryService().getBpmnModel(c.getArgument(0)));
        instances = mock(BpmProcessInstanceService.class);
        when(instances.getProcessInstance(anyString())).thenAnswer(c -> engine.getRuntimeService().createProcessInstanceQuery()
                .processInstanceId(c.getArgument(0)).includeProcessVariables().singleResult());
        notifications = mock(BpmNotificationManager.class);
        copyService = mock(BpmProcessInstanceCopyService.class);
        rawPolicy = new BpmInitiatorWithdrawPolicyService();
        set(rawPolicy, "dataSource", ds);
        set(rawPolicy, "taskService", engine.getTaskService());
        set(rawPolicy, "managementService", engine.getManagementService());
        set(rawPolicy, "bpmProcessDefinitionService", definitionService);
        policy = proxy(rawPolicy);
        rawService = new BpmTaskServiceImpl();
        set(rawService, "initiatorWithdrawPolicyService", policy);
        set(rawService, "runtimeService", engine.getRuntimeService());
        set(rawService, "taskService", engine.getTaskService());
        set(rawService, "historyService", engine.getHistoryService());
        set(rawService, "managementService", engine.getManagementService());
        set(rawService, "processInstanceService", instances);
        set(rawService, "bpmProcessDefinitionService", definitionService);
        set(rawService, "modelService", models);
        set(rawService, "notificationManager", notifications);
        set(rawService, "processInstanceCopyService", copyService);
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
        set(rawSubmit, "processDefinitionService", definitionService);
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

    private Fixture fixture(int mode) { return fixture(mode, true, model -> { }); }
    private Fixture fixture(int mode, boolean allowWithdrawTask) { return fixture(mode, allowWithdrawTask, model -> { }); }
    private Fixture fixture(int mode, java.util.function.Consumer<BpmnModel> customize) { return fixture(mode, true, customize); }
    private Fixture fixture(int mode, boolean allowWithdrawTask, java.util.function.Consumer<BpmnModel> customize) {
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
        definitions.put(definition, new BpmProcessDefinitionInfoDO().setInitiatorWithdrawMode(mode).setAllowWithdrawTask(allowWithdrawTask)
                .setAllowCancelRunningProcess(true));
        engine.getIdentityService().setAuthenticatedUserId("1");
        String id;
        try {
            id = transaction(4, () -> engine.getRuntimeService().startProcessInstanceById(definition, key,
                    new HashMap<>(Map.of(BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_STATUS, BpmProcessInstanceStatusEnum.RUNNING.getStatus()))).getId());
        } finally { engine.getIdentityService().setAuthenticatedUserId(null); }
        transaction(4, () -> { engine.getTaskService().complete(task(id).getId()); return null; });
        List<Task> leftover = engine.getTaskService().createTaskQuery().processInstanceId(id).list();
        String taskId = leftover.isEmpty() ? null : leftover.get(0).getId();
        return new Fixture(id, definition, taskId, key);
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
    private void approve(String taskId) { service.approveTask(1L, new BpmTaskApproveReqVO().setId(taskId).setReason("human")); }
    private void withdraw(String id) { service.withdrawProcessToStart(1L, id, "withdraw"); }
    private String submit(Fixture f) {
        String key = engine.getRepositoryService().getProcessDefinition(f.definition).getKey();
        String prior = org.flowable.common.engine.impl.identity.Authentication.getAuthenticatedUserId();
        org.flowable.common.engine.impl.identity.Authentication.setAuthenticatedUserId("outer-authentication");
        try {
            return submitService.submitProcessInstance(1L,
                    new cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO()
                            .setProcessDefinitionKey(key).setBusinessKey(f.businessKey).setVariables(Map.of("resubmitted", true)));
        } finally {
            org.flowable.common.engine.impl.identity.Authentication.setAuthenticatedUserId(prior);
        }
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
    private void fireTimeout(Fixture f) { fireTimeoutOn(f.id); }
    private void fireTimeoutOn(String instanceId) {
        String timerId = engine.getManagementService().createTimerJobQuery().processInstanceId(instanceId).singleResult().getId();
        String jobId = engine.getManagementService().moveTimerToExecutableJob(timerId).getId();
        withProductionTimerListener(() -> engine.getManagementService().executeJob(jobId));
    }
    private void setSuperExecOnDedicatedConnection(String instanceId, String superExec) throws SQLException {
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            c.setAutoCommit(false);
            s.execute("SET FOREIGN_KEY_CHECKS=0");
            try (PreparedStatement u = c.prepareStatement("UPDATE ACT_RU_EXECUTION SET SUPER_EXEC_=? WHERE ID_=?")) {
                if (superExec == null) u.setNull(1, java.sql.Types.VARCHAR); else u.setString(1, superExec);
                u.setString(2, instanceId);
                assertEquals(1, u.executeUpdate());
            }
            s.execute("SET FOREIGN_KEY_CHECKS=1");
            c.commit();
        }
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

    @Test void cfg11IsoRunsOriginalDdlTwiceWithoutDamagingState() throws Exception {
        jdbc.execute("CREATE TABLE IF NOT EXISTS bpm_process_definition_info ("
                + "process_definition_id VARCHAR(64) PRIMARY KEY) ENGINE=InnoDB");
        jdbc.update("INSERT IGNORE INTO bpm_process_definition_info(process_definition_id) VALUES('iso-null-def')");
        jdbc.update("INSERT IGNORE INTO bpm_initiator_withdraw_state(tenant_id,process_instance_id,human_result,generation) VALUES(1,'iso-keep',1,7)");
        Path policy = Path.of("/tmp/oa-withdraw-login-test-20260906/sql/mysql/bpm_initiator_withdraw_policy.sql");
        Path state = Path.of("/tmp/oa-withdraw-login-test-20260906/sql/mysql/bpm_initiator_withdraw_state.sql");
        String sql = Files.readString(policy) + "\n" + Files.readString(state);
        for (int i = 0; i < 2; i++) {
            ProcessBuilder pb = new ProcessBuilder("docker", "exec", "-i", "oa-mi-timeout-regression",
                    "bash", "-lc", "export MYSQL_PWD=\"$MYSQL_ROOT_PASSWORD\"; mysql -uroot bpm_u2_test");
            pb.redirectErrorStream(true);
            java.lang.Process proc = pb.start();
            proc.getOutputStream().write(sql.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            proc.getOutputStream().close();
            String out = new String(proc.getInputStream().readAllBytes());
            assertEquals(0, proc.waitFor(), "ddl pass " + (i + 1) + " output=" + out);
            assertFalse(out.toLowerCase().contains("error"), out);
        }
        assertNull(jdbc.queryForObject("SELECT initiator_withdraw_mode FROM bpm_process_definition_info WHERE process_definition_id='iso-null-def'", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT human_result FROM bpm_initiator_withdraw_state WHERE process_instance_id='iso-keep'", Integer.class));
        assertEquals(7, jdbc.queryForObject("SELECT generation FROM bpm_initiator_withdraw_state WHERE process_instance_id='iso-keep'", Integer.class));
        jdbc.update("DELETE FROM bpm_process_definition_info WHERE process_definition_id='iso-null-def'");
        jdbc.update("DELETE FROM bpm_initiator_withdraw_state WHERE process_instance_id='iso-keep'");
    }

    @Test void pol02DirtyModeWithdrawDisabledNoState() {
        Fixture f = fixture(1);
        definitions.put(f.definition, new BpmProcessDefinitionInfoDO().setInitiatorWithdrawMode(99).setAllowWithdrawTask(true));
        assertEquals(INITIATOR_WITHDRAW_DISABLED.getCode(),
                assertThrows(ServiceException.class, () -> withdraw(f.id)).getCode());
        assertEquals(0, stateRows(f.id));
        assertEquals(f.task, task(f.id).getId());
    }

    @Test void pol05ExplicitModeOneIgnoresLegacyFalse() {
        Fixture f = fixture(1, false);
        withdraw(f.id);
        assertEquals(START_USER_NODE_ID, task(f.id).getTaskDefinitionKey());
        assertEquals(0, marker(f.id));
    }

    @Test void wth06NonStarterDenied() {
        Fixture f = fixture(1);
        assertEquals(PROCESS_INSTANCE_CANCEL_FAIL_NOT_SELF.getCode(),
                assertThrows(ServiceException.class, () -> service.withdrawProcessToStart(2L, f.id, "x")).getCode());
        assertEquals(0, stateRows(f.id));
        assertEquals(f.task, task(f.id).getId());
    }

    @Test void wth08RandomIdMissing() {
        assertEquals(PROCESS_INSTANCE_NOT_EXISTS.getCode(),
                assertThrows(ServiceException.class, () -> withdraw("missing-" + UUID.randomUUID())).getCode());
    }

    @Test void wth11NoTaskNoCallActivityRollsBack() {
        Fixture f = fixture(1, model -> {
            model.getMainProcess().removeFlowElement("review");
            ReceiveTask wait = new ReceiveTask(); wait.setId("wait"); model.getMainProcess().addFlowElement(wait);
            model.getMainProcess().getFlowElements().stream().filter(SequenceFlow.class::isInstance).map(SequenceFlow.class::cast)
                    .forEach(flow -> {
                        if ("review".equals(flow.getSourceRef())) flow.setSourceRef("wait");
                        if ("review".equals(flow.getTargetRef())) flow.setTargetRef("wait");
                    });
        });
        assertNull(task(f.id));
        assertThrows(RuntimeException.class, () -> withdraw(f.id));
        assertEquals(0, stateRows(f.id));
        assertTrue(engine.getTaskService().getProcessInstanceComments(f.id).isEmpty());
        assertNotNull(engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(f.id).singleResult());
    }

    @Test void wth12SuccessSendsWithdrawnNotification() {
        Fixture f = fixture(1);
        withdraw(f.id);
        verify(notifications, atLeastOnce()).sendTaskEventNotification(any(), any(), eq(BpmEventTypeEnum.TASK_WITHDRAWN), any(), any());
        assertEquals(START_USER_NODE_ID, task(f.id).getTaskDefinitionKey());
    }

    @Test void wth13ApproverWithdrawUsesLegacySwitch() {
        Fixture f = fixture(0, true);
        String startTask = engine.getHistoryService().createHistoricTaskInstanceQuery().processInstanceId(f.id)
                .taskDefinitionKey(START_USER_NODE_ID).finished().singleResult().getId();
        assertDoesNotThrow(() -> service.withdrawTask(1L, startTask));
        assertEquals(START_USER_NODE_ID, task(f.id).getTaskDefinitionKey());
    }

    @Test void wthEndedRejectsMissingRuntime() {
        Fixture f = fixture(1);
        approve(f.task);
        service.approveTask(2L, new BpmTaskApproveReqVO().setId(task(f.id).getId()).setReason("end"));
        assertNull(engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(f.id).singleResult());
        assertEquals(PROCESS_INSTANCE_NOT_EXISTS.getCode(),
                assertThrows(ServiceException.class, () -> withdraw(f.id)).getCode());
        assertNotNull(engine.getHistoryService().createHistoricProcessInstanceQuery().processInstanceId(f.id).singleResult());
    }

    @Test void wthCancelledThenWithdrawMissingRuntime() {
        Fixture f = fixture(1);
        submitService.cancelProcessInstanceByStartUser(1L, new BpmProcessInstanceCancelReqVO().setId(f.id).setReason("cancel"));
        assertNull(engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(f.id).singleResult());
        assertEquals(PROCESS_INSTANCE_NOT_EXISTS.getCode(),
                assertThrows(ServiceException.class, () -> withdraw(f.id)).getCode());
    }

    @Test void hum04HumanRejectEndingMarks() {
        Fixture f = fixture(1);
        service.rejectTask(1L, new BpmTaskRejectReqVO().setId(f.task).setReason("end-reject"));
        assertEquals(1, marker(f.id));
        assertEquals(PROCESS_INSTANCE_NOT_EXISTS.getCode(),
                assertThrows(ServiceException.class, () -> withdraw(f.id)).getCode());
    }

    @Test void hum13CopyDoesNotMark() {
        Fixture f = fixture(1);
        service.copyTask(1L, new BpmTaskCopyReqVO().setId(f.task).setCopyUserIds(List.of(2L)).setReason("copy"));
        verify(copyService).createProcessInstanceCopy(any(), any(), eq(f.task));
        assertEquals(0, marker(f.id));
        withdraw(f.id);
        assertEquals(START_USER_NODE_ID, task(f.id).getTaskDefinitionKey());
    }

    @Test void hum14BeforeSignChildHumanMarks() {
        Fixture f = fixture(1);
        service.createSignTask(1L, new BpmTaskSignCreateReqVO().setId(f.task).setUserIds(Set.of(2L))
                .setType(BpmTaskSignTypeEnum.BEFORE.getType()).setReason("before"));
        assertEquals(0, marker(f.id));
        Task child = engine.getTaskService().createTaskQuery().processInstanceId(f.id).taskAssignee("2").singleResult();
        assertNotNull(child);
        service.approveTask(2L, new BpmTaskApproveReqVO().setId(child.getId()).setReason("before-child"));
        assertEquals(1, marker(f.id));
        assertEquals(INITIATOR_WITHDRAW_HUMAN_RESULT.getCode(), assertThrows(ServiceException.class, () -> withdraw(f.id)).getCode());
    }

    @Test void aut05EmptyAssigneeAutoReject() {
        Fixture f = fixture(1, model -> {
            UserTask review = (UserTask) model.getFlowElement("review");
            review.setAssignee(null);
            cn.iocoder.yudao.module.bpm.framework.flowable.core.util.BpmnModelUtils.addExtensionElement(review,
                    BpmnModelConstants.USER_TASK_APPROVE_TYPE,
                    cn.iocoder.yudao.module.bpm.enums.definition.BpmUserTaskApproveTypeEnum.USER.getType().toString());
            cn.iocoder.yudao.module.bpm.framework.flowable.core.util.BpmnModelUtils.addExtensionElement(review,
                    BpmnModelConstants.USER_TASK_ASSIGN_EMPTY_HANDLER_TYPE,
                    cn.iocoder.yudao.module.bpm.enums.definition.BpmUserTaskAssignEmptyHandlerTypeEnum.REJECT.getType().toString());
        });
        try (MockedStatic<SpringUtil> beans = mockStatic(SpringUtil.class)) {
            beans.when(() -> SpringUtil.getBean(BpmInitiatorWithdrawPolicyService.class)).thenReturn(policy);
            transaction(4, () -> { service.processTaskCreated(task(f.id)); return null; });
        }
        assertEquals(0, marker(f.id));
        assertNull(engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(f.id).singleResult());
    }

    @Test void aut05EmptyAssigneeAutoApprove() {
        Fixture f = fixture(1, model -> {
            UserTask review = (UserTask) model.getFlowElement("review");
            review.setAssignee(null);
            cn.iocoder.yudao.module.bpm.framework.flowable.core.util.BpmnModelUtils.addExtensionElement(review,
                    BpmnModelConstants.USER_TASK_APPROVE_TYPE,
                    cn.iocoder.yudao.module.bpm.enums.definition.BpmUserTaskApproveTypeEnum.USER.getType().toString());
            cn.iocoder.yudao.module.bpm.framework.flowable.core.util.BpmnModelUtils.addExtensionElement(review,
                    BpmnModelConstants.USER_TASK_ASSIGN_EMPTY_HANDLER_TYPE,
                    cn.iocoder.yudao.module.bpm.enums.definition.BpmUserTaskAssignEmptyHandlerTypeEnum.APPROVE.getType().toString());
        });
        try (MockedStatic<SpringUtil> beans = mockStatic(SpringUtil.class)) {
            beans.when(() -> SpringUtil.getBean(BpmInitiatorWithdrawPolicyService.class)).thenReturn(policy);
            transaction(4, () -> { service.processTaskCreated(task(f.id)); return null; });
        }
        assertEquals(0, marker(f.id));
        assertEquals("finalReview", task(f.id).getTaskDefinitionKey());
        withdraw(f.id);
    }

    @Test void aut06AutoRejectType() {
        Fixture f = fixture(1, model -> cn.iocoder.yudao.module.bpm.framework.flowable.core.util.BpmnModelUtils.addExtensionElement(
                model.getFlowElement("review"), BpmnModelConstants.USER_TASK_APPROVE_TYPE,
                cn.iocoder.yudao.module.bpm.enums.definition.BpmUserTaskApproveTypeEnum.AUTO_REJECT.getType().toString()));
        try (MockedStatic<SpringUtil> beans = mockStatic(SpringUtil.class)) {
            beans.when(() -> SpringUtil.getBean(BpmInitiatorWithdrawPolicyService.class)).thenReturn(policy);
            transaction(4, () -> { service.processTaskCreated(task(f.id)); return null; });
        }
        assertEquals(0, marker(f.id));
        assertNull(engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(f.id).singleResult());
    }

    @Test void aut08GoneInstanceCallbackReturns() {
        Fixture f = fixture(1);
        approve(f.task);
        service.approveTask(2L, new BpmTaskApproveReqVO().setId(task(f.id).getId()).setReason("end"));
        assertDoesNotThrow(() -> transaction(4, () -> { policy.runAfterCompletionInTransaction(f.id, () -> fail("must not run")); return null; }));
    }

    @Test void aut09DelayTimerTriggersReceiveTaskWithoutHumanMarker() {
        Fixture f = fixture(1, model -> {
            ReceiveTask wait = new ReceiveTask(); wait.setId("wait");
            model.getMainProcess().addFlowElement(wait);
            model.getMainProcess().getFlowElements().stream().filter(SequenceFlow.class::isInstance).map(SequenceFlow.class::cast)
                    .forEach(flow -> {
                        if (START_USER_NODE_ID.equals(flow.getSourceRef()) && "review".equals(flow.getTargetRef())) {
                            flow.setTargetRef("wait");
                        }
                    });
            model.getMainProcess().addFlowElement(new SequenceFlow("wait", "review"));
            BoundaryEvent boundary = new BoundaryEvent();
            boundary.setId("delay"); boundary.setAttachedToRef(wait); boundary.setCancelActivity(false);
            TimerEventDefinition timer = new TimerEventDefinition(); timer.setTimeDuration("PT1H");
            boundary.addEventDefinition(timer);
            cn.iocoder.yudao.module.bpm.framework.flowable.core.util.BpmnModelUtils.addExtensionElement(boundary,
                    BpmnModelConstants.BOUNDARY_EVENT_TYPE,
                    cn.iocoder.yudao.module.bpm.enums.definition.BpmBoundaryEventTypeEnum.DELAY_TIMER_TIMEOUT.getType());
            model.getMainProcess().addFlowElement(boundary);
        });
        assertNull(task(f.id));
        assertNotNull(engine.getRuntimeService().createExecutionQuery().processInstanceId(f.id).activityId("wait").singleResult());
        fireTimeout(f);
        assertEquals("review", task(f.id).getTaskDefinitionKey());
        assertEquals(0, marker(f.id));
        assertEquals(0, stateRows(f.id));
        withdraw(f.id);
        assertEquals(START_USER_NODE_ID, task(f.id).getTaskDefinitionKey());
    }

    @Test void aut09ChildProcessTimeoutEndsCalledInstanceWithoutHumanMarker() {
        Fixture childDef = fixture(2);
        String childKey = engine.getRepositoryService().getProcessDefinition(childDef.definition).getKey();
        Fixture parent = fixture(1, model -> {
            CallActivity call = new CallActivity(); call.setId("call"); call.setCalledElement(childKey);
            model.getMainProcess().addFlowElement(call);
            model.getMainProcess().getFlowElements().stream().filter(SequenceFlow.class::isInstance).map(SequenceFlow.class::cast)
                    .forEach(flow -> {
                        if ("review".equals(flow.getSourceRef())) flow.setTargetRef("call");
                    });
            model.getMainProcess().addFlowElement(new SequenceFlow("call", "finalReview"));
            BoundaryEvent boundary = new BoundaryEvent();
            boundary.setId("childTimeout"); boundary.setAttachedToRef(call); boundary.setCancelActivity(false);
            TimerEventDefinition timer = new TimerEventDefinition(); timer.setTimeDuration("PT1H");
            boundary.addEventDefinition(timer);
            cn.iocoder.yudao.module.bpm.framework.flowable.core.util.BpmnModelUtils.addExtensionElement(boundary,
                    BpmnModelConstants.BOUNDARY_EVENT_TYPE,
                    cn.iocoder.yudao.module.bpm.enums.definition.BpmBoundaryEventTypeEnum.CHILD_PROCESS_TIMEOUT.getType());
            model.getMainProcess().addFlowElement(boundary);
        });
        engine.getTaskService().complete(parent.task);
        String child = engine.getRuntimeService().createProcessInstanceQuery().superProcessInstanceId(parent.id).singleResult().getId();
        assertNotNull(task(child));
        fireTimeoutOn(parent.id);
        assertEquals(0, engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(child).count());
        assertEquals("finalReview", task(parent.id).getTaskDefinitionKey());
        assertEquals(0, marker(parent.id));
        withdraw(parent.id);
        assertEquals(START_USER_NODE_ID, task(parent.id).getTaskDefinitionKey());
    }

    @Test void autOverflowNumericTenantIsNumberFormatNotAccessDenied() {
        Fixture f = fixture(1);
        TenantContextHolder.setTenantId(1L);
        NumberFormatException ex = assertThrows(NumberFormatException.class,
                () -> policy.runAfterCompletion(f.id, "9223372036854775808", f.task, () -> fail("must not run")));
        assertNotNull(ex);
        assertEquals(1L, TenantContextHolder.getTenantId());
        assertEquals(0, stateRows(f.id));
        assertEquals(f.task, task(f.id).getId());
    }

    @Test void autTaskGoneCreatedCallbackNoops() {
        Fixture f = fixture(1);
        Task stale = task(f.id);
        withdraw(f.id);
        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(rawService, "processTaskCreatedAfterCompletion", stale.getId()));
        assertEquals(START_USER_NODE_ID, task(f.id).getTaskDefinitionKey());
        assertEquals(0, marker(f.id));
    }

    @Test void sub01bWithdrawAgainAfterResubmit() {
        Fixture f = fixture(1);
        withdraw(f.id);
        assertEquals(f.id, submit(f));
        assertEquals("review", task(f.id).getTaskDefinitionKey());
        assertEquals(0, marker(f.id));
        withdraw(f.id);
        assertEquals(START_USER_NODE_ID, task(f.id).getTaskDefinitionKey());
    }

    @Test void sub05SubmitWithoutStartTask() {
        Fixture f = fixture(1, model -> ((UserTask) model.getFlowElement("review")).setAssignee("2"));
        assertEquals(TASK_NOT_EXISTS.getCode(), assertThrows(ServiceException.class, () -> submit(f)).getCode());
        assertEquals(1, engine.getRuntimeService().createProcessInstanceQuery().processInstanceBusinessKey(f.businessKey).count());
    }

    @Test void sub06AssigneeOnReviewMustNotApproveReview() {
        Fixture f = fixture(1);
        assertEquals("review", task(f.id).getTaskDefinitionKey());
        try {
            submit(f);
            assertEquals("review", task(f.id).getTaskDefinitionKey(),
                    "findStartUserTask must not complete a real review node as resubmit");
        } catch (ServiceException ex) {
            assertEquals(TASK_NOT_EXISTS.getCode(), ex.getCode());
            assertEquals("review", task(f.id).getTaskDefinitionKey());
        }
    }

    @Test void sub06StartNodeResubmitIsLegitimate() {
        Fixture f = fixture(1);
        withdraw(f.id);
        assertEquals(START_USER_NODE_ID, task(f.id).getTaskDefinitionKey());
        assertEquals(f.id, submit(f));
        assertEquals("review", task(f.id).getTaskDefinitionKey());
        assertEquals(0, marker(f.id));
    }

    @Test void sub06CandidateMisHitMustNotApproveReview() {
        Fixture f = fixture(1, model -> {
            UserTask review = (UserTask) model.getFlowElement("review");
            review.setAssignee(null);
            review.setCandidateUsers(new ArrayList<>(List.of("1")));
        });
        assertEquals("review", task(f.id).getTaskDefinitionKey());
        assertNull(task(f.id).getAssignee());
        try {
            submit(f);
            assertEquals("review", task(f.id).getTaskDefinitionKey(),
                    "candidate lookup must not complete a real review node as resubmit");
        } catch (ServiceException ex) {
            assertEquals(TASK_NOT_EXISTS.getCode(), ex.getCode());
            assertEquals("review", task(f.id).getTaskDefinitionKey());
        }
    }

    @Test void ten05UnknownTaskLock() {
        assertEquals(TASK_NOT_EXISTS.getCode(),
                assertThrows(ServiceException.class, () -> transaction(4, () -> {
                    policy.withTaskLock("no-such-task", () -> fail("must not run"));
                    return null;
                })).getCode());
    }

    @Test void ten06NestedDifferentInstanceRejected() {
        Fixture a = fixture(1);
        Fixture b = fixture(1);
        assertEquals(INITIATOR_WITHDRAW_CONCURRENT_CHANGE.getCode(),
                assertThrows(ServiceException.class, () -> transaction(4, () -> policy.withInstanceLock(a.id, () -> {
                    policy.withInstanceLock(b.id, () -> fail("expanded"));
                    return null;
                }))).getCode());
    }

    @Test void ten08SuspendedCycleAndMissingParent() throws Exception {
        Fixture suspended = fixture(1);
        engine.getRuntimeService().suspendProcessInstanceById(suspended.id);
        assertEquals(PROCESS_INSTANCE_NOT_EXISTS.getCode(),
                assertThrows(ServiceException.class, () -> withdraw(suspended.id)).getCode());
        engine.getRuntimeService().activateProcessInstanceById(suspended.id);

        Fixture cycle = fixture(1);
        jdbc.update("UPDATE ACT_RU_EXECUTION SET SUPER_EXEC_=? WHERE ID_=?", cycle.id, cycle.id);
        assertEquals(INITIATOR_WITHDRAW_CONCURRENT_CHANGE.getCode(),
                assertThrows(ServiceException.class, () -> withdraw(cycle.id)).getCode());

        Fixture orphan = fixture(1);
        String originalSuper = jdbc.queryForObject("SELECT SUPER_EXEC_ FROM ACT_RU_EXECUTION WHERE ID_=?", String.class, orphan.id);
        try {
            setSuperExecOnDedicatedConnection(orphan.id, "missing-parent");
            assertEquals("missing-parent", jdbc.queryForObject("SELECT SUPER_EXEC_ FROM ACT_RU_EXECUTION WHERE ID_=?", String.class, orphan.id));
            assertNull(jdbc.queryForObject("SELECT p.PROC_INST_ID_ FROM ACT_RU_EXECUTION e LEFT JOIN ACT_RU_EXECUTION p ON p.ID_=e.SUPER_EXEC_ AND p.TENANT_ID_=e.TENANT_ID_ WHERE e.ID_=?", String.class, orphan.id));
            assertEquals(INITIATOR_WITHDRAW_CONCURRENT_CHANGE.getCode(),
                    assertThrows(ServiceException.class, () -> withdraw(orphan.id)).getCode());
            assertEquals(orphan.task, task(orphan.id).getId());
        } finally {
            setSuperExecOnDedicatedConnection(orphan.id, originalSuper);
        }
    }

    @Test void con04InsertIgnoreRace() throws Exception {
        Fixture f = fixture(1);
        race(4, () -> policy.withInstanceLock(f.id, () -> null), () -> withdraw(f.id), INITIATOR_WITHDRAW_CONCURRENT_CHANGE.getCode());
    }

    @Test void con05HumanWithdrawLockIsPolicyTable() throws Exception {
        Fixture f = fixture(1);
        CountDownLatch mutationDone = new CountDownLatch(1);
        CountDownLatch allowCommit = new CountDownLatch(1);
        CountDownLatch loserStarted = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> a = executor.submit(() -> {
                TenantContextHolder.setTenantId(1L);
                try { transaction(4, () -> { approve(f.task); mutationDone.countDown(); await(allowCommit); return null; }); }
                finally { TenantContextHolder.clear(); }
            });
            assertTrue(mutationDone.await(20, TimeUnit.SECONDS));
            Future<Throwable> b = executor.submit(() -> {
                TenantContextHolder.setTenantId(1L);
                try { return transaction(4, () -> {
                    jdbc.queryForObject("SELECT COUNT(*) FROM ACT_RU_TASK", Long.class);
                    loserStarted.countDown();
                    withdraw(f.id); return null;
                }); } catch (Throwable e) { return e; } finally { TenantContextHolder.clear(); }
            });
            assertTrue(loserStarted.await(10, TimeUnit.SECONDS));
            org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(10)).until(() ->
                    jdbc.queryForObject("SELECT COUNT(*) FROM performance_schema.data_lock_waits w JOIN performance_schema.data_locks l "
                            + "ON l.ENGINE_LOCK_ID=w.REQUESTING_ENGINE_LOCK_ID WHERE l.OBJECT_SCHEMA=DATABASE() AND l.OBJECT_NAME='bpm_initiator_withdraw_state'", Long.class) > 0);
            allowCommit.countDown();
            a.get(20, TimeUnit.SECONDS);
            Throwable failure = b.get(20, TimeUnit.SECONDS);
            assertEquals(INITIATOR_WITHDRAW_HUMAN_RESULT.getCode(), assertInstanceOf(ServiceException.class, failure).getCode());
        } finally { allowCommit.countDown(); executor.shutdownNow(); }
    }

    @Test void cal11CrossTargetRefuseThenCallbackSameTarget() {
        Fixture parent = parallelCall(fixture(2));
        String child = childAtReview(parent);
        assertEquals(INITIATOR_WITHDRAW_CONCURRENT_CHANGE.getCode(),
                assertThrows(ServiceException.class, () -> transaction(4, () -> policy.withInstanceLock(parent.id, () -> {
                    policy.withInstanceLock(child, () -> fail("cross-target"));
                    return null;
                }))).getCode());
        try (MockedStatic<SpringUtil> beans = mockStatic(SpringUtil.class)) {
            beans.when(() -> SpringUtil.getBean(BpmInitiatorWithdrawPolicyService.class)).thenReturn(policy);
            assertDoesNotThrow(() -> policy.runAfterCompletion(child, "1", task(child).getId(), () -> { }));
        }
        assertNotNull(engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(child).singleResult());
    }

    @Test void tmr09ReminderKeepsTaskAndWindow() {
        Fixture f = oneShotTimeout(cn.iocoder.yudao.module.bpm.enums.definition.BpmUserTaskTimeoutHandlerTypeEnum.REMINDER.getType(), model -> { });
        fireTimeout(f);
        assertEquals(f.task, task(f.id).getId());
        assertEquals(0, marker(f.id));
        withdraw(f.id);
        assertEquals(START_USER_NODE_ID, task(f.id).getTaskDefinitionKey());
    }

    @Test void tmr10NullJobDenied() {
        assertEquals(PROCESS_INSTANCE_ACCESS_DENIED.getCode(),
                assertThrows(ServiceException.class, () -> transaction(4, () -> {
                    policy.withTimerJobLock(null, "review", t -> fail("must not run"));
                    return null;
                })).getCode());
    }

    @Test void tmr11EmptyOriginalTasksConflict() {
        Fixture f = oneShotTimeout(cn.iocoder.yudao.module.bpm.enums.definition.BpmUserTaskTimeoutHandlerTypeEnum.APPROVE.getType(), model -> { });
        Job job = engine.getManagementService().moveTimerToExecutableJob(
                engine.getManagementService().createTimerJobQuery().processInstanceId(f.id).singleResult().getId());
        jdbc.update("DELETE FROM ACT_RU_TASK WHERE ID_=?", f.task);
        withProductionTimerListener(() -> {
            Throwable thrown = assertThrows(RuntimeException.class, () -> engine.getManagementService().executeJob(job.getId()));
            while (thrown.getCause() != null && !(thrown instanceof ServiceException)) thrown = thrown.getCause();
            assertEquals(INITIATOR_WITHDRAW_CONCURRENT_CHANGE.getCode(), assertInstanceOf(ServiceException.class, thrown).getCode());
        });
    }

    @Test void tmr12SkipAlreadyGoneSibling() {
        Fixture f = oneShotTimeout(cn.iocoder.yudao.module.bpm.enums.definition.BpmUserTaskTimeoutHandlerTypeEnum.APPROVE.getType(), model -> {
            MultiInstanceLoopCharacteristics loop = new MultiInstanceLoopCharacteristics();
            loop.setSequential(false); loop.setLoopCardinality("2");
            ((UserTask) model.getFlowElement("review")).setLoopCharacteristics(loop);
        });
        List<Task> originals = engine.getTaskService().createTaskQuery().processInstanceId(f.id).taskDefinitionKey("review").list();
        assertEquals(2, originals.size());
        jdbc.update("DELETE FROM ACT_RU_TASK WHERE ID_=?", originals.get(1).getId());
        fireTimeout(f);
        assertEquals(0, marker(f.id));
        assertEquals(0, engine.getTaskService().createTaskQuery().taskId(originals.get(0).getId()).count());
    }

    @Test void ret04ThreeRealGenerationConflictsExhaust() throws Exception {
        Fixture f = fixture(1, model -> cn.iocoder.yudao.module.bpm.framework.flowable.core.util.BpmnModelUtils.addExtensionElement(
                model.getFlowElement("review"), BpmnModelConstants.USER_TASK_APPROVE_TYPE,
                cn.iocoder.yudao.module.bpm.enums.definition.BpmUserTaskApproveTypeEnum.AUTO_APPROVE.getType().toString()));
        transaction(4, () -> policy.withInstanceLock(f.id, () -> null));
        DataSource stale = new DataSource() {
            @Override public java.sql.Connection getConnection() throws SQLException {
                java.sql.Connection c = ds.getConnection();
                return new StaleGenerationConnection(c);
            }
            @Override public java.sql.Connection getConnection(String u, String p) throws SQLException { return getConnection(); }
            @Override public java.io.PrintWriter getLogWriter() { return null; }
            @Override public void setLogWriter(java.io.PrintWriter out) { }
            @Override public void setLoginTimeout(int seconds) { }
            @Override public int getLoginTimeout() { return 0; }
            @Override public java.util.logging.Logger getParentLogger() { return java.util.logging.Logger.getGlobal(); }
            @Override public <T> T unwrap(Class<T> iface) { return iface.cast(this); }
            @Override public boolean isWrapperFor(Class<?> iface) { return false; }
        };
        set(rawPolicy, "dataSource", stale);
        try (MockedStatic<SpringUtil> beans = mockStatic(SpringUtil.class)) {
            beans.when(() -> SpringUtil.getBean(BpmInitiatorWithdrawPolicyService.class)).thenReturn(policy);
            ServiceException ex = assertThrows(ServiceException.class,
                    () -> policy.runAfterCompletion(f.id, "1", f.task, () -> { }));
            assertEquals(INITIATOR_WITHDRAW_CONCURRENT_CHANGE.getCode(), ex.getCode());
        } finally {
            set(rawPolicy, "dataSource", ds);
        }
        assertEquals("review", task(f.id).getTaskDefinitionKey());
        assertEquals(0, marker(f.id));
    }

    private final class StaleGenerationConnection implements java.sql.Connection {
        private final java.sql.Connection inner;
        private StaleGenerationConnection(java.sql.Connection inner) { this.inner = inner; }
        @Override public java.sql.PreparedStatement prepareStatement(String sql) throws SQLException {
            java.sql.PreparedStatement ps = inner.prepareStatement(sql);
            if (sql != null && sql.contains("SELECT generation FROM bpm_initiator_withdraw_state")) {
                return new StalePrepared(ps);
            }
            return ps;
        }
        @Override public java.sql.Statement createStatement() throws SQLException { return inner.createStatement(); }
        @Override public void commit() throws SQLException { inner.commit(); }
        @Override public void rollback() throws SQLException { inner.rollback(); }
        @Override public void close() throws SQLException { inner.close(); }
        @Override public boolean isClosed() throws SQLException { return inner.isClosed(); }
        @Override public void setAutoCommit(boolean autoCommit) throws SQLException { inner.setAutoCommit(autoCommit); }
        @Override public boolean getAutoCommit() throws SQLException { return inner.getAutoCommit(); }
        @Override public java.sql.DatabaseMetaData getMetaData() throws SQLException { return inner.getMetaData(); }
        @Override public void setReadOnly(boolean readOnly) throws SQLException { inner.setReadOnly(readOnly); }
        @Override public boolean isReadOnly() throws SQLException { return inner.isReadOnly(); }
        @Override public void setCatalog(String catalog) throws SQLException { inner.setCatalog(catalog); }
        @Override public String getCatalog() throws SQLException { return inner.getCatalog(); }
        @Override public void setTransactionIsolation(int level) throws SQLException { inner.setTransactionIsolation(level); }
        @Override public int getTransactionIsolation() throws SQLException { return inner.getTransactionIsolation(); }
        @Override public java.sql.SQLWarning getWarnings() throws SQLException { return inner.getWarnings(); }
        @Override public void clearWarnings() throws SQLException { inner.clearWarnings(); }
        @Override public java.sql.PreparedStatement prepareStatement(String sql, int a, int b) throws SQLException { return inner.prepareStatement(sql, a, b); }
        @Override public java.sql.CallableStatement prepareCall(String sql) throws SQLException { return inner.prepareCall(sql); }
        @Override public String nativeSQL(String sql) throws SQLException { return inner.nativeSQL(sql); }
        @Override public java.sql.Statement createStatement(int a, int b) throws SQLException { return inner.createStatement(a, b); }
        @Override public java.sql.CallableStatement prepareCall(String sql, int a, int b) throws SQLException { return inner.prepareCall(sql, a, b); }
        @Override public java.util.Map<String, Class<?>> getTypeMap() throws SQLException { return inner.getTypeMap(); }
        @Override public void setTypeMap(java.util.Map<String, Class<?>> map) throws SQLException { inner.setTypeMap(map); }
        @Override public void setHoldability(int holdability) throws SQLException { inner.setHoldability(holdability); }
        @Override public int getHoldability() throws SQLException { return inner.getHoldability(); }
        @Override public java.sql.Savepoint setSavepoint() throws SQLException { return inner.setSavepoint(); }
        @Override public java.sql.Savepoint setSavepoint(String name) throws SQLException { return inner.setSavepoint(name); }
        @Override public void rollback(java.sql.Savepoint savepoint) throws SQLException { inner.rollback(savepoint); }
        @Override public void releaseSavepoint(java.sql.Savepoint savepoint) throws SQLException { inner.releaseSavepoint(savepoint); }
        @Override public java.sql.Statement createStatement(int a, int b, int c) throws SQLException { return inner.createStatement(a, b, c); }
        @Override public java.sql.PreparedStatement prepareStatement(String sql, int a, int b, int c) throws SQLException { return inner.prepareStatement(sql, a, b, c); }
        @Override public java.sql.CallableStatement prepareCall(String sql, int a, int b, int c) throws SQLException { return inner.prepareCall(sql, a, b, c); }
        @Override public java.sql.PreparedStatement prepareStatement(String sql, int a) throws SQLException { return inner.prepareStatement(sql, a); }
        @Override public java.sql.PreparedStatement prepareStatement(String sql, int[] a) throws SQLException { return inner.prepareStatement(sql, a); }
        @Override public java.sql.PreparedStatement prepareStatement(String sql, String[] a) throws SQLException { return inner.prepareStatement(sql, a); }
        @Override public java.sql.Clob createClob() throws SQLException { return inner.createClob(); }
        @Override public java.sql.Blob createBlob() throws SQLException { return inner.createBlob(); }
        @Override public java.sql.NClob createNClob() throws SQLException { return inner.createNClob(); }
        @Override public java.sql.SQLXML createSQLXML() throws SQLException { return inner.createSQLXML(); }
        @Override public boolean isValid(int timeout) throws SQLException { return inner.isValid(timeout); }
        @Override public void setClientInfo(String name, String value) throws java.sql.SQLClientInfoException { inner.setClientInfo(name, value); }
        @Override public void setClientInfo(java.util.Properties properties) throws java.sql.SQLClientInfoException { inner.setClientInfo(properties); }
        @Override public String getClientInfo(String name) throws SQLException { return inner.getClientInfo(name); }
        @Override public java.util.Properties getClientInfo() throws SQLException { return inner.getClientInfo(); }
        @Override public java.sql.Array createArrayOf(String typeName, Object[] elements) throws SQLException { return inner.createArrayOf(typeName, elements); }
        @Override public java.sql.Struct createStruct(String typeName, Object[] attributes) throws SQLException { return inner.createStruct(typeName, attributes); }
        @Override public void setSchema(String schema) throws SQLException { inner.setSchema(schema); }
        @Override public String getSchema() throws SQLException { return inner.getSchema(); }
        @Override public void abort(java.util.concurrent.Executor executor) throws SQLException { inner.abort(executor); }
        @Override public void setNetworkTimeout(java.util.concurrent.Executor executor, int milliseconds) throws SQLException { inner.setNetworkTimeout(executor, milliseconds); }
        @Override public int getNetworkTimeout() throws SQLException { return inner.getNetworkTimeout(); }
        @Override public <T> T unwrap(Class<T> iface) throws SQLException { return inner.unwrap(iface); }
        @Override public boolean isWrapperFor(Class<?> iface) throws SQLException { return inner.isWrapperFor(iface); }
    }
    private final class StalePrepared implements java.sql.PreparedStatement {
        private final java.sql.PreparedStatement inner;
        private Object tenant; private Object instance;
        private StalePrepared(java.sql.PreparedStatement inner) { this.inner = inner; }
        @Override public java.sql.ResultSet executeQuery() throws SQLException {
            java.sql.ResultSet rs = inner.executeQuery();
            try (java.sql.Connection bump = ds.getConnection();
                 java.sql.PreparedStatement up = bump.prepareStatement(
                         "UPDATE bpm_initiator_withdraw_state SET generation=generation+1 WHERE tenant_id=? AND process_instance_id=?")) {
                bump.setAutoCommit(true);
                up.setObject(1, tenant); up.setObject(2, instance); up.executeUpdate();
            }
            return rs;
        }
        @Override public void setObject(int parameterIndex, Object x) throws SQLException {
            if (parameterIndex == 1) tenant = x; if (parameterIndex == 2) instance = x; inner.setObject(parameterIndex, x);
        }
        @Override public void setLong(int parameterIndex, long x) throws SQLException { setObject(parameterIndex, x); inner.setLong(parameterIndex, x); }
        @Override public void setString(int parameterIndex, String x) throws SQLException { setObject(parameterIndex, x); inner.setString(parameterIndex, x); }
        @Override public int executeUpdate() throws SQLException { return inner.executeUpdate(); }
        @Override public void close() throws SQLException { inner.close(); }
        @Override public int getMaxFieldSize() throws SQLException { return inner.getMaxFieldSize(); }
        @Override public void setMaxFieldSize(int max) throws SQLException { inner.setMaxFieldSize(max); }
        @Override public int getMaxRows() throws SQLException { return inner.getMaxRows(); }
        @Override public void setMaxRows(int max) throws SQLException { inner.setMaxRows(max); }
        @Override public void setEscapeProcessing(boolean enable) throws SQLException { inner.setEscapeProcessing(enable); }
        @Override public int getQueryTimeout() throws SQLException { return inner.getQueryTimeout(); }
        @Override public void setQueryTimeout(int seconds) throws SQLException { inner.setQueryTimeout(seconds); }
        @Override public void cancel() throws SQLException { inner.cancel(); }
        @Override public java.sql.SQLWarning getWarnings() throws SQLException { return inner.getWarnings(); }
        @Override public void clearWarnings() throws SQLException { inner.clearWarnings(); }
        @Override public void setCursorName(String name) throws SQLException { inner.setCursorName(name); }
        @Override public boolean execute() throws SQLException { return inner.execute(); }
        @Override public java.sql.ResultSet getResultSet() throws SQLException { return inner.getResultSet(); }
        @Override public int getUpdateCount() throws SQLException { return inner.getUpdateCount(); }
        @Override public boolean getMoreResults() throws SQLException { return inner.getMoreResults(); }
        @Override public void setFetchDirection(int direction) throws SQLException { inner.setFetchDirection(direction); }
        @Override public int getFetchDirection() throws SQLException { return inner.getFetchDirection(); }
        @Override public void setFetchSize(int rows) throws SQLException { inner.setFetchSize(rows); }
        @Override public int getFetchSize() throws SQLException { return inner.getFetchSize(); }
        @Override public int getResultSetConcurrency() throws SQLException { return inner.getResultSetConcurrency(); }
        @Override public int getResultSetType() throws SQLException { return inner.getResultSetType(); }
        @Override public void addBatch() throws SQLException { inner.addBatch(); }
        @Override public void clearBatch() throws SQLException { inner.clearBatch(); }
        @Override public int[] executeBatch() throws SQLException { return inner.executeBatch(); }
        @Override public java.sql.Connection getConnection() throws SQLException { return inner.getConnection(); }
        @Override public boolean getMoreResults(int current) throws SQLException { return inner.getMoreResults(current); }
        @Override public java.sql.ResultSet getGeneratedKeys() throws SQLException { return inner.getGeneratedKeys(); }
        @Override public int executeUpdate(String sql, int a) throws SQLException { return inner.executeUpdate(sql, a); }
        @Override public int executeUpdate(String sql, int[] a) throws SQLException { return inner.executeUpdate(sql, a); }
        @Override public int executeUpdate(String sql, String[] a) throws SQLException { return inner.executeUpdate(sql, a); }
        @Override public boolean execute(String sql, int a) throws SQLException { return inner.execute(sql, a); }
        @Override public boolean execute(String sql, int[] a) throws SQLException { return inner.execute(sql, a); }
        @Override public boolean execute(String sql, String[] a) throws SQLException { return inner.execute(sql, a); }
        @Override public int getResultSetHoldability() throws SQLException { return inner.getResultSetHoldability(); }
        @Override public boolean isClosed() throws SQLException { return inner.isClosed(); }
        @Override public void setPoolable(boolean poolable) throws SQLException { inner.setPoolable(poolable); }
        @Override public boolean isPoolable() throws SQLException { return inner.isPoolable(); }
        @Override public void closeOnCompletion() throws SQLException { inner.closeOnCompletion(); }
        @Override public boolean isCloseOnCompletion() throws SQLException { return inner.isCloseOnCompletion(); }
        @Override public void setNull(int parameterIndex, int sqlType) throws SQLException { inner.setNull(parameterIndex, sqlType); }
        @Override public void setBoolean(int parameterIndex, boolean x) throws SQLException { inner.setBoolean(parameterIndex, x); }
        @Override public void setByte(int parameterIndex, byte x) throws SQLException { inner.setByte(parameterIndex, x); }
        @Override public void setShort(int parameterIndex, short x) throws SQLException { inner.setShort(parameterIndex, x); }
        @Override public void setInt(int parameterIndex, int x) throws SQLException { inner.setInt(parameterIndex, x); }
        @Override public void setFloat(int parameterIndex, float x) throws SQLException { inner.setFloat(parameterIndex, x); }
        @Override public void setDouble(int parameterIndex, double x) throws SQLException { inner.setDouble(parameterIndex, x); }
        @Override public void setBigDecimal(int parameterIndex, java.math.BigDecimal x) throws SQLException { inner.setBigDecimal(parameterIndex, x); }
        @Override public void setBytes(int parameterIndex, byte[] x) throws SQLException { inner.setBytes(parameterIndex, x); }
        @Override public void setDate(int parameterIndex, java.sql.Date x) throws SQLException { inner.setDate(parameterIndex, x); }
        @Override public void setTime(int parameterIndex, java.sql.Time x) throws SQLException { inner.setTime(parameterIndex, x); }
        @Override public void setTimestamp(int parameterIndex, java.sql.Timestamp x) throws SQLException { inner.setTimestamp(parameterIndex, x); }
        @Override public void setAsciiStream(int parameterIndex, java.io.InputStream x, int length) throws SQLException { inner.setAsciiStream(parameterIndex, x, length); }
        @Deprecated @Override public void setUnicodeStream(int parameterIndex, java.io.InputStream x, int length) throws SQLException { inner.setUnicodeStream(parameterIndex, x, length); }
        @Override public void setBinaryStream(int parameterIndex, java.io.InputStream x, int length) throws SQLException { inner.setBinaryStream(parameterIndex, x, length); }
        @Override public void clearParameters() throws SQLException { inner.clearParameters(); }
        @Override public void setObject(int parameterIndex, Object x, int targetSqlType) throws SQLException { inner.setObject(parameterIndex, x, targetSqlType); }
        @Override public boolean execute(String sql) throws SQLException { return inner.execute(sql); }
        @Override public void addBatch(String sql) throws SQLException { inner.addBatch(sql); }
        @Override public void setCharacterStream(int parameterIndex, java.io.Reader reader, int length) throws SQLException { inner.setCharacterStream(parameterIndex, reader, length); }
        @Override public void setRef(int parameterIndex, java.sql.Ref x) throws SQLException { inner.setRef(parameterIndex, x); }
        @Override public void setBlob(int parameterIndex, java.sql.Blob x) throws SQLException { inner.setBlob(parameterIndex, x); }
        @Override public void setClob(int parameterIndex, java.sql.Clob x) throws SQLException { inner.setClob(parameterIndex, x); }
        @Override public void setArray(int parameterIndex, java.sql.Array x) throws SQLException { inner.setArray(parameterIndex, x); }
        @Override public java.sql.ResultSetMetaData getMetaData() throws SQLException { return inner.getMetaData(); }
        @Override public void setDate(int parameterIndex, java.sql.Date x, Calendar cal) throws SQLException { inner.setDate(parameterIndex, x, cal); }
        @Override public void setTime(int parameterIndex, java.sql.Time x, Calendar cal) throws SQLException { inner.setTime(parameterIndex, x, cal); }
        @Override public void setTimestamp(int parameterIndex, java.sql.Timestamp x, Calendar cal) throws SQLException { inner.setTimestamp(parameterIndex, x, cal); }
        @Override public void setNull(int parameterIndex, int sqlType, String typeName) throws SQLException { inner.setNull(parameterIndex, sqlType, typeName); }
        @Override public void setURL(int parameterIndex, java.net.URL x) throws SQLException { inner.setURL(parameterIndex, x); }
        @Override public java.sql.ParameterMetaData getParameterMetaData() throws SQLException { return inner.getParameterMetaData(); }
        @Override public void setRowId(int parameterIndex, java.sql.RowId x) throws SQLException { inner.setRowId(parameterIndex, x); }
        @Override public void setNString(int parameterIndex, String value) throws SQLException { inner.setNString(parameterIndex, value); }
        @Override public void setNCharacterStream(int parameterIndex, java.io.Reader value, long length) throws SQLException { inner.setNCharacterStream(parameterIndex, value, length); }
        @Override public void setNClob(int parameterIndex, java.sql.NClob value) throws SQLException { inner.setNClob(parameterIndex, value); }
        @Override public void setClob(int parameterIndex, java.io.Reader reader, long length) throws SQLException { inner.setClob(parameterIndex, reader, length); }
        @Override public void setBlob(int parameterIndex, java.io.InputStream inputStream, long length) throws SQLException { inner.setBlob(parameterIndex, inputStream, length); }
        @Override public void setNClob(int parameterIndex, java.io.Reader reader, long length) throws SQLException { inner.setNClob(parameterIndex, reader, length); }
        @Override public void setSQLXML(int parameterIndex, java.sql.SQLXML xmlObject) throws SQLException { inner.setSQLXML(parameterIndex, xmlObject); }
        @Override public void setObject(int parameterIndex, Object x, int targetSqlType, int scaleOrLength) throws SQLException { inner.setObject(parameterIndex, x, targetSqlType, scaleOrLength); }
        @Override public void setAsciiStream(int parameterIndex, java.io.InputStream x, long length) throws SQLException { inner.setAsciiStream(parameterIndex, x, length); }
        @Override public void setBinaryStream(int parameterIndex, java.io.InputStream x, long length) throws SQLException { inner.setBinaryStream(parameterIndex, x, length); }
        @Override public void setCharacterStream(int parameterIndex, java.io.Reader reader, long length) throws SQLException { inner.setCharacterStream(parameterIndex, reader, length); }
        @Override public void setAsciiStream(int parameterIndex, java.io.InputStream x) throws SQLException { inner.setAsciiStream(parameterIndex, x); }
        @Override public void setBinaryStream(int parameterIndex, java.io.InputStream x) throws SQLException { inner.setBinaryStream(parameterIndex, x); }
        @Override public void setCharacterStream(int parameterIndex, java.io.Reader reader) throws SQLException { inner.setCharacterStream(parameterIndex, reader); }
        @Override public void setNCharacterStream(int parameterIndex, java.io.Reader value) throws SQLException { inner.setNCharacterStream(parameterIndex, value); }
        @Override public void setClob(int parameterIndex, java.io.Reader reader) throws SQLException { inner.setClob(parameterIndex, reader); }
        @Override public void setBlob(int parameterIndex, java.io.InputStream inputStream) throws SQLException { inner.setBlob(parameterIndex, inputStream); }
        @Override public void setNClob(int parameterIndex, java.io.Reader reader) throws SQLException { inner.setNClob(parameterIndex, reader); }
        @Override public int executeUpdate(String sql) throws SQLException { return inner.executeUpdate(sql); }
        @Override public java.sql.ResultSet executeQuery(String sql) throws SQLException { return inner.executeQuery(sql); }
        @Override public <T> T unwrap(Class<T> iface) throws SQLException { return inner.unwrap(iface); }
        @Override public boolean isWrapperFor(Class<?> iface) throws SQLException { return inner.isWrapperFor(iface); }
    }

    @Test void locSqlInjectedConnectionFailure() {
        set(rawPolicy, "dataSource", new DataSource() {
            @Override public java.sql.Connection getConnection() throws SQLException { throw new SQLException("injected locator"); }
            @Override public java.sql.Connection getConnection(String username, String password) throws SQLException { return getConnection(); }
            @Override public java.io.PrintWriter getLogWriter() { return null; }
            @Override public void setLogWriter(java.io.PrintWriter out) { }
            @Override public void setLoginTimeout(int seconds) { }
            @Override public int getLoginTimeout() { return 0; }
            @Override public java.util.logging.Logger getParentLogger() { return java.util.logging.Logger.getGlobal(); }
            @Override public <T> T unwrap(Class<T> iface) { return iface.cast(this); }
            @Override public boolean isWrapperFor(Class<?> iface) { return false; }
        });
        Fixture f = fixture(1);
        try {
            IllegalStateException ex = assertThrows(IllegalStateException.class, () -> policy.findActiveInstanceId("k", "bk"));
            assertEquals("Cannot locate current process instance", ex.getMessage());
            assertInstanceOf(SQLException.class, ex.getCause());
            IllegalStateException submitFail = assertThrows(IllegalStateException.class, () -> submit(f));
            assertEquals("Cannot locate current process instance", submitFail.getMessage());
            assertEquals("review", task(f.id).getTaskDefinitionKey());
            assertNull(engine.getRuntimeService().getVariable(f.id, "resubmitted"));
        } finally {
            set(rawPolicy, "dataSource", ds);
        }
        assertEquals(f.id, policy.findActiveInstanceId(
                engine.getRepositoryService().getProcessDefinition(f.definition).getKey(), f.businessKey));
    }

    @ParameterizedTest @ValueSource(strings = {"finance_payment_apply", "finance_salary_payment_apply", "finance_tax_payment_apply"})
    void reg02SyntheticPaymentCancelBlocked(String key) {
        BpmnModel model = new BpmnModel();
        org.flowable.bpmn.model.Process process = new org.flowable.bpmn.model.Process();
        process.setId(key); process.setExecutable(true); model.addProcess(process);
        StartEvent start = new StartEvent(); start.setId("start"); process.addFlowElement(start);
        UserTask startTask = new UserTask(); startTask.setId(START_USER_NODE_ID); startTask.setAssignee("1"); process.addFlowElement(startTask);
        UserTask review = new UserTask(); review.setId("review"); review.setAssignee("1"); process.addFlowElement(review);
        EndEvent end = new EndEvent(); end.setId("end"); process.addFlowElement(end);
        process.addFlowElement(new SequenceFlow("start", START_USER_NODE_ID));
        process.addFlowElement(new SequenceFlow(START_USER_NODE_ID, "review"));
        process.addFlowElement(new SequenceFlow("review", "end"));
        String def = engine.getRepositoryService().createDeployment().tenantId("1").addBpmnModel(key + ".bpmn", model).deploy().getId();
        String definitionId = engine.getRepositoryService().createProcessDefinitionQuery().deploymentId(def).singleResult().getId();
        definitions.put(definitionId, new BpmProcessDefinitionInfoDO().setInitiatorWithdrawMode(1).setAllowCancelRunningProcess(true));
        engine.getIdentityService().setAuthenticatedUserId("1");
        String id;
        try {
            id = transaction(4, () -> engine.getRuntimeService().startProcessInstanceById(definitionId, "pay-" + UUID.randomUUID(),
                    new HashMap<>(Map.of(BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_STATUS, BpmProcessInstanceStatusEnum.RUNNING.getStatus()))).getId());
        } finally { engine.getIdentityService().setAuthenticatedUserId(null); }
        transaction(4, () -> { engine.getTaskService().complete(task(id).getId()); return null; });
        assertEquals(PROCESS_INSTANCE_CANCEL_FAIL_USE_PAYMENT_DOMAIN.getCode(),
                assertThrows(ServiceException.class, () -> submitService.cancelProcessInstanceByStartUser(1L,
                        new BpmProcessInstanceCancelReqVO().setId(id).setReason("cancel"))).getCode());
        assertNotNull(engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(id).singleResult());
    }
}
