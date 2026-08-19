package cn.iocoder.yudao.module.finance.controller.admin.report;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.finance.controller.admin.report.vo.FinanceDeptProfitReportReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.report.vo.FinanceDeptProfitReportRespVO;
import cn.iocoder.yudao.module.finance.service.report.FinanceDeptProfitReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 部门利润表")
@RestController
@RequestMapping("/finance/report/dept-profit")
@Validated
public class FinanceDeptProfitReportController {

    @Resource
    private FinanceDeptProfitReportService deptProfitReportService;

    @GetMapping("/list")
    @Operation(summary = "获得部门利润表")
    @PreAuthorize("@ss.hasPermission('finance:report-dept-profit:query')")
    public CommonResult<FinanceDeptProfitReportRespVO> list(FinanceDeptProfitReportReqVO reqVO) {
        return success(deptProfitReportService.query(reqVO));
    }
}
