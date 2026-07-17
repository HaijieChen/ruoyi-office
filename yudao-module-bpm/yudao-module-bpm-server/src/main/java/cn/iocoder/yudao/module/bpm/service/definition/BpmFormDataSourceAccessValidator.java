package cn.iocoder.yudao.module.bpm.service.definition;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmProcessDefinitionInfoDO;
import cn.iocoder.yudao.module.bpm.enums.definition.BpmFieldPermissionEnum;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.BpmnModelUtils;
import cn.iocoder.yudao.module.bpm.service.task.BpmTaskService;
import lombok.RequiredArgsConstructor;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.BPM_DATA_SOURCE_FORM_ACCESS_DENIED;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.BPM_DATA_SOURCE_FORM_NOT_REFERENCED;
import static cn.iocoder.yudao.module.bpm.enums.task.BpmnModelConstants.START_USER_NODE_ID;

/**
 * Authorizes runtime data-source access against an immutable deployed process-definition snapshot.
 *
 * <p>A mutable form id by itself is not an authorization boundary. A caller must prove either that it may start the
 * active process definition or that it currently owns/is assigned the active approval task. The source reference is
 * then checked against the deployed {@link BpmProcessDefinitionInfoDO#getFormFields()} snapshot, so editing the form
 * later cannot widen access for an existing definition or process instance.</p>
 */
@Component
@RequiredArgsConstructor
public class BpmFormDataSourceAccessValidator {

    private final BpmFormDataSourceReferenceValidator referenceValidator;
    private final BpmProcessDefinitionService processDefinitionService;
    private final BpmTaskService taskService;

    public RuntimeAccessContext validateRuntimeAccess(Long formId, String sourceCode, LoginUser loginUser,
                                                      String processDefinitionId, String taskId) {
        if (loginUser == null || loginUser.getId() == null || TenantContextHolder.getTenantId() == null) {
            throw exception(BPM_DATA_SOURCE_FORM_ACCESS_DENIED);
        }
        boolean definitionContext = StringUtils.hasText(processDefinitionId);
        boolean taskContext = StringUtils.hasText(taskId);
        if (definitionContext == taskContext) { // exactly one trusted context is required
            throw exception(BPM_DATA_SOURCE_FORM_ACCESS_DENIED);
        }

        if (definitionContext) {
            validateStartContext(formId, sourceCode, loginUser.getId(), processDefinitionId);
            return new RuntimeAccessContext(null);
        } else {
            return new RuntimeAccessContext(validateTaskContext(formId, sourceCode, loginUser.getId(), taskId));
        }
    }

    private void validateStartContext(Long formId, String sourceCode, Long userId, String processDefinitionId) {
        ProcessDefinition definition = processDefinitionService.getProcessDefinition(processDefinitionId);
        if (definition == null || definition.isSuspended() || !currentTenant().equals(definition.getTenantId())) {
            throw exception(BPM_DATA_SOURCE_FORM_ACCESS_DENIED);
        }
        BpmProcessDefinitionInfoDO info = processDefinitionService.getProcessDefinitionInfo(definition.getId());
        List<BpmFormDataSourceReferenceValidator.FormDataSourceReference> matchingReferences =
                validateSnapshot(info, formId, sourceCode);
        if (!processDefinitionService.canUserStartProcessDefinition(info, userId)) {
            throw exception(BPM_DATA_SOURCE_FORM_ACCESS_DENIED);
        }
        validateFieldPermission(definition.getId(), START_USER_NODE_ID, matchingReferences);
    }

    private String validateTaskContext(Long formId, String sourceCode, Long userId, String taskId) {
        Task task = taskService.getTask(taskId);
        String user = String.valueOf(userId);
        if (task == null || task.isSuspended() || !currentTenant().equals(task.getTenantId())
                || !StringUtils.hasText(task.getProcessInstanceId())
                || !user.equals(task.getAssignee()) && !user.equals(task.getOwner())) {
            throw exception(BPM_DATA_SOURCE_FORM_ACCESS_DENIED);
        }
        BpmProcessDefinitionInfoDO info = processDefinitionService.getProcessDefinitionInfo(
                task.getProcessDefinitionId());
        List<BpmFormDataSourceReferenceValidator.FormDataSourceReference> matchingReferences =
                validateSnapshot(info, formId, sourceCode);
        validateFieldPermission(task.getProcessDefinitionId(), task.getTaskDefinitionKey(), matchingReferences);
        return task.getProcessInstanceId();
    }

    private List<BpmFormDataSourceReferenceValidator.FormDataSourceReference> validateSnapshot(
            BpmProcessDefinitionInfoDO info, Long formId, String sourceCode) {
        if (info == null || !Objects.equals(formId, info.getFormId())) {
            throw exception(BPM_DATA_SOURCE_FORM_ACCESS_DENIED);
        }
        BpmFormDataSourceReferenceValidator.ReferenceParseResult references =
                referenceValidator.parseReferences(info.getFormFields());
        if (!references.valid() || !StringUtils.hasText(sourceCode)) {
            throw exception(BPM_DATA_SOURCE_FORM_NOT_REFERENCED);
        }
        List<BpmFormDataSourceReferenceValidator.FormDataSourceReference> matchingReferences =
                references.references().stream()
                        .filter(reference -> sourceCode.equals(reference.sourceCode()))
                        .toList();
        if (matchingReferences.isEmpty()) {
            throw exception(BPM_DATA_SOURCE_FORM_NOT_REFERENCED);
        }
        return matchingReferences;
    }

    private void validateFieldPermission(String processDefinitionId, String nodeId,
                                         List<BpmFormDataSourceReferenceValidator.FormDataSourceReference>
                                                 matchingReferences) {
        BpmnModel bpmnModel = processDefinitionService.getProcessDefinitionBpmnModel(processDefinitionId);
        Map<String, String> fieldPermissions = BpmnModelUtils.parseFormFieldsPermission(bpmnModel, nodeId);
        if (fieldPermissions == null || fieldPermissions.isEmpty()) {
            return; // Historical definitions without an explicit map keep the platform's visible/read-only default.
        }
        String hiddenPermission = String.valueOf(BpmFieldPermissionEnum.NONE.getPermission());
        boolean allMatchingFieldsHidden = matchingReferences.stream()
                .allMatch(reference -> hiddenPermission.equals(fieldPermissions.get(reference.field())));
        if (allMatchingFieldsHidden) {
            throw exception(BPM_DATA_SOURCE_FORM_ACCESS_DENIED);
        }
    }

    private static String currentTenant() {
        return String.valueOf(TenantContextHolder.getRequiredTenantId());
    }

    /** Trusted audit context derived from server-side Flowable state, never from a client process-instance id. */
    public record RuntimeAccessContext(String processInstanceId) {
    }

}
