package cn.iocoder.yudao.module.finance.controller.admin.allocation;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.excel.core.util.ExcelUtils;
import cn.iocoder.yudao.module.finance.controller.admin.allocation.vo.FinanceDeptCostAllocationImportExcelVO;
import cn.iocoder.yudao.module.finance.controller.admin.allocation.vo.FinanceDeptCostAllocationImportRespVO;
import cn.iocoder.yudao.module.finance.service.allocation.FinanceDeptCostAllocationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - 部门费用分摊")
@RestController
@RequestMapping("/finance/dept-cost-allocation")
@Validated
public class FinanceDeptCostAllocationController {

    private final FinanceDeptCostAllocationService allocationService;

    public FinanceDeptCostAllocationController(FinanceDeptCostAllocationService allocationService) {
        this.allocationService = allocationService;
    }

    @GetMapping("/get-import-template")
    @Operation(summary = "获得部门费用分摊导入模板")
    @PreAuthorize("@ss.hasPermission('finance:dept-allocation:import')")
    public void importTemplate(HttpServletResponse response) throws IOException {
        List<FinanceDeptCostAllocationImportExcelVO> list = List.of(
                FinanceDeptCostAllocationImportExcelVO.builder()
                        .period("2026-08")
                        .deptName("研发")
                        .deptId("")
                        .amount("1000.00")
                        .remark("示例")
                        .build()
        );
        ExcelUtils.write(response, "部门费用分摊导入模板.xls", "分摊",
                FinanceDeptCostAllocationImportExcelVO.class, list);
    }

    @PostMapping("/import")
    @Operation(summary = "导入部门费用分摊。sourceType 为表单必选：薪资 / 云服务 / 其他；Excel 不含来源类型列")
    @PreAuthorize("@ss.hasPermission('finance:dept-allocation:import')")
    public CommonResult<FinanceDeptCostAllocationImportRespVO> importAllocation(
            @RequestParam("file") MultipartFile file,
            @RequestParam("sourceType") String sourceType) throws IOException {
        return success(allocationService.importAllocationList(
                ExcelUtils.read(file, FinanceDeptCostAllocationImportExcelVO.class),
                sourceType, getLoginUserId()));
    }
}
