package cn.iocoder.yudao.module.bpm.api.task;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.framework.security.OaBillAccessPermission;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Product OaBillAccessPermission + BpmProcessParticipantApiImpl against real Flowable. */
class BpmProcessParticipantApiFlowableTest {

    private ProcessEngine engine;
    private BpmProcessParticipantApiImpl api;
    private String runningId;
    private String endedId;

    @BeforeEach
    void setup() {
        TenantContextHolder.setTenantId(1L);
        engine = ProcessEngineConfiguration.createStandaloneInMemProcessEngineConfiguration()
                .setJdbcUrl("jdbc:h2:mem:part" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1")
                .setDatabaseSchemaUpdate("true").setAsyncExecutorActivate(false).buildProcessEngine();
        engine.getRepositoryService().createDeployment().addString("p.bpmn20.xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn" targetNamespace="test">
                <process id="p" isExecutable="true"><startEvent id="start"/><sequenceFlow id="a" sourceRef="start" targetRef="first"/>
                <userTask id="first"/><sequenceFlow id="b" sourceRef="first" targetRef="second"/>
                <userTask id="second"/><sequenceFlow id="c" sourceRef="second" targetRef="end"/><endEvent id="end"/></process></definitions>
                """).deploy();
        OaBillAccessPermission access = new OaBillAccessPermission();
        ReflectionTestUtils.setField(access, "taskServiceProvider", provider(engine.getTaskService()));
        ReflectionTestUtils.setField(access, "historyServiceProvider", provider(engine.getHistoryService()));
        ObjectProvider<BpmFinanceAttachAccess> attach = mock(ObjectProvider.class);
        when(attach.getIfAvailable()).thenReturn(null);
        ReflectionTestUtils.setField(access, "financeAttachAccessProvider", attach);
        api = new BpmProcessParticipantApiImpl();
        ReflectionTestUtils.setField(api, "oaBillAccessPermission", access);

        ProcessInstance running = engine.getRuntimeService().startProcessInstanceByKey("p");
        runningId = running.getId();
        Task first = engine.getTaskService().createTaskQuery().processInstanceId(runningId).singleResult();
        engine.getTaskService().setOwner(first.getId(), "684");
        engine.getTaskService().addCandidateUser(first.getId(), "682");

        ProcessInstance ended = engine.getRuntimeService().startProcessInstanceByKey("p");
        endedId = ended.getId();
        Task t1 = engine.getTaskService().createTaskQuery().processInstanceId(endedId).singleResult();
        engine.getTaskService().setOwner(t1.getId(), "684");
        engine.getTaskService().setAssignee(t1.getId(), "683");
        engine.getTaskService().complete(t1.getId());
        Task t2 = engine.getTaskService().createTaskQuery().processInstanceId(endedId).singleResult();
        engine.getTaskService().setAssignee(t2.getId(), "681");
        engine.getTaskService().complete(t2.getId());
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        TenantContextHolder.clear();
        if (engine != null) {
            engine.close();
        }
    }

    @Test
    void activeCandidateAndTaskOwnerCanReadRunningProcess() {
        login(682);
        assertTrue(api.canReadProcess(runningId).getCheckedData());
        login(684);
        assertTrue(api.canReadProcess(runningId).getCheckedData());
        login(999);
        assertFalse(api.canReadProcess(runningId).getCheckedData());
    }

    @Test
    void historicAssigneeAndTaskOwnerCanReadEndedProcess() {
        login(683);
        assertTrue(api.canReadProcess(endedId).getCheckedData());
        login(684);
        assertTrue(api.canReadProcess(endedId).getCheckedData());
        login(681);
        assertTrue(api.canReadProcess(endedId).getCheckedData());
        login(999);
        assertFalse(api.canReadProcess(endedId).getCheckedData());
        login(838);
        assertFalse(api.canReadProcess(endedId).getCheckedData());
    }

    private void login(long uid) {
        SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(uid).setTenantId(1L).setUserType(2),
                new org.springframework.mock.web.MockHttpServletRequest());
    }

    private static <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> p = mock(ObjectProvider.class);
        when(p.getIfAvailable()).thenReturn(value);
        return p;
    }
}
