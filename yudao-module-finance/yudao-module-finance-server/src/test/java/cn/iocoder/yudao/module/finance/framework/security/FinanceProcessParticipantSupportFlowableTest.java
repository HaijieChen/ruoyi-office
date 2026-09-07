package cn.iocoder.yudao.module.finance.framework.security;

import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Product FinanceProcessParticipantSupport against real Flowable; task owner is not applicant. */
class FinanceProcessParticipantSupportFlowableTest {

    private ProcessEngine engine;
    private FinanceProcessParticipantSupport support;
    private String runningId;
    private String endedId;

    @BeforeEach
    void setup() {
        engine = ProcessEngineConfiguration.createStandaloneInMemProcessEngineConfiguration()
                .setJdbcUrl("jdbc:h2:mem:finpart" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1")
                .setDatabaseSchemaUpdate("true").setAsyncExecutorActivate(false).buildProcessEngine();
        engine.getRepositoryService().createDeployment().addString("p.bpmn20.xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn" targetNamespace="test">
                <process id="p" isExecutable="true"><startEvent id="start"/><sequenceFlow id="a" sourceRef="start" targetRef="first"/>
                <userTask id="first"/><sequenceFlow id="b" sourceRef="first" targetRef="second"/>
                <userTask id="second"/><sequenceFlow id="c" sourceRef="second" targetRef="end"/><endEvent id="end"/></process></definitions>
                """).deploy();
        support = new FinanceProcessParticipantSupport();
        ReflectionTestUtils.setField(support, "taskServiceProvider", provider(engine.getTaskService()));
        ReflectionTestUtils.setField(support, "historyServiceProvider", provider(engine.getHistoryService()));

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
        if (engine != null) {
            engine.close();
        }
    }

    @Test
    void activeCandidateAndTaskOwnerAreNotApplicant() {
        assertTrue(support.canReadBill(682L, 838L, runningId));
        assertTrue(support.canReadBill(684L, 838L, runningId));
        assertFalse(support.canReadBill(999L, 838L, runningId));
    }

    @Test
    void endedHistoricAssigneeAndTaskOwnerAreNotApplicant() {
        assertTrue(support.canReadBill(683L, 838L, endedId));
        assertTrue(support.canReadBill(684L, 838L, endedId));
        assertTrue(support.canReadBill(681L, 838L, endedId));
        assertTrue(support.canReadBill(838L, 838L, endedId));
        assertFalse(support.canReadBill(999L, 838L, endedId));
    }

    private static <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> p = mock(ObjectProvider.class);
        when(p.getIfAvailable()).thenReturn(value);
        return p;
    }
}
