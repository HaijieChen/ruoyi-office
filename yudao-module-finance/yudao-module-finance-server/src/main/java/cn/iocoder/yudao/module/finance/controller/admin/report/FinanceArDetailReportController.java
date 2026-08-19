package cn.iocoder.yudao.module.finance.controller.admin.report;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.module.finance.controller.admin.report.vo.FinanceArDetailReportPageReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.report.vo.FinanceArDetailReportPageRespVO;
import cn.iocoder.yudao.module.finance.service.report.FinanceArDetailReportService;
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
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - 应收明细表")
@RestController
@RequestMapping("/finance/report/ar-detail")
@Validated
public class FinanceArDetailReportController {

    static final String QUERY_ALL_PERMISSION = "finance:report-ar:query-all";

    @Resource
    private FinanceArDetailReportService arDetailReportService;
    @Resource
    private SecurityFrameworkService securityFrameworkService;

    @GetMapping("/page")
    @Operation(summary = "获得应收明细分页")
    @PreAuthorize("@ss.hasPermission('finance:report-ar:query')")
    public CommonResult<FinanceArDetailReportPageRespVO> getPage(@Valid FinanceArDetailReportPageReqVO pageReqVO) {
        boolean queryAll = securityFrameworkService.hasPermission(QUERY_ALL_PERMISSION);
        return success(arDetailReportService.getPage(pageReqVO, getLoginUserId(), queryAll));
    }
}
