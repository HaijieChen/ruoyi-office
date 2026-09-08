package cn.iocoder.yudao.module.bpm.service.task;
import cn.iocoder.yudao.module.bpm.service.definition.BpmModelServiceImpl;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import org.flowable.engine.*;
import org.flowable.engine.repository.Model;
import org.flowable.engine.runtime.ProcessInstanceQuery;
import org.flowable.engine.history.HistoricProcessInstanceQuery;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class BpmModelCleanTenantTest {
 BpmModelServiceImpl service; RepositoryService repo; RuntimeService runtime; HistoryService history; TaskService tasks; Model model;
 @BeforeEach void setup(){
  service=new BpmModelServiceImpl();repo=mock(RepositoryService.class);runtime=mock(RuntimeService.class);history=mock(HistoryService.class);tasks=mock(TaskService.class);model=mock(Model.class);
  ReflectionTestUtils.setField(service,"repositoryService",repo);ReflectionTestUtils.setField(service,"runtimeService",runtime);ReflectionTestUtils.setField(service,"historyService",history);ReflectionTestUtils.setField(service,"taskService",tasks);
  when(repo.getModel("model")).thenReturn(model);when(model.getCreateTime()).thenReturn(new java.util.Date());when(model.getLastUpdateTime()).thenReturn(new java.util.Date());when(model.getMetaInfo()).thenReturn("{\"managerUserIds\":[1]}");when(model.getTenantId()).thenReturn("1");when(model.getKey()).thenReturn("oa_leave");TenantContextHolder.setTenantId(1L);
 }
 @AfterEach void clear(){TenantContextHolder.clear();}
 @Test void rejectsOtherTenantBeforeDeletion(){when(model.getTenantId()).thenReturn("2");assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,()->service.cleanModel(1L,"model"));verifyNoInteractions(runtime,history,tasks);}
 @Test void queriesAreScoped(){
  var pq=mock(ProcessInstanceQuery.class,RETURNS_SELF);var hq=mock(HistoricProcessInstanceQuery.class,RETURNS_SELF);var tq=mock(TaskQuery.class,RETURNS_SELF);
  when(runtime.createProcessInstanceQuery()).thenReturn(pq);when(history.createHistoricProcessInstanceQuery()).thenReturn(hq);when(tasks.createTaskQuery()).thenReturn(tq);
  when(pq.list()).thenReturn(List.of());when(hq.list()).thenReturn(List.of());when(tq.list()).thenReturn(List.of());service.cleanModel(1L,"model");
  verify(pq).processInstanceTenantId("1");verify(hq).processInstanceTenantId("1");verify(tq).taskTenantId("1");
 }
}
