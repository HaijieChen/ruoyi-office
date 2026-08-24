package cn.iocoder.yudao.module.finance.controller.admin.invoice;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.finance.controller.admin.invoice.vo.FinanceInvoiceApplicationCompleteIssueReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.invoice.vo.FinanceInvoiceApplicationRespVO;
import cn.iocoder.yudao.module.finance.controller.admin.invoice.vo.FinanceInvoiceRedflushCreateAndStartReqVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.invoice.FinanceInvoiceRedflushDO;
import cn.iocoder.yudao.module.finance.service.invoice.FinanceInvoiceApplicationService;
import cn.iocoder.yudao.module.finance.service.invoice.FinanceInvoiceRedflushService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - 开票红冲")
@RestController
@RequestMapping("/finance/invoice-redflush")
@Validated
public class FinanceInvoiceRedflushController {

    @Resource
    private FinanceInvoiceApplicationService invoiceApplicationService;
    @Resource
    private FinanceInvoiceRedflushService redflushService;

    @GetMapping("/source-invoice-application-list")
    @Operation(summary = "红冲可选前置开票申请（已办完票、无认领、未锁定）")
    @PreAuthorize("@ss.hasPermission('finance:invoice-application:query')")
    public CommonResult<List<FinanceInvoiceApplicationRespVO>> listSelectablePredecessors() {
        return success(BeanUtils.toBean(
                invoiceApplicationService.listSelectableForRedFlush(),
                FinanceInvoiceApplicationRespVO.class));
    }

    @PostMapping("/create-and-start")
    @Operation(summary = "创建红冲申请并启动审批（不占商务单）")
    @PreAuthorize("@ss.hasPermission('finance:invoice-application:create')")
    public CommonResult<Long> createAndStart(@Valid @RequestBody FinanceInvoiceRedflushCreateAndStartReqVO reqVO) {
        return success(redflushService.createAndStart(reqVO, getLoginUserId()));
    }

    @PostMapping("/resubmit")
    @Operation(summary = "驳回后重提红冲申请")
    @PreAuthorize("@ss.hasPermission('finance:invoice-application:resubmit')")
    public CommonResult<Boolean> resubmit(@RequestParam("id") Long id,
                                          @Valid @RequestBody FinanceInvoiceRedflushCreateAndStartReqVO reqVO) {
        redflushService.resubmit(id, reqVO, getLoginUserId());
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "红冲申请详情")
    @PreAuthorize("@ss.hasPermission('finance:invoice-application:query')")
    public CommonResult<FinanceInvoiceRedflushDO> get(@RequestParam("id") Long id) {
        return success(redflushService.get(id));
    }

    @PostMapping("/complete-issue")
    @Operation(summary = "红冲办票：释原单占用并标记已红冲")
    @PreAuthorize("@ss.hasPermission('finance:invoice-application:issue')")
    public CommonResult<Boolean> completeIssue(@RequestParam("id") Long id,
                                               @Valid @RequestBody FinanceInvoiceApplicationCompleteIssueReqVO reqVO) {
        redflushService.completeIssue(id, reqVO);
        return success(true);
    }
}
