package cn.iocoder.yudao.module.bpm.service.task;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmProcessDefinitionInfoDO;
import cn.iocoder.yudao.module.bpm.service.definition.*;
import cn.iocoder.yudao.module.bpm.service.message.BpmMessageService;
import cn.iocoder.yudao.module.bpm.service.notification.BpmNotificationManager;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task.BpmTaskApproveReqVO;
import org.flowable.engine.*;
import org.flowable.engine.delegate.TaskListener;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Exercises production callbacks and approveTask against a real Spring/Flowable database. */
class BpmAutomaticApprovalTransactionTest {
 ProcessEngine engine; DataSourceTransactionManager tm; BpmTaskServiceImpl service;
 boolean evidence; int guardCalls; MockedStatic<SpringUtil> beans;
 @BeforeEach void setup() {
  var ds = new DriverManagerDataSource("jdbc:h2:mem:auto"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1", "sa", "");
  tm=new DataSourceTransactionManager(ds);
  var cfg=new SpringProcessEngineConfiguration();cfg.setDataSource(ds);cfg.setTransactionManager(tm);
  cfg.setDatabaseSchemaUpdate("true");cfg.setAsyncExecutorActivate(false);
  cfg.setBeans(Map.of("financeContractExecCompleteGuardListener", (TaskListener)t->{ guardCalls++; if(!evidence)throw new IllegalStateException("扫描件不能为空"); },
      "failingGuard", (TaskListener)t->{throw new IllegalStateException("listener failure");}));
  engine=cfg.buildProcessEngine();
  var raw=new BpmTaskServiceImpl();
  set(raw,"taskService",engine.getTaskService());set(raw,"runtimeService",engine.getRuntimeService());
  set(raw,"historyService",engine.getHistoryService());set(raw,"managementService",engine.getManagementService());
  set(raw,"transactionManager",tm);
  var instances=mock(BpmProcessInstanceService.class);
  when(instances.getProcessInstance(anyString())).thenAnswer(i->engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(i.getArgument(0)).includeProcessVariables().singleResult());
  set(raw,"processInstanceService",instances);
  var models=mock(BpmModelService.class);when(models.getBpmnModelByDefinitionId(anyString())).thenAnswer(i->engine.getRepositoryService().getBpmnModel(i.getArgument(0)));set(raw,"modelService",models);
  var defs=mock(BpmProcessDefinitionService.class);when(defs.getProcessDefinitionInfo(anyString())).thenReturn(new BpmProcessDefinitionInfoDO().setAutoApprovalType(2));set(raw,"bpmProcessDefinitionService",defs);
  set(raw,"notificationManager",mock(BpmNotificationManager.class));set(raw,"messageService",mock(BpmMessageService.class));
  var policy=mock(BpmInitiatorWithdrawPolicyService.class);
  doAnswer(inv->{ ((Runnable)inv.getArgument(3)).run(); return null; }).when(policy).runAfterCompletion(any(), any(), any(), any());
  doAnswer(inv->{ ((Runnable)inv.getArgument(1)).run(); return null; }).when(policy).withTaskLock(any(), any());
  doNothing().when(policy).validateCurrentTask(any());
  set(raw,"initiatorWithdrawPolicyService",policy);
  var users=mock(AdminUserApi.class);when(users.getUser(any())).thenReturn(CommonResult.success(new AdminUserRespDTO().setId(853L).setNickname("fixture")));set(raw,"adminUserApi",users);
  var proxy=new ProxyFactory(raw);proxy.setProxyTargetClass(true);proxy.addAdvice(new TransactionInterceptor(tm,new AnnotationTransactionAttributeSource()));service=(BpmTaskServiceImpl)proxy.getProxy();
  beans=mockStatic(SpringUtil.class);beans.when(()->SpringUtil.getBean(BpmTaskServiceImpl.class)).thenReturn(service);
 }
 @AfterEach void close(){ if(beans!=null)beans.close(); if(engine!=null)engine.close(); }
 static void set(Object o,String f,Object v){ReflectionTestUtils.setField(o,f,v);}
 Task create(String listener){
  String xml="""
   <?xml version="1.0" encoding="UTF-8"?>
   <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn" targetNamespace="test">
    <process id="contract" isExecutable="true"><startEvent id="start"/><sequenceFlow id="a" sourceRef="start" targetRef="legal"/>
    <userTask id="legal" flowable:assignee="853"/><sequenceFlow id="b" sourceRef="legal" targetRef="taskSeal"/>
    <userTask id="taskSeal" name="用印" flowable:assignee="853">%s</userTask>
    <sequenceFlow id="c" sourceRef="taskSeal" targetRef="end"/><endEvent id="end"/></process></definitions>
   """.formatted(listener.isEmpty()?"":"<extensionElements><flowable:taskListener event=\"complete\" delegateExpression=\"${"+listener+"}\"/></extensionElements>");
  engine.getRepositoryService().createDeployment().tenantId("1").addString("auto.bpmn20.xml",xml).deploy();
  var pi=engine.getRuntimeService().startProcessInstanceByKeyAndTenantId("contract", Map.of(),"1");
  Task legal=engine.getTaskService().createTaskQuery().processInstanceId(pi.getId()).singleResult();
  engine.getTaskService().setVariableLocal(legal.getId(),"TASK_STATUS",2);engine.getTaskService().complete(legal.getId());
  Task seal=engine.getTaskService().createTaskQuery().processInstanceId(pi.getId()).singleResult();
  engine.getTaskService().setVariableLocal(seal.getId(),"TASK_STATUS",1);
  return engine.getTaskService().createTaskQuery().taskId(seal.getId()).includeTaskLocalVariables().singleResult();
 }
 void assigned(Task task){new TransactionTemplate(tm).executeWithoutResult(s->service.processTaskAssigned(task));}
 @Test void executionEvidenceNodeRemainsManualEvenWhenSameApprover(){
  Task t=create("financeContractExecCompleteGuardListener");assigned(t);
  assertNotNull(engine.getTaskService().createTaskQuery().taskId(t.getId()).singleResult());
  assertEquals(0,guardCalls,"dedup must not attempt execution node completion");
  assertEquals(1,engine.getTaskService().getVariableLocal(t.getId(),"TASK_STATUS"));
 }
 @Test void automaticListenerFailureRollsBackStatusAndReason(){
  Task t=create("failingGuard");assigned(t);
  assertEquals(1,engine.getTaskService().getVariableLocal(t.getId(),"TASK_STATUS"));
  assertNull(engine.getTaskService().getVariableLocal(t.getId(),"TASK_REASON"));
  assertEquals(1, engine.getHistoryService().createHistoricVariableInstanceQuery().taskId(t.getId()).variableName("TASK_STATUS").singleResult().getValue());
  assertNull(engine.getHistoryService().createHistoricTaskInstanceQuery().taskId(t.getId()).singleResult().getEndTime());
 }
 @Test void ordinaryConsecutiveApprovalStillCompletes(){Task t=create("");assigned(t);assertNull(engine.getTaskService().createTaskQuery().taskId(t.getId()).singleResult());}
 @Test void manualExecutionStillRequiresEvidenceAndThenCompletes(){
  Task t=create("financeContractExecCompleteGuardListener");
  assertThrows(Exception.class,()->service.approveTask(853L,new BpmTaskApproveReqVO().setId(t.getId()).setReason("人工办理")));
  assertEquals(1,engine.getTaskService().getVariableLocal(t.getId(),"TASK_STATUS"));
  evidence=true;service.approveTask(853L,new BpmTaskApproveReqVO().setId(t.getId()).setReason("人工办理"));
  assertNull(engine.getTaskService().createTaskQuery().taskId(t.getId()).singleResult());
 }
}
