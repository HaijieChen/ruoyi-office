package cn.iocoder.yudao.module.bpm.controller.admin.definition;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.config.SecurityProperties;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.datasource.*;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmFormDataSourceDO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmFormDataSourceVersionDO;
import cn.iocoder.yudao.module.bpm.framework.datasource.BpmFormDataSourceQueryResult;
import cn.iocoder.yudao.module.bpm.service.definition.BpmFormDataSourceExecutionService;
import cn.iocoder.yudao.module.bpm.service.definition.BpmFormDataSourceAccessValidator;
import cn.iocoder.yudao.module.bpm.service.definition.BpmFormDataSourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertList;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUser;

@Tag(name = "管理后台 - BPM 表单数据源")
@RestController
@RequestMapping("/bpm/form-data-source")
@Validated
public class BpmFormDataSourceController {

    @Resource
    private BpmFormDataSourceService dataSourceService;
    @Resource
    private BpmFormDataSourceExecutionService executionService;
    @Resource
    private BpmFormDataSourceAccessValidator accessValidator;
    @Resource
    private SecurityProperties securityProperties;

    @PostMapping("/create")
    @Operation(summary = "创建表单数据源定义")
    @PreAuthorize("@ss.hasPermission('bpm:form-data-source:create')")
    public CommonResult<Long> createDataSource(@Valid @RequestBody BpmFormDataSourceSaveReqVO reqVO) {
        return success(dataSourceService.createDataSource(reqVO));
    }

    @PostMapping("/create-with-draft")
    @Operation(summary = "原子创建表单数据源定义和首个版本草稿")
    @PreAuthorize("@ss.hasPermission('bpm:form-data-source:create')")
    public CommonResult<Long> createDataSourceWithDraft(
            @Valid @RequestBody BpmFormDataSourceCreateWithDraftReqVO reqVO) {
        return success(dataSourceService.createDataSourceWithDraft(reqVO.getDefinition(), reqVO.getVersion()));
    }

    @PutMapping("/update")
    @Operation(summary = "更新表单数据源定义")
    @PreAuthorize("@ss.hasPermission('bpm:form-data-source:update')")
    public CommonResult<Boolean> updateDataSource(@Valid @RequestBody BpmFormDataSourceSaveReqVO reqVO) {
        dataSourceService.updateDataSource(reqVO);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得表单数据源定义")
    @Parameter(name = "id", description = "数据源编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('bpm:form-data-source:query')")
    public CommonResult<BpmFormDataSourceRespVO> getDataSource(
            @RequestParam("id") @Positive(message = "数据源编号必须大于 0") Long id) {
        BpmFormDataSourceDO dataSource = dataSourceService.getDataSource(id);
        return success(BeanUtils.toBean(dataSource, BpmFormDataSourceRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得表单数据源定义分页")
    @PreAuthorize("@ss.hasPermission('bpm:form-data-source:query')")
    public CommonResult<PageResult<BpmFormDataSourceRespVO>> getDataSourcePage(
            @Valid BpmFormDataSourcePageReqVO reqVO) {
        PageResult<BpmFormDataSourceDO> pageResult = dataSourceService.getDataSourcePage(reqVO);
        return success(BeanUtils.toBean(pageResult, BpmFormDataSourceRespVO.class));
    }

    @GetMapping("/simple-list")
    @Operation(summary = "获得已启用且已发布的表单数据源精简列表",
            description = "用于表单设计器选择数据源，不返回 SQL、配置或 Schema")
    @PreAuthorize("@ss.hasPermission('bpm:form-data-source:query')")
    public CommonResult<List<BpmFormDataSourceSimpleRespVO>> getSimpleDataSourceList() {
        List<BpmFormDataSourceDO> list = dataSourceService.getSimpleDataSourceList();
        return success(convertList(list, source -> BeanUtils.toBean(source, BpmFormDataSourceSimpleRespVO.class)));
    }

    @GetMapping("/published-metadata")
    @Operation(summary = "获得已发布数据源的安全字段元数据",
            description = "用于表单设计器生成字段下拉，不返回 SQL、接口路径、原始配置或原始 Schema")
    @PreAuthorize("@ss.hasPermission('bpm:form-data-source:query')")
    public CommonResult<BpmFormDataSourcePublishedMetadataRespVO> getPublishedMetadata(
            @RequestParam("code") @NotBlank(message = "数据源标识不能为空")
            @Size(max = 127, message = "数据源标识长度不能超过 127 个字符")
            @Pattern(regexp = "^[a-z][a-z0-9_]*$", message = "数据源标识格式不正确") String code) {
        return success(BpmFormDataSourcePublishedMetadataRespVO.from(dataSourceService.getPublishedMetadata(code)));
    }

    @GetMapping("/version/list")
    @Operation(summary = "获得表单数据源版本历史")
    @Parameter(name = "sourceId", description = "数据源编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('bpm:form-data-source:query')")
    public CommonResult<List<BpmFormDataSourceVersionSummaryRespVO>> getVersionList(
            @RequestParam("sourceId") @Positive(message = "数据源编号必须大于 0") Long sourceId) {
        List<BpmFormDataSourceVersionDO> versions = dataSourceService.getVersionList(sourceId);
        return success(BeanUtils.toBean(versions, BpmFormDataSourceVersionSummaryRespVO.class));
    }

    @GetMapping("/version/get")
    @Operation(summary = "获得表单数据源版本配置",
            description = "仅具有编辑权限的管理员可读取草稿或历史配置；运行时接口永不返回配置")
    @Parameters({
            @Parameter(name = "sourceId", description = "数据源编号", required = true, example = "1024"),
            @Parameter(name = "versionId", description = "版本记录编号", required = true, example = "2048")
    })
    @PreAuthorize("@ss.hasPermission('bpm:form-data-source:update')")
    public CommonResult<BpmFormDataSourceVersionRespVO> getVersion(
            @RequestParam("sourceId") @Positive(message = "数据源编号必须大于 0") Long sourceId,
            @RequestParam("versionId") @Positive(message = "版本记录编号必须大于 0") Long versionId) {
        return success(BeanUtils.toBean(dataSourceService.getVersion(sourceId, versionId),
                BpmFormDataSourceVersionRespVO.class));
    }

    @PostMapping("/version/save-draft")
    @Operation(summary = "保存表单数据源版本草稿")
    @Parameter(name = "sourceId", description = "数据源编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('bpm:form-data-source:update')")
    public CommonResult<Long> saveDraft(
            @RequestParam("sourceId") @Positive(message = "数据源编号必须大于 0") Long sourceId,
            @Valid @RequestBody BpmFormDataSourceVersionSaveReqVO reqVO) {
        return success(dataSourceService.saveDraft(sourceId, reqVO));
    }

    @PostMapping("/version/trial-run")
    @Operation(summary = "试运行已保存的表单数据源草稿版本",
            description = "仅管理端使用，不会临时发布版本，也不会使用运行时缓存")
    @PreAuthorize("@ss.hasPermission('bpm:form-data-source:update')")
    public CommonResult<BpmFormDataSourceExecuteRespVO> trialRun(
            @Valid @RequestBody BpmFormDataSourceTrialRunReqVO reqVO, HttpServletRequest request) {
        BpmFormDataSourceQueryResult result = dataSourceService.trialRun(reqVO.getSourceId(), reqVO.getVersionId(),
                reqVO.getParams(), getLoginUser(), getRawAuthorization(request));
        return success(toExecuteRespVO(result));
    }

    @PostMapping("/version/publish")
    @Operation(summary = "发布表单数据源版本")
    @Parameters({
            @Parameter(name = "sourceId", description = "数据源编号", required = true, example = "1024"),
            @Parameter(name = "versionId", description = "版本记录编号", required = true, example = "2048")
    })
    @PreAuthorize("@ss.hasPermission('bpm:form-data-source:publish')")
    public CommonResult<Boolean> publish(
            @RequestParam("sourceId") @Positive(message = "数据源编号必须大于 0") Long sourceId,
            @RequestParam("versionId") @Positive(message = "版本记录编号必须大于 0") Long versionId) {
        dataSourceService.publish(sourceId, versionId);
        return success(true);
    }

    @PutMapping("/disable")
    @Operation(summary = "停用表单数据源")
    @Parameter(name = "id", description = "数据源编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('bpm:form-data-source:update')")
    public CommonResult<Boolean> disable(
            @RequestParam("id") @Positive(message = "数据源编号必须大于 0") Long id) {
        dataSourceService.disable(id);
        return success(true);
    }

    @PostMapping("/execute/{code}")
    @Operation(summary = "执行表单引用的已发布数据源",
            description = "只返回经过 Schema 投影和脱敏的业务结果，不返回 SQL、数据源配置或 Schema")
    @PreAuthorize("isAuthenticated()")
    public CommonResult<BpmFormDataSourceExecuteRespVO> execute(
            @PathVariable("code") @NotBlank(message = "数据源标识不能为空")
            @Size(max = 127, message = "数据源标识长度不能超过 127 个字符")
            @Pattern(regexp = "^[a-z][a-z0-9_]*$", message = "数据源标识格式不正确") String code,
            @Valid @RequestBody BpmFormDataSourceExecuteReqVO reqVO, HttpServletRequest request) {
        LoginUser loginUser = getLoginUser();
        BpmFormDataSourceAccessValidator.RuntimeAccessContext accessContext =
                accessValidator.validateRuntimeAccess(reqVO.getFormId(), code, loginUser,
                        reqVO.getProcessDefinitionId(), reqVO.getTaskId());
        BpmFormDataSourceQueryResult result = executionService.execute(code, reqVO.getParams(), loginUser,
                getRawAuthorization(request), reqVO.getFormId(), accessContext.processInstanceId());
        return success(toExecuteRespVO(result));
    }

    private String getRawAuthorization(HttpServletRequest request) {
        return request.getHeader(securityProperties.getTokenHeader());
    }

    private static BpmFormDataSourceExecuteRespVO toExecuteRespVO(BpmFormDataSourceQueryResult result) {
        return new BpmFormDataSourceExecuteRespVO().setRows(result.getRows())
                .setTotal(result.getTotal()).setVersion(result.getVersion());
    }

}
