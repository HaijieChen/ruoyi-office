package cn.iocoder.yudao.module.bpm.controller.admin.definition;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.process.BpmProcessDefinitionPageReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.process.BpmProcessDefinitionRespVO;
import cn.iocoder.yudao.module.bpm.convert.definition.BpmProcessDefinitionConvert;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmCategoryDO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmFormDO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmProcessDefinitionInfoDO;
import cn.iocoder.yudao.module.bpm.service.definition.BpmCategoryService;
import cn.iocoder.yudao.module.bpm.service.definition.BpmFormService;
import cn.iocoder.yudao.module.bpm.service.definition.BpmModelService;
import cn.iocoder.yudao.module.bpm.service.definition.BpmProcessDefinitionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.common.engine.impl.db.SuspensionState;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.Model;
import org.flowable.engine.repository.ProcessDefinition;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertList;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertMap;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertSet;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - 流程定义")
@RestController
@RequestMapping("/bpm/process-definition")
@Validated
public class BpmProcessDefinitionController {

    @Resource
    private BpmProcessDefinitionService processDefinitionService;
    @Resource
    private BpmFormService formService;
    @Resource
    private BpmCategoryService categoryService;
    @Resource
    private BpmModelService modelService;

    @GetMapping("/page")
    @Operation(summary = "获得流程定义分页")
    @PreAuthorize("@ss.hasPermission('bpm:process-definition:query')")
    public CommonResult<PageResult<BpmProcessDefinitionRespVO>> getProcessDefinitionPage(
            BpmProcessDefinitionPageReqVO pageReqVO) {
        PageResult<ProcessDefinition> pageResult = processDefinitionService.getProcessDefinitionPage(pageReqVO);
        if (CollUtil.isEmpty(pageResult.getList())) {
            return success(PageResult.empty(pageResult.getTotal()));
        }

        // 获得 Category Map
        Map<String, BpmCategoryDO> categoryMap = categoryService.getCategoryMap(
                convertSet(pageResult.getList(), ProcessDefinition::getCategory));
        // 获得 Deployment Map
        Map<String, Deployment> deploymentMap = processDefinitionService.getDeploymentMap(
                convertSet(pageResult.getList(), ProcessDefinition::getDeploymentId));
        // 获得 BpmProcessDefinitionInfoDO Map
        Map<String, BpmProcessDefinitionInfoDO> processDefinitionMap = processDefinitionService.getProcessDefinitionInfoMap(
                convertSet(pageResult.getList(), ProcessDefinition::getId));
        // 获得 Form Map
        Map<Long, BpmFormDO> formMap = formService.getFormMap(
               convertSet(processDefinitionMap.values(), BpmProcessDefinitionInfoDO::getFormId));
        return success(BpmProcessDefinitionConvert.INSTANCE.buildProcessDefinitionPage(
                pageResult, deploymentMap, processDefinitionMap, formMap, categoryMap));
    }

    @GetMapping ("/list")
    @Operation(summary = "获得流程定义列表")
    @Parameter(name = "suspensionState", description = "挂起状态", required = true, example = "1") // 参见 Flowable SuspensionState 枚举
    public CommonResult<List<BpmProcessDefinitionRespVO>> getProcessDefinitionList(
            @RequestParam("suspensionState") Integer suspensionState) {
        // 1.1 获得开启的流程定义
        List<ProcessDefinition> list = processDefinitionService.getProcessDefinitionListBySuspensionState(suspensionState);
        if (CollUtil.isEmpty(list)) {
            return success(Collections.emptyList());
        }
        // 1.2 移除不可见的流程定义
        Map<String, BpmProcessDefinitionInfoDO> processDefinitionMap = processDefinitionService.getProcessDefinitionInfoMap(
                convertSet(list, ProcessDefinition::getId));
        Long userId = getLoginUserId();
        list.removeIf(processDefinition -> {
            BpmProcessDefinitionInfoDO processDefinitionInfo = processDefinitionMap.get(processDefinition.getId());
            if (processDefinitionInfo == null) {
                return false;
            }
            if (Boolean.FALSE.equals(processDefinitionInfo.getVisible())) {
                return true;
            }
            return !processDefinitionService.canUserStartProcessDefinition(processDefinitionInfo, userId);
        });

        Map<String, Model> modelByKey = convertMap(modelService.getModelList(null), Model::getKey);
        Set<String> categoryCodes = new HashSet<>();
        for (ProcessDefinition definition : list) {
            Model model = modelByKey.get(definition.getKey());
            String code = model != null && StrUtil.isNotBlank(model.getCategory())
                    ? model.getCategory() : definition.getCategory();
            if (StrUtil.isNotBlank(code)) {
                categoryCodes.add(code);
            }
        }
        Map<String, BpmCategoryDO> categoryMap = categoryService.getCategoryMap(categoryCodes);
        List<BpmProcessDefinitionRespVO> voList = BpmProcessDefinitionConvert.INSTANCE.buildProcessDefinitionList(
                list, null, processDefinitionMap, null, categoryMap);
        for (BpmProcessDefinitionRespVO vo : voList) {
            Model model = modelByKey.get(vo.getKey());
            String code = model != null && StrUtil.isNotBlank(model.getCategory())
                    ? model.getCategory() : vo.getCategory();
            vo.setCategory(code);
            BpmCategoryDO category = categoryMap.get(code);
            if (category != null) {
                vo.setCategoryName(category.getName());
            }
            vo.setCanStart(true);
        }
        return success(voList);
    }

    @GetMapping("/simple-list")
    @Operation(summary = "获得流程定义精简列表", description = "只包含未挂起的流程，主要用于前端的下拉选项")
    public CommonResult<List<BpmProcessDefinitionRespVO>> getSimpleProcessDefinitionList(
            @RequestParam(value = "category", required = false) String category) {
        // 只查询未挂起的流程
        List<ProcessDefinition> list = processDefinitionService.getProcessDefinitionListBySuspensionState(
                SuspensionState.ACTIVE.getStateCode());
        // 根据流程分类进行过滤（可选）
        if (StrUtil.isNotBlank(category)) {
            list.removeIf(definition -> !StrUtil.equals(category, definition.getCategory()));
        }
        // 拼接 VO 返回，只返回 id、name、key
        return success(convertList(list, definition -> new BpmProcessDefinitionRespVO()
                .setId(definition.getId()).setName(definition.getName()).setKey(definition.getKey())));
    }

    @GetMapping ("/get")
    @Operation(summary = "获得流程定义")
    @Parameter(name = "id", description = "流程编号", required = true, example = "1024")
    @Parameter(name = "key", description = "流程定义标识", required = true, example = "1024")
    public CommonResult<BpmProcessDefinitionRespVO> getProcessDefinition(
            @RequestParam(value = "id", required = false) String id,
            @RequestParam(value = "key", required = false) String key) {
        ProcessDefinition processDefinition = id != null ? processDefinitionService.getProcessDefinition(id)
                : processDefinitionService.getActiveProcessDefinition(key);
        if (processDefinition == null) {
            return success(null);
        }
        BpmProcessDefinitionInfoDO processDefinitionInfo = processDefinitionService.getProcessDefinitionInfo(processDefinition.getId());
        BpmnModel bpmnModel = processDefinitionService.getProcessDefinitionBpmnModel(processDefinition.getId());
        BpmProcessDefinitionRespVO respVO = BpmProcessDefinitionConvert.INSTANCE.buildProcessDefinition(
                processDefinition, null, processDefinitionInfo, null, null, bpmnModel);
        fillStartEligibility(respVO);
        return success(respVO);
    }

    @GetMapping("/allowed-employments")
    @Operation(summary = "当前登录人可发起该流程的任职")
    @Parameter(name = "id", description = "流程定义编号", required = true)
    public CommonResult<List<Map<String, Object>>> getAllowedEmployments(
            @RequestParam("id") String id) {
        BpmProcessDefinitionInfoDO info = processDefinitionService.getProcessDefinitionInfo(id);
        if (info == null) {
            return success(Collections.emptyList());
        }
        List<cn.iocoder.yudao.module.bpm.api.task.BpmStartEmploymentProvider.Employment> list =
                processDefinitionService.listAllowedStartEmployments(info, getLoginUserId());
        List<Map<String, Object>> body = convertList(list, item -> {
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("deptId", item.deptId());
            row.put("companyDeptId", item.companyDeptId());
            row.put("signed", item.signed());
            row.put("companyName", item.companyName());
            row.put("deptName", item.deptName());
            return row;
        });
        return success(body);
    }

    private void fillStartEligibility(List<BpmProcessDefinitionRespVO> list) {
        if (CollUtil.isEmpty(list)) {
            return;
        }
        for (BpmProcessDefinitionRespVO vo : list) {
            fillStartEligibility(vo);
        }
    }

    private void fillStartEligibility(BpmProcessDefinitionRespVO vo) {
        if (vo == null) {
            return;
        }
        boolean canStart = true;
        if (StrUtil.isNotBlank(vo.getId())) {
            BpmProcessDefinitionInfoDO info = processDefinitionService.getProcessDefinitionInfo(vo.getId());
            canStart = info != null
                    && !Boolean.FALSE.equals(info.getVisible())
                    && processDefinitionService.canUserStartProcessDefinition(info, getLoginUserId());
        }
        vo.setCanStart(canStart);
        vo.setRequiredStartPermission(null);
        vo.setCannotStartReason(canStart ? null : "当前流程未对你开放发起");
    }

}

