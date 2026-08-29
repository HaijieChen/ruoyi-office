package cn.iocoder.yudao.module.system.controller.admin.dept;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.http.HttpUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.system.controller.admin.dept.vo.dept.DeptImportRespVO;
import cn.iocoder.yudao.module.system.controller.admin.dept.vo.dept.DeptListReqVO;
import cn.iocoder.yudao.module.system.controller.admin.dept.vo.dept.DeptRespVO;
import cn.iocoder.yudao.module.system.controller.admin.dept.vo.dept.DeptSaveReqVO;
import cn.iocoder.yudao.module.system.controller.admin.dept.vo.dept.DeptSimpleRespVO;
import cn.iocoder.yudao.module.system.dal.dataobject.dept.DeptDO;
import cn.iocoder.yudao.module.system.service.dept.DeptImportService;
import cn.iocoder.yudao.module.system.service.dept.DeptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StreamUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static cn.iocoder.yudao.framework.apilog.core.enums.OperateTypeEnum.IMPORT;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 部门")
@RestController
@RequestMapping("/system/dept")
@Validated
public class DeptController {

    @Resource
    private DeptService deptService;
    @Resource
    private DeptImportService deptImportService;

    @PostMapping("create")
    @Operation(summary = "创建部门")
    @PreAuthorize("@ss.hasPermission('system:dept:create')")
    public CommonResult<Long> createDept(@Valid @RequestBody DeptSaveReqVO createReqVO) {
        Long deptId = deptService.createDept(createReqVO);
        return success(deptId);
    }

    @PutMapping("update")
    @Operation(summary = "更新部门")
    @PreAuthorize("@ss.hasPermission('system:dept:update')")
    public CommonResult<Boolean> updateDept(@Valid @RequestBody DeptSaveReqVO updateReqVO) {
        deptService.updateDept(updateReqVO);
        return success(true);
    }

    @DeleteMapping("delete")
    @Operation(summary = "删除部门")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('system:dept:delete')")
    public CommonResult<Boolean> deleteDept(@RequestParam("id") Long id) {
        deptService.deleteDept(id);
        return success(true);
    }

    @DeleteMapping("/delete-list")
    @Operation(summary = "批量删除部门")
    @Parameter(name = "ids", description = "编号列表", required = true)
    @PreAuthorize("@ss.hasPermission('system:dept:delete')")
    public CommonResult<Boolean> deleteDeptList(@RequestParam("ids") List<Long> ids) {
        deptService.deleteDeptList(ids);
        return success(true);
    }

    @GetMapping("/list")
    @Operation(summary = "获取部门列表")
    @PreAuthorize("@ss.hasPermission('system:dept:query')")
    public CommonResult<List<DeptRespVO>> getDeptList(DeptListReqVO reqVO) {
        List<DeptDO> list = deptService.getDeptList(reqVO);
        return success(BeanUtils.toBean(list, DeptRespVO.class));
    }

    @GetMapping("/company-list")
    @Operation(summary = "获取公司列表")
    @PreAuthorize("@ss.hasPermission('system:dept:query')")
    public CommonResult<List<DeptRespVO>> getCompanyList(DeptListReqVO reqVO) {
        List<DeptDO> list = deptService.getCompanyList(reqVO);
        return success(BeanUtils.toBean(list, DeptRespVO.class));
    }

    @GetMapping("/company-simple-list")
    @Operation(summary = "获取启用公司精简列表", description = "仅返回当前租户已启用的公司，供下拉选项使用")
    public CommonResult<List<DeptSimpleRespVO>> getSimpleCompanyList() {
        List<DeptDO> list = deptService.getCompanyList(
                new DeptListReqVO().setStatus(CommonStatusEnum.ENABLE.getStatus()));
        return success(BeanUtils.toBean(list, DeptSimpleRespVO.class));
    }

    @GetMapping(value = {"/list-all-simple", "/simple-list"})
    @Operation(summary = "获取部门精简信息列表", description = "只包含被开启的部门，主要用于前端的下拉选项")
    public CommonResult<List<DeptSimpleRespVO>> getSimpleDeptList() {
        List<DeptDO> list = deptService.getDeptList(
                new DeptListReqVO().setStatus(CommonStatusEnum.ENABLE.getStatus()));
        return success(BeanUtils.toBean(list, DeptSimpleRespVO.class));
    }

    @GetMapping("/get")
    @Operation(summary = "获得部门信息")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('system:dept:query')")
    public CommonResult<DeptRespVO> getDept(@RequestParam("id") Long id) {
        DeptDO dept = deptService.getDept(id);
        return success(BeanUtils.toBean(dept, DeptRespVO.class));
    }

    @GetMapping("/get-import-template")
    @Operation(summary = "下载组织架构导入模板")
    @PreAuthorize("@ss.hasPermission('system:dept:import')")
    public void getImportTemplate(HttpServletResponse response) throws IOException {
        ClassPathResource resource = new ClassPathResource("excel/组织架构导入模板.xlsx");
        if (!resource.exists()) {
            throw new IllegalStateException("组织架构导入模板资源不存在");
        }
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.addHeader("Content-Disposition",
                "attachment;filename=" + HttpUtils.encodeUtf8("组织架构导入模板.xlsx"));
        try (InputStream in = resource.getInputStream()) {
            StreamUtils.copy(in, response.getOutputStream());
        }
        response.flushBuffer();
    }

    @PostMapping("/import/validate")
    @Operation(summary = "校验组织架构导入文件（只读预览，零写入）")
    @PreAuthorize("@ss.hasPermission('system:dept:import')")
    public CommonResult<DeptImportRespVO> validateImportDept(@RequestParam("file") MultipartFile file) {
        return success(deptImportService.validateImport(file));
    }

    @PostMapping("/import")
    @Operation(summary = "导入组织架构（新增或同路径更新，整批原子提交）")
    @PreAuthorize("@ss.hasPermission('system:dept:import')")
    @ApiAccessLog(operateType = IMPORT)
    public CommonResult<DeptImportRespVO> importDept(@RequestParam("file") MultipartFile file,
                                                     @RequestParam("expectedDigest") String expectedDigest) {
        return success(deptImportService.importDepts(file, expectedDigest));
    }

}
