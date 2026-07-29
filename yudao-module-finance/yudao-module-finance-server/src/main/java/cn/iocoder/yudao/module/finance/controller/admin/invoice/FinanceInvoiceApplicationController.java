package cn.iocoder.yudao.module.finance.controller.admin.invoice;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.finance.controller.admin.invoice.vo.*;
import cn.iocoder.yudao.module.finance.dal.dataobject.invoice.FinanceInvoiceApplicationDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.invoice.FinanceInvoiceApplicationLineDO;
import cn.iocoder.yudao.module.finance.service.invoice.FinanceInvoiceApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - 开票申请")
@RestController
@RequestMapping("/finance/invoice-application")
@Validated
public class FinanceInvoiceApplicationController {

    @Resource
    private FinanceInvoiceApplicationService invoiceApplicationService;

    @PostMapping("/create-and-start")
    @Operation(summary = "创建开票申请并启动审批（无草稿）")
    @PreAuthorize("@ss.hasPermission('finance:invoice-application:create')")
    public CommonResult<Long> createAndStart(@Valid @RequestBody FinanceInvoiceApplicationCreateAndStartReqVO reqVO) {
        return success(invoiceApplicationService.createAndStart(reqVO, getLoginUserId()));
    }

    @PutMapping("/resubmit")
    @Operation(summary = "驳回后重提开票申请（释占→换明细→再占→新流程）")
    @Parameter(name = "id", description = "申请编号", required = true)
    @PreAuthorize("@ss.hasPermission('finance:invoice-application:resubmit')")
    public CommonResult<Boolean> resubmit(@RequestParam("id") Long id,
                                          @Valid @RequestBody FinanceInvoiceApplicationResubmitReqVO reqVO) {
        invoiceApplicationService.resubmit(id, reqVO, getLoginUserId());
        return success(true);
    }

    @PostMapping("/resubmit")
    @Operation(summary = "驳回后重提开票申请（POST 别名）")
    @Parameter(name = "id", description = "申请编号", required = true)
    @PreAuthorize("@ss.hasPermission('finance:invoice-application:resubmit')")
    public CommonResult<Boolean> resubmitPost(@RequestParam("id") Long id,
                                              @Valid @RequestBody FinanceInvoiceApplicationResubmitReqVO reqVO) {
        invoiceApplicationService.resubmit(id, reqVO, getLoginUserId());
        return success(true);
    }

    @PutMapping("/update-issue-progress")
    @Operation(summary = "办票进度（一行一票）")
    @PreAuthorize("@ss.hasPermission('finance:invoice-application:issue')")
    public CommonResult<Boolean> updateIssueProgress(
            @Valid @RequestBody FinanceInvoiceApplicationUpdateIssueProgressReqVO reqVO) {
        invoiceApplicationService.updateIssueProgress(reqVO);
        return success(true);
    }

    @PostMapping("/update-issue-progress")
    @Operation(summary = "办票进度（POST 别名）")
    @PreAuthorize("@ss.hasPermission('finance:invoice-application:issue')")
    public CommonResult<Boolean> updateIssueProgressPost(
            @Valid @RequestBody FinanceInvoiceApplicationUpdateIssueProgressReqVO reqVO) {
        invoiceApplicationService.updateIssueProgress(reqVO);
        return success(true);
    }

    @PostMapping("/on-approval-outcome")
    @Operation(summary = "同步审批落账（内部/联调；生产主路径走 Flowable Delegate）")
    @PreAuthorize("@ss.hasPermission('finance:invoice-application:update')")
    public CommonResult<Boolean> onApprovalOutcome(
            @Valid @RequestBody FinanceInvoiceApplicationApprovalOutcomeReqVO reqVO) {
        invoiceApplicationService.onApprovalOutcome(reqVO.getApplicationId(), reqVO.getOutcome());
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得开票申请详情")
    @Parameter(name = "id", description = "申请编号", required = true)
    @PreAuthorize("@ss.hasPermission('finance:invoice-application:query')")
    public CommonResult<FinanceInvoiceApplicationRespVO> getApplication(@RequestParam("id") Long id) {
        FinanceInvoiceApplicationDO application = invoiceApplicationService.getApplication(id);
        FinanceInvoiceApplicationRespVO respVO = BeanUtils.toBean(application, FinanceInvoiceApplicationRespVO.class);
        List<FinanceInvoiceApplicationLineDO> lines = invoiceApplicationService.getApplicationLines(id);
        respVO.setLines(BeanUtils.toBean(lines, FinanceInvoiceApplicationRespVO.Line.class));
        return success(respVO);
    }

    @GetMapping("/page")
    @Operation(summary = "获得开票申请分页")
    @PreAuthorize("@ss.hasPermission('finance:invoice-application:query')")
    public CommonResult<PageResult<FinanceInvoiceApplicationRespVO>> getApplicationPage(
            @Valid FinanceInvoiceApplicationPageReqVO pageReqVO) {
        PageResult<FinanceInvoiceApplicationDO> page = invoiceApplicationService.getApplicationPage(pageReqVO);
        return success(BeanUtils.toBean(page, FinanceInvoiceApplicationRespVO.class));
    }

}
