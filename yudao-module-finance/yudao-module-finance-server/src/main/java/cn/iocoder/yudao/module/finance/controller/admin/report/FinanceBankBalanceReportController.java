package cn.iocoder.yudao.module.finance.controller.admin.report;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.finance.controller.admin.report.vo.FinanceBankBalanceReportReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.report.vo.FinanceBankBalanceReportRespVO;
import cn.iocoder.yudao.module.finance.service.report.FinanceBankBalanceReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 银行余额表")
@RestController
@RequestMapping("/finance/report/bank-balance")
@Validated
public class FinanceBankBalanceReportController {

    @Resource
    private FinanceBankBalanceReportService bankBalanceReportService;

    @GetMapping("/list")
    @Operation(summary = "获得银行余额表")
    @PreAuthorize("@ss.hasPermission('finance:report-bank-balance:query')")
    public CommonResult<FinanceBankBalanceReportRespVO> list(FinanceBankBalanceReportReqVO reqVO) {
        return success(bankBalanceReportService.query(reqVO));
    }
}
