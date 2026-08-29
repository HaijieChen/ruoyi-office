package cn.iocoder.yudao.module.hrm.controller.admin.employee;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.http.HttpUtils;
import cn.iocoder.yudao.framework.excel.core.util.ExcelUtils;
import cn.iocoder.yudao.module.hrm.controller.admin.employee.vo.*;
import cn.iocoder.yudao.module.hrm.service.employee.EmployeeService;
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

import static cn.iocoder.yudao.framework.apilog.core.enums.OperateTypeEnum.EXPORT;
import static cn.iocoder.yudao.framework.apilog.core.enums.OperateTypeEnum.IMPORT;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 员工档案管理")
@RestController
@RequestMapping("/hrm/employee-archive")
@Validated
public class EmployeeController {

    @Resource
    private EmployeeService employeeArchiveService;

    @PostMapping("/create")
    @Operation(summary = "创建员工档案")
    @PreAuthorize("@ss.hasPermission('hrm:employee-archive:create')")
    public CommonResult<Long> createEmployeeArchive(@Valid @RequestBody EmployeeSaveReqVO createReqVO) {
        return success(employeeArchiveService.createEmployeeArchive(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新员工档案")
    @PreAuthorize("@ss.hasPermission('hrm:employee-archive:update')")
    public CommonResult<Boolean> updateEmployeeArchive(@Valid @RequestBody EmployeeSaveReqVO updateReqVO) {
        employeeArchiveService.updateEmployeeArchive(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除员工档案")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('hrm:employee-archive:delete')")
    public CommonResult<Boolean> deleteEmployeeArchive(@RequestParam("id") Long id) {
        employeeArchiveService.deleteEmployeeArchive(id);
        return success(true);
    }

    @DeleteMapping("/delete-list")
    @Operation(summary = "批量删除员工档案")
    @Parameter(name = "ids", description = "编号列表", required = true, example = "[1,2,3]")
    @PreAuthorize("@ss.hasPermission('hrm:employee-archive:delete')")
    public CommonResult<Boolean> deleteEmployeeArchiveList(@RequestParam("ids") List<Long> ids) {
        employeeArchiveService.deleteEmployeeArchiveList(ids);
        return success(true);
    }

    @GetMapping("/my-employments")
    @Operation(summary = "当前登录人任职公司")
    public CommonResult<List<EmployeeEmploymentVO>> getMyEmployments() {
        return success(employeeArchiveService.listMyEmployments(
                cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId()));
    }

    @GetMapping("/colleagues")
    @Operation(summary = "当前登录人任职公司下的同事（含本人）")
    public CommonResult<List<cn.iocoder.yudao.module.hrm.api.employee.dto.EmployeeColleagueRespDTO>> getColleagues() {
        return success(employeeArchiveService.listColleaguesByUserId(
                cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId()));
    }

    @GetMapping("/bank-by-user-id")
    @Operation(summary = "按系统用户查工资卡（报销预填，登录即可）")
    @Parameter(name = "userId", description = "系统用户编号", required = true, example = "1")
    public CommonResult<EmployeeWageCardRespVO> getWageCardByUserId(@RequestParam("userId") Long userId) {
        return success(employeeArchiveService.getWageCardByUserId(userId));
    }

    @GetMapping("/get")
    @Operation(summary = "获得员工档案")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('hrm:employee-archive:query')")
    public CommonResult<EmployeeRespVO> getEmployeeArchive(@RequestParam("id") Long id) {
        EmployeeRespVO archive = employeeArchiveService.getEmployeeArchive(id);
        return success(archive);
    }

    @GetMapping("/page")
    @Operation(summary = "获得员工档案分页")
    @PreAuthorize("@ss.hasPermission('hrm:employee-archive:query')")
    public CommonResult<PageResult<EmployeeRespVO>> getEmployeeArchivePage(@Valid EmployeePageReqVO pageReqVO) {
        PageResult<EmployeeRespVO> pageResult = employeeArchiveService.getEmployeeArchivePage(pageReqVO);
        return success(pageResult);
    }

    @GetMapping("/select-page")
    @Operation(summary = "员工档案选择分页（过滤正式员工）")
    @PreAuthorize("@ss.hasPermission('hrm:employee-archive:query')")
    public CommonResult<PageResult<EmployeeRespVO>> getEmployeeArchiveSelectablePage(@Valid EmployeeSelectPageReqVO pageReqVO) {
        PageResult<EmployeeRespVO> pageResult = employeeArchiveService.getEmployeeArchiveSelectablePage(pageReqVO);
        return success(pageResult);
    }

    @GetMapping("/export-excel")
    @Operation(summary = "导出文枢花名册 Excel")
    @PreAuthorize("@ss.hasPermission('hrm:employee-archive:export')")
    @ApiAccessLog(operateType = EXPORT)
    public void exportEmployeeArchiveExcel(@Valid EmployeePageReqVO pageReqVO,
                                           HttpServletResponse response) throws IOException {
        List<EmployeeRosterExportVO> list = employeeArchiveService.getEmployeeRosterExportList(pageReqVO);
        ExcelUtils.write(response, "文枢花名册.xlsx", "文枢在职", EmployeeRosterExportVO.class, list);
    }

    @GetMapping("/get-import-template")
    @Operation(summary = "下载文枢花名册导入模板（表头与官方附件第 2 行一致）")
    @PreAuthorize("@ss.hasPermission('hrm:employee-archive:export') or @ss.hasPermission('hrm:employee-archive:create')")
    public void getImportTemplate(HttpServletResponse response) throws IOException {
        // 直接下发官方模板字节，保证 52 列 title 含换行/长文案与附件逐字一致
        ClassPathResource resource = new ClassPathResource("excel/文枢花名册导入模板.xlsx");
        if (!resource.exists()) {
            throw new IllegalStateException("导入模板资源不存在");
        }
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.addHeader("Content-Disposition",
                "attachment;filename=" + HttpUtils.encodeUtf8("文枢花名册导入模板.xlsx"));
        try (InputStream in = resource.getInputStream()) {
            StreamUtils.copy(in, response.getOutputStream());
        }
        response.flushBuffer();
    }

    @PostMapping("/import")
    @Operation(summary = "导入文枢花名册（按身份证号 upsert，部分成功）")
    @PreAuthorize("@ss.hasPermission('hrm:employee-archive:create')")
    @ApiAccessLog(operateType = IMPORT)
    public CommonResult<EmployeeRosterImportRespVO> importEmployeeRoster(
            @RequestParam("file") MultipartFile file) throws IOException {
        // 官方模板：第 1 行标题、第 2 行表头 → headRowNumber=2
        // MultipartFile 流只能读一次：先落内存
        byte[] bytes = file.getBytes();
        List<EmployeeRosterImportExcelVO> rows =
                ExcelUtils.read(bytes, EmployeeRosterImportExcelVO.class, 2);
        // 兼容仅表头无标题行：若读出空且文件非空，再按首行表头试一次
        if (rows.isEmpty() && bytes.length > 0) {
            rows = ExcelUtils.read(bytes, EmployeeRosterImportExcelVO.class, 1);
        }
        return success(employeeArchiveService.importEmployeeRosterList(rows));
    }

    @PostMapping("/onboarding-file/upload")
    @Operation(summary = "上传入职资料并签发一次性 claim（不返回公开 URL）")
    @PreAuthorize("@ss.hasPermission('hrm:employee-archive:create') or @ss.hasPermission('hrm:employee-archive:update') "
            + "or @ss.hasPermission('hrm:employee-entry-bill:create') or @ss.hasPermission('hrm:employee-entry-bill:update')")
    public CommonResult<OnboardingFileClaimRespVO> uploadOnboardingFile(
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file) throws Exception {
        return success(employeeArchiveService.uploadOnboardingFile(file));
    }

    @GetMapping("/onboarding-attachment/download")
    @Operation(summary = "下载入职资料附件（需登录且具备员工档案查询权限）")
    @PreAuthorize("@ss.hasPermission('hrm:employee-archive:query')")
    public void downloadOnboardingAttachment(@RequestParam("employeeId") Long employeeId,
                                             @RequestParam("attachmentId") Long attachmentId,
                                             HttpServletResponse response) throws Exception {
        employeeArchiveService.downloadOnboardingAttachment(employeeId, attachmentId, response);
    }

    @PostMapping("/generate-user")
    @Operation(summary = "为员工生成系统用户")
    @Parameter(name = "id", description = "员工编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('hrm:employee-archive:create')")
    public CommonResult<Long> generateUserForEmployee(@RequestParam("id") Long id,
                                                      @RequestParam(value = "roleIds", required = false) List<Long> roleIds) {
        Long userId = employeeArchiveService.generateUserForEmployee(id, roleIds);
        return success(userId);
    }

    @PostMapping("/batch-generate-user")
    @Operation(summary = "批量为员工生成系统用户")
    @Parameter(name = "ids", description = "员工编号列表", required = true, example = "[1,2,3]")
    @PreAuthorize("@ss.hasPermission('hrm:employee-archive:create')")
    public CommonResult<Boolean> batchGenerateUserForEmployee(@RequestParam("ids") String ids,
                                                              @RequestParam(value = "roleIds", required = false) List<Long> roleIds) {
        List<Long> idList = List.of(ids.split(",")).stream()
                .map(Long::valueOf)
                .collect(java.util.stream.Collectors.toList());
        employeeArchiveService.batchGenerateUserForEmployee(idList, roleIds);
        return success(true);
    }

}

