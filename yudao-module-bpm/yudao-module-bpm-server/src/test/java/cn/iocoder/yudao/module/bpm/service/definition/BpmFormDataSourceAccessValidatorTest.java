package cn.iocoder.yudao.module.bpm.service.definition;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmProcessDefinitionInfoDO;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.BpmnModelUtils;
import cn.iocoder.yudao.module.bpm.service.task.BpmTaskService;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.module.bpm.enums.task.BpmnModelConstants.START_USER_NODE_ID;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.BPM_DATA_SOURCE_FORM_ACCESS_DENIED;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.BPM_DATA_SOURCE_FORM_NOT_REFERENCED;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BpmFormDataSourceAccessValidatorTest {

    private static final Long TENANT_ID = 9L;
    private static final Long USER_ID = 7L;
    private static final Long FORM_ID = 11L;
    private static final String SOURCE_CODE = "oa_available_seals";
    private static final String DEFINITION_ID = "oa-seal:1:100";
    private static final String TASK_ID = "task-200";

    private BpmProcessDefinitionService processDefinitionService;
    private BpmTaskService taskService;
    private BpmFormDataSourceAccessValidator validator;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT_ID);
        processDefinitionService = mock(BpmProcessDefinitionService.class);
        taskService = mock(BpmTaskService.class);
        BpmFormDataSourceReferenceValidator referenceValidator =
                new BpmFormDataSourceReferenceValidator(mock(BpmFormService.class));
        validator = new BpmFormDataSourceAccessValidator(referenceValidator, processDefinitionService, taskService);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void acceptsAuthorizedActiveProcessDefinitionSnapshot() {
        ProcessDefinition definition = definition(false, "9");
        BpmProcessDefinitionInfoDO info = info(remoteField(SOURCE_CODE));
        when(processDefinitionService.getProcessDefinition(DEFINITION_ID)).thenReturn(definition);
        when(processDefinitionService.getProcessDefinitionInfo(DEFINITION_ID)).thenReturn(info);
        when(processDefinitionService.canUserStartProcessDefinition(info, USER_ID)).thenReturn(true);

        assertDoesNotThrow(() -> validator.validateRuntimeAccess(FORM_ID, SOURCE_CODE, loginUser(),
                DEFINITION_ID, null));
    }

    @Test
    void rejectsSourceWhenAllMatchingStartFieldsAreExplicitlyHidden() {
        ProcessDefinition definition = definition(false, "9");
        BpmProcessDefinitionInfoDO info = info(remoteField(SOURCE_CODE));
        when(processDefinitionService.getProcessDefinition(DEFINITION_ID)).thenReturn(definition);
        when(processDefinitionService.getProcessDefinitionInfo(DEFINITION_ID)).thenReturn(info);
        when(processDefinitionService.canUserStartProcessDefinition(info, USER_ID)).thenReturn(true);
        when(processDefinitionService.getProcessDefinitionBpmnModel(DEFINITION_ID))
                .thenReturn(modelWithFieldPermission(START_USER_NODE_ID, "sealIds", "3"));

        assertDenied(() -> validator.validateRuntimeAccess(FORM_ID, SOURCE_CODE, loginUser(),
                DEFINITION_ID, null));
    }

    @Test
    void acceptsSourceWhenAnyMatchingStartFieldIsVisible() {
        ProcessDefinition definition = definition(false, "9");
        BpmProcessDefinitionInfoDO info = info(remoteField(SOURCE_CODE), remoteField("alternate", SOURCE_CODE));
        when(processDefinitionService.getProcessDefinition(DEFINITION_ID)).thenReturn(definition);
        when(processDefinitionService.getProcessDefinitionInfo(DEFINITION_ID)).thenReturn(info);
        when(processDefinitionService.canUserStartProcessDefinition(info, USER_ID)).thenReturn(true);
        BpmnModel model = modelWithFieldPermission(START_USER_NODE_ID, "sealIds", "3");
        addFieldPermission(model, START_USER_NODE_ID, "alternate", "1");
        when(processDefinitionService.getProcessDefinitionBpmnModel(DEFINITION_ID)).thenReturn(model);

        assertDoesNotThrow(() -> validator.validateRuntimeAccess(FORM_ID, SOURCE_CODE, loginUser(),
                DEFINITION_ID, null));
    }

    @Test
    void rejectsMissingOrAmbiguousAccessContext() {
        assertDenied(() -> validator.validateRuntimeAccess(FORM_ID, SOURCE_CODE, loginUser(), null, null));
        assertDenied(() -> validator.validateRuntimeAccess(FORM_ID, SOURCE_CODE, loginUser(),
                DEFINITION_ID, TASK_ID));
    }

    @Test
    void rejectsUnauthorizedSuspendedOrCrossTenantDefinition() {
        BpmProcessDefinitionInfoDO info = info(remoteField(SOURCE_CODE));
        ProcessDefinition unauthorizedDefinition = definition(false, "9");
        when(processDefinitionService.getProcessDefinition(DEFINITION_ID)).thenReturn(unauthorizedDefinition);
        when(processDefinitionService.getProcessDefinitionInfo(DEFINITION_ID)).thenReturn(info);
        when(processDefinitionService.canUserStartProcessDefinition(info, USER_ID)).thenReturn(false);
        assertDenied(() -> validator.validateRuntimeAccess(FORM_ID, SOURCE_CODE, loginUser(),
                DEFINITION_ID, null));

        ProcessDefinition suspendedDefinition = definition(true, "9");
        when(processDefinitionService.getProcessDefinition(DEFINITION_ID)).thenReturn(suspendedDefinition);
        assertDenied(() -> validator.validateRuntimeAccess(FORM_ID, SOURCE_CODE, loginUser(),
                DEFINITION_ID, null));

        ProcessDefinition crossTenantDefinition = definition(false, "10");
        when(processDefinitionService.getProcessDefinition(DEFINITION_ID)).thenReturn(crossTenantDefinition);
        assertDenied(() -> validator.validateRuntimeAccess(FORM_ID, SOURCE_CODE, loginUser(),
                DEFINITION_ID, null));
    }

    @Test
    void acceptsActiveTaskAssignedToUserOrOwnedByUser() {
        Task assignedTask = task("7", null, "9");
        when(taskService.getTask(TASK_ID)).thenReturn(assignedTask);
        when(processDefinitionService.getProcessDefinitionInfo(DEFINITION_ID))
                .thenReturn(info(remoteField(SOURCE_CODE)));
        assertEquals("process-300", validator.validateRuntimeAccess(FORM_ID, SOURCE_CODE, loginUser(),
                null, TASK_ID).processInstanceId());

        Task ownedTask = task("8", "7", "9");
        when(taskService.getTask(TASK_ID)).thenReturn(ownedTask);
        assertDoesNotThrow(() -> validator.validateRuntimeAccess(FORM_ID, SOURCE_CODE, loginUser(),
                null, TASK_ID));
    }

    @Test
    void rejectsSourceWhenAllMatchingTaskFieldsAreExplicitlyHidden() {
        Task assignedTask = task("7", null, "9");
        when(taskService.getTask(TASK_ID)).thenReturn(assignedTask);
        when(processDefinitionService.getProcessDefinitionInfo(DEFINITION_ID))
                .thenReturn(info(remoteField(SOURCE_CODE)));
        when(processDefinitionService.getProcessDefinitionBpmnModel(DEFINITION_ID))
                .thenReturn(modelWithFieldPermission("ApproveTask", "sealIds", "3"));

        assertDenied(() -> validator.validateRuntimeAccess(FORM_ID, SOURCE_CODE, loginUser(), null, TASK_ID));
    }

    @Test
    void acceptsTaskSourceWhenPermissionMapIsAbsentOrFieldIsReadable() {
        Task assignedTask = task("7", null, "9");
        when(taskService.getTask(TASK_ID)).thenReturn(assignedTask);
        when(processDefinitionService.getProcessDefinitionInfo(DEFINITION_ID))
                .thenReturn(info(remoteField(SOURCE_CODE)));

        assertDoesNotThrow(() -> validator.validateRuntimeAccess(FORM_ID, SOURCE_CODE, loginUser(), null, TASK_ID));

        when(processDefinitionService.getProcessDefinitionBpmnModel(DEFINITION_ID))
                .thenReturn(modelWithFieldPermission("ApproveTask", "sealIds", "1"));
        assertDoesNotThrow(() -> validator.validateRuntimeAccess(FORM_ID, SOURCE_CODE, loginUser(), null, TASK_ID));
    }

    @Test
    void rejectsBlankWrongOrCrossTenantTaskIdentity() {
        Task unassignedTask = task(null, null, "9");
        when(taskService.getTask(TASK_ID)).thenReturn(unassignedTask);
        assertDenied(() -> validator.validateRuntimeAccess(FORM_ID, SOURCE_CODE, loginUser(), null, TASK_ID));

        Task wrongIdentityTask = task("8", "6", "9");
        when(taskService.getTask(TASK_ID)).thenReturn(wrongIdentityTask);
        assertDenied(() -> validator.validateRuntimeAccess(FORM_ID, SOURCE_CODE, loginUser(), null, TASK_ID));

        Task crossTenantTask = task("7", null, "10");
        when(taskService.getTask(TASK_ID)).thenReturn(crossTenantTask);
        assertDenied(() -> validator.validateRuntimeAccess(FORM_ID, SOURCE_CODE, loginUser(), null, TASK_ID));

        Task suspended = task("7", null, "9");
        when(suspended.isSuspended()).thenReturn(true);
        when(taskService.getTask(TASK_ID)).thenReturn(suspended);
        assertDenied(() -> validator.validateRuntimeAccess(FORM_ID, SOURCE_CODE, loginUser(), null, TASK_ID));

        when(taskService.getTask(TASK_ID)).thenReturn(null);
        assertDenied(() -> validator.validateRuntimeAccess(FORM_ID, SOURCE_CODE, loginUser(), null, TASK_ID));
    }

    @Test
    void rejectsFormMismatchAndUsesImmutableDefinitionSnapshot() {
        BpmProcessDefinitionInfoDO wrongForm = info(remoteField(SOURCE_CODE)).setFormId(99L);
        ProcessDefinition definition = definition(false, "9");
        when(processDefinitionService.getProcessDefinition(DEFINITION_ID)).thenReturn(definition);
        when(processDefinitionService.getProcessDefinitionInfo(DEFINITION_ID)).thenReturn(wrongForm);
        when(processDefinitionService.canUserStartProcessDefinition(wrongForm, USER_ID)).thenReturn(true);
        assertDenied(() -> validator.validateRuntimeAccess(FORM_ID, SOURCE_CODE, loginUser(),
                DEFINITION_ID, null));

        BpmProcessDefinitionInfoDO snapshotWithoutSource = info(remoteField("another_source"));
        when(processDefinitionService.getProcessDefinitionInfo(DEFINITION_ID)).thenReturn(snapshotWithoutSource);
        when(processDefinitionService.canUserStartProcessDefinition(snapshotWithoutSource, USER_ID)).thenReturn(true);
        ServiceException error = assertThrows(ServiceException.class,
                () -> validator.validateRuntimeAccess(FORM_ID, SOURCE_CODE, loginUser(), DEFINITION_ID, null));
        assertEquals(BPM_DATA_SOURCE_FORM_NOT_REFERENCED.getCode(), error.getCode());
    }

    private static LoginUser loginUser() {
        return new LoginUser().setId(USER_ID).setTenantId(TENANT_ID);
    }

    private static ProcessDefinition definition(boolean suspended, String tenantId) {
        ProcessDefinition definition = mock(ProcessDefinition.class);
        when(definition.getId()).thenReturn(DEFINITION_ID);
        when(definition.getTenantId()).thenReturn(tenantId);
        when(definition.isSuspended()).thenReturn(suspended);
        return definition;
    }

    private static Task task(String assignee, String owner, String tenantId) {
        Task task = mock(Task.class);
        when(task.getId()).thenReturn(TASK_ID);
        when(task.getProcessDefinitionId()).thenReturn(DEFINITION_ID);
        when(task.getProcessInstanceId()).thenReturn("process-300");
        when(task.getTaskDefinitionKey()).thenReturn("ApproveTask");
        when(task.getTenantId()).thenReturn(tenantId);
        when(task.getAssignee()).thenReturn(assignee);
        when(task.getOwner()).thenReturn(owner);
        return task;
    }

    private static BpmProcessDefinitionInfoDO info(String... fields) {
        return new BpmProcessDefinitionInfoDO().setProcessDefinitionId(DEFINITION_ID)
                .setFormId(FORM_ID).setFormFields(List.of(fields));
    }

    private static String remoteField(String code) {
        return remoteField("sealIds", code);
    }

    private static String remoteField(String field, String code) {
        return "{\"type\":\"RemoteDataSourceSelect\",\"field\":\"" + field + "\"," +
                "\"props\":{\"dataSourceCode\":\"" + code + "\"}}";
    }

    private static BpmnModel modelWithFieldPermission(String nodeId, String field, String permission) {
        BpmnModel model = new BpmnModel();
        org.flowable.bpmn.model.Process process = new org.flowable.bpmn.model.Process();
        UserTask task = new UserTask();
        task.setId(nodeId);
        BpmnModelUtils.addFormFieldsPermission(List.of(Map.of("field", field, "permission", permission)), task);
        process.addFlowElement(task);
        model.addProcess(process);
        return model;
    }

    private static void addFieldPermission(BpmnModel model, String nodeId, String field, String permission) {
        UserTask task = (UserTask) BpmnModelUtils.getFlowElementById(model, nodeId);
        BpmnModelUtils.addFormFieldsPermission(List.of(Map.of("field", field, "permission", permission)), task);
    }

    private static void assertDenied(org.junit.jupiter.api.function.Executable executable) {
        ServiceException error = assertThrows(ServiceException.class, executable);
        assertEquals(BPM_DATA_SOURCE_FORM_ACCESS_DENIED.getCode(), error.getCode());
    }

}
