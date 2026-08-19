package cn.iocoder.yudao.module.finance.controller.admin.report;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.finance.controller.admin.report.vo.FinanceGrossMarginReportPageReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.report.vo.FinanceGrossMarginReportPageRespVO;
import cn.iocoder.yudao.module.finance.service.report.FinanceGrossMarginReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 产品毛利表")
@RestController
@RequestMapping("/finance/report/gross-margin")
@Validated
public class FinanceGrossMarginReportController {

    @Resource
    private FinanceGrossMarginReportService grossMarginReportService;

    @GetMapping("/page")
    @Operation(summary = "获得产品毛利表分页")
    @PreAuthorize("@ss.hasPermission('finance:report-gross-margin:query')")
    public CommonResult<FinanceGrossMarginReportPageRespVO> getPage(
            @Valid FinanceGrossMarginReportPageReqVO pageReqVO) {
        return success(grossMarginReportService.getPage(pageReqVO));
    }
}
