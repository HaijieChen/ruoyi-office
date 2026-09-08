package cn.iocoder.yudao.module.bpm.service.task;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.instance.BpmApprovalDetailRespVO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmProcessDefinitionInfoDO;
import cn.iocoder.yudao.module.bpm.service.definition.BpmProcessDefinitionService;
import cn.iocoder.yudao.module.bpm.service.definition.BpmProcessStartEligibilityService;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import org.flowable.engine.*;
import org.flowable.engine.repository.ProcessDefinition;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class BpmHistoryPreservationTest {
 ProcessEngine engine; BpmProcessInstanceServiceImpl service; BpmProcessDefinitionService defs;
 @BeforeEach void setup(){
  engine=ProcessEngineConfiguration.createStandaloneInMemProcessEngineConfiguration().setJdbcUrl("jdbc:h2:mem:history"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1").setDatabaseSchemaUpdate("true").setAsyncExecutorActivate(false).buildProcessEngine();
  service=spy(new BpmProcessInstanceServiceImpl());defs=mock(BpmProcessDefinitionService.class);
  ReflectionTestUtils.setField(service,"runtimeService",engine.getRuntimeService());ReflectionTestUtils.setField(service,"historyService",engine.getHistoryService());ReflectionTestUtils.setField(service,"processDefinitionService",defs);
  ReflectionTestUtils.setField(service,"processStartEligibilityService",mock(BpmProcessStartEligibilityService.class));
  when(defs.getProcessDefinitionInfo(anyString())).thenReturn(new BpmProcessDefinitionInfoDO());when(defs.canUserStartProcessDefinition(any(),any())).thenReturn(true);
  doReturn(new BpmApprovalDetailRespVO()).when(service).getApprovalDetail(anyLong(),any());TenantContextHolder.setTenantId(1L);
 }
 @AfterEach void close(){engine.close();TenantContextHolder.clear();}
 ProcessDefinition deploy(String key,String tenant){
  String xml="<?xml version=\"1.0\" encoding=\"UTF-8\"?><definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" targetNamespace=\"test\"><process id=\""+key+"\" isExecutable=\"true\"><startEvent id=\"start\"/><sequenceFlow id=\"a\" sourceRef=\"start\" targetRef=\"approve\"/><userTask id=\"approve\" name=\"Approve\"/><sequenceFlow id=\"b\" sourceRef=\"approve\" targetRef=\"end\"/><endEvent id=\"end\"/></process></definitions>";
  String dep=engine.getRepositoryService().createDeployment().tenantId(tenant).addString(key+".bpmn20.xml",xml).deploy().getId();return engine.getRepositoryService().createProcessDefinitionQuery().deploymentId(dep).singleResult();
 }
 String start(ProcessDefinition d){return ReflectionTestUtils.invokeMethod(service,"createProcessInstance0",1L,d,new HashMap<String,Object>(),"17",null,true);}
 @Test void crossBusinessAndResubmissionKeepCompletedApproval(){
  var leave=deploy("oa_leave","1");String original=start(leave);var task=engine.getTaskService().createTaskQuery().processInstanceId(original).singleResult();engine.getTaskService().setAssignee(task.getId(),"674");engine.getTaskService().complete(task.getId());
  start(deploy("oa_seal_apply_bill","1"));start(leave);
  assertNotNull(engine.getHistoryService().createHistoricProcessInstanceQuery().processInstanceId(original).singleResult());assertEquals("674",engine.getHistoryService().createHistoricTaskInstanceQuery().taskId(task.getId()).singleResult().getAssignee());assertEquals(3,engine.getHistoryService().createHistoricProcessInstanceQuery().processInstanceBusinessKey("17").count());
 }
 @Test void crossTenantAndFailedStartKeepHistory(){
  var leave=deploy("oa_leave","2");String original=start(leave);engine.getTaskService().complete(engine.getTaskService().createTaskQuery().processInstanceId(original).singleResult().getId());
  var seal=deploy("oa_seal_apply_bill","1");start(seal);when(defs.canUserStartProcessDefinition(any(),any())).thenReturn(false);assertThrows(RuntimeException.class,()->start(seal));assertNotNull(engine.getHistoryService().createHistoricProcessInstanceQuery().processInstanceId(original).singleResult());
 }
}
