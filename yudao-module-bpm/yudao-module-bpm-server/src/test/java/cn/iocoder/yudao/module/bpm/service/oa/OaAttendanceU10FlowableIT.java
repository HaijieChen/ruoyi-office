package cn.iocoder.yudao.module.bpm.service.oa;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.instance.BpmProcessInstanceCopyPageReqVO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.task.BpmProcessInstanceCopyDO;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.BpmTaskCandidateInvoker;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.listener.BpmCopyTaskDelegate;
import cn.iocoder.yudao.module.bpm.service.task.BpmProcessInstanceCopyService;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * U10 AE7 补验：部署<strong>原始</strong>加班/补卡 BPMN（保留 MI 与 {@code bpmCopyTaskDelegate}）。
 * {@code coll_userList} 为合成用户，不是真实 hr_admin/gm 组织解析。
 * 静态 candidateStrategy 断言与执行层或签/抄送分层，互不冒充。
 */
class OaAttendanceU10FlowableIT {

    private static final List<String> SYNTHETIC_OR_SIGN = List.of("syn-hr-1", "syn-hr-2");

    private static ProcessEngine engine;
    private static RecordingCopyService copies;

    @BeforeAll
    static void startEngine() {
        copies = new RecordingCopyService();
        BpmTaskCandidateInvoker invoker = mock(BpmTaskCandidateInvoker.class);
        when(invoker.calculateUsersByTask(any())).thenReturn(Set.of(221L));
        BpmCopyTaskDelegate copy = new BpmCopyTaskDelegate();
        ReflectionTestUtils.setField(copy, "taskCandidateInvoker", invoker);
        ReflectionTestUtils.setField(copy, "processInstanceCopyService", copies);

        ProcessEngineConfigurationImpl cfg = (ProcessEngineConfigurationImpl)
                ProcessEngineConfiguration.createStandaloneInMemProcessEngineConfiguration();
        cfg.setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
        Map<Object, Object> beans = new LinkedHashMap<>();
        beans.put("bpmCopyTaskDelegate", copy);
        cfg.setBeans(beans);
        engine = cfg.buildProcessEngine();
    }

    @AfterAll
    static void stopEngine() {
        if (engine != null) {
            engine.close();
        }
    }

    @Test
    void productionXmlKeepsStrategiesAndOrSignMi() throws Exception {
        String overtime = Files.readString(bpmn("oa_overtime.bpmn20.xml"), StandardCharsets.UTF_8);
        assertTrue(overtime.contains("candidateStrategy=\"38\""));
        assertTrue(overtime.contains("candidateParam=\"hr_admin\""));
        assertTrue(overtime.contains("candidateParam=\"gm\""));
        assertTrue(overtime.contains("candidateParam>705") || overtime.contains("candidateParam=\"705\""));
        assertTrue(overtime.contains("${bpmCopyTaskDelegate}"));
        assertTrue(overtime.contains("flowable:collection=\"${coll_userList}\""));
        assertTrue(overtime.contains("nrOfCompletedInstances > 0"));
        assertEquals(2, overtime.split("<multiInstanceLoopCharacteristics", -1).length - 1);
        String punch = Files.readString(bpmn("oa_punch_correction.bpmn20.xml"), StandardCharsets.UTF_8);
        assertTrue(punch.contains("nrOfCompletedInstances > 0"));
        assertFalse(punch.contains("taskCopyWangPeng"));
    }

    @Test
    void overtimeWeekendHrOrSignThenSkipGmAndCopy() throws Exception {
        copies.calls.clear();
        ProcessInstance pi = start("oa_overtime", "oa_overtime.bpmn20.xml", Map.of("holiday", false));
        completeOne("taskDeptLeaderMulti", pi);
        orSignOneOf("taskHr", pi);
        assertEquals(0, tasks().createTaskQuery().processInstanceId(pi.getId()).count());
        assertTrue(engine.getRuntimeService().createProcessInstanceQuery()
                .processInstanceId(pi.getId()).count() == 0);
        List<String> ids = activityIds(pi.getId());
        assertTrue(ids.contains("taskHr"));
        assertFalse(ids.contains("taskGm"));
        assertFalse(ids.contains("taskCopyWangPeng"));
        assertEquals(0, copies.calls.size());
    }

    @Test
    void overtimeHolidayHrAndGmOrSignThenCopy221Once() throws Exception {
        copies.calls.clear();
        ProcessInstance pi = start("oa_overtime", "oa_overtime.bpmn20.xml", Map.of("holiday", true));
        completeOne("taskDeptLeaderMulti", pi);
        orSignOneOf("taskHr", pi);
        orSignOneOf("taskGm", pi);
        assertTrue(engine.getRuntimeService().createProcessInstanceQuery()
                .processInstanceId(pi.getId()).count() == 0);
        List<String> ids = activityIds(pi.getId());
        assertTrue(ids.contains("taskGm"));
        assertTrue(ids.contains("taskCopyWangPeng"));
        assertEquals(1, copies.calls.size());
        RecordingCopyService.Call call = copies.calls.get(0);
        assertEquals(Set.of(221L), Set.copyOf(call.userIds));
        assertEquals("taskCopyWangPeng", call.activityId);
        assertEquals(pi.getId(), call.processInstanceId);
    }

    @Test
    void punchHrOrSignNeverCopies() throws Exception {
        copies.calls.clear();
        ProcessInstance pi = start("oa_punch_correction", "oa_punch_correction.bpmn20.xml", Map.of());
        completeOne("taskDeptLeaderMulti", pi);
        orSignOneOf("taskHr", pi);
        assertTrue(engine.getRuntimeService().createProcessInstanceQuery()
                .processInstanceId(pi.getId()).count() == 0);
        assertFalse(activityIds(pi.getId()).contains("taskCopyWangPeng"));
        assertEquals(0, copies.calls.size());
    }

    private static void orSignOneOf(String taskKey, ProcessInstance pi) {
        List<Task> open = tasks().createTaskQuery()
                .processInstanceId(pi.getId())
                .taskDefinitionKey(taskKey)
                .list();
        assertTrue(open.size() >= 2,
                () -> taskKey + " or-sign needs >=2 synthetic tasks, got " + open.size()
                        + " (not production hr_admin/gm org resolution)");
        tasks().complete(open.get(0).getId());
        assertEquals(0, tasks().createTaskQuery()
                .processInstanceId(pi.getId())
                .taskDefinitionKey(taskKey)
                .count(), taskKey + " remaining instances must be cancelled after one complete");
    }

    private static void completeOne(String taskKey, ProcessInstance pi) {
        List<Task> open = tasks().createTaskQuery()
                .processInstanceId(pi.getId())
                .taskDefinitionKey(taskKey)
                .list();
        assertFalse(open.isEmpty(), "missing " + taskKey);
        tasks().complete(open.get(0).getId());
    }

    private static ProcessInstance start(String key, String file, Map<String, Object> extra) throws Exception {
        String xml = Files.readString(bpmn(file), StandardCharsets.UTF_8);
        assertTrue(xml.contains("multiInstanceLoopCharacteristics") || key.equals("oa_overtime")
                        || xml.contains("nrOfCompletedInstances"),
                "must deploy original MI XML");
        engine.getRepositoryService().createDeployment().addString(file, xml).deploy();
        Map<String, Object> vars = new LinkedHashMap<>(extra);
        vars.put("coll_userList", SYNTHETIC_OR_SIGN);
        return engine.getRuntimeService().startProcessInstanceByKey(key, vars);
    }

    private static TaskService tasks() {
        return engine.getTaskService();
    }

    private static List<String> activityIds(String processInstanceId) {
        List<HistoricActivityInstance> hist = engine.getHistoryService()
                .createHistoricActivityInstanceQuery()
                .processInstanceId(processInstanceId)
                .orderByHistoricActivityInstanceStartTime()
                .asc()
                .list();
        List<String> ids = new ArrayList<>();
        for (HistoricActivityInstance a : hist) {
            if (a.getActivityId() != null) {
                ids.add(a.getActivityId());
            }
        }
        return ids;
    }

    private static Path bpmn(String name) {
        Path current = Path.of("").toAbsolutePath();
        while (current != null && !Files.isDirectory(current.resolve("sql/mysql/bpmn"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new AssertionError("repo root not found");
        }
        return current.resolve("sql/mysql/bpmn").resolve(name);
    }

    /** Fixture copy sink — not a production copy store. */
    static final class RecordingCopyService implements BpmProcessInstanceCopyService {
        final List<Call> calls = new CopyOnWriteArrayList<>();

        @Override
        public void createProcessInstanceCopy(Collection<Long> userIds, String reason, String taskId) {
            calls.add(new Call(userIds, null, null, taskId));
        }

        @Override
        public void createProcessInstanceCopy(Collection<Long> userIds, String reason, String processInstanceId,
                                              String activityId, String activityName, String taskId) {
            calls.add(new Call(userIds, processInstanceId, activityId, taskId));
        }

        @Override
        public PageResult<BpmProcessInstanceCopyDO> getProcessInstanceCopyPage(
                Long userId, BpmProcessInstanceCopyPageReqVO pageReqVO) {
            return new PageResult<>(List.of(), 0L);
        }

        @Override
        public void deleteProcessInstanceCopy(String processInstanceId) {
        }

        record Call(Collection<Long> userIds, String processInstanceId, String activityId, String taskId) {
        }
    }
}
