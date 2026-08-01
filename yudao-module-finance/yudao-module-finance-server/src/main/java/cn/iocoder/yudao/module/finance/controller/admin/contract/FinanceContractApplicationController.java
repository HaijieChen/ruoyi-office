package cn.iocoder.yudao.module.finance.controller.admin.contract;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.finance.controller.admin.contract.vo.*;
import cn.iocoder.yudao.module.finance.dal.dataobject.contract.FinanceContractApplicationDO;
import cn.iocoder.yudao.module.finance.service.contract.FinanceContractApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - 合同签约申请")
@RestController
@RequestMapping("/finance/contract-application")
@Validated
public class FinanceContractApplicationController {

    @Resource
    private FinanceContractApplicationService contractApplicationService;

    @PostMapping("/create-and-start")
    @Operation(summary = "创建合同签约申请并启动审批（无草稿）")
    @PreAuthorize("@ss.hasPermission('finance:contract-application:create')")
    public CommonResult<Long> createAndStart(@Valid @RequestBody FinanceContractApplicationCreateAndStartReqVO reqVO) {
        return success(contractApplicationService.createAndStart(reqVO, getLoginUserId()));
    }

    @PutMapping("/resubmit")
    @Operation(summary = "驳回后重提（整链重批）")
    @Parameter(name = "id", description = "申请编号", required = true)
    @PreAuthorize("@ss.hasPermission('finance:contract-application:resubmit')")
    public CommonResult<Boolean> resubmit(@RequestParam("id") Long id,
                                          @Valid @RequestBody FinanceContractApplicationResubmitReqVO reqVO) {
        contractApplicationService.resubmit(id, reqVO, getLoginUserId());
        return success(true);
    }

    @PostMapping("/resubmit")
    @Operation(summary = "驳回后重提（POST 别名）")
    @PreAuthorize("@ss.hasPermission('finance:contract-application:resubmit')")
    public CommonResult<Boolean> resubmitPost(@RequestParam("id") Long id,
                                              @Valid @RequestBody FinanceContractApplicationResubmitReqVO reqVO) {
        contractApplicationService.resubmit(id, reqVO, getLoginUserId());
        return success(true);
    }

    @PostMapping("/cancel")
    @Operation(summary = "申请人撤回（仅用印前）")
    @Parameter(name = "id", description = "申请编号", required = true)
    @PreAuthorize("@ss.hasPermission('finance:contract-application:create')")
    public CommonResult<Boolean> cancel(@RequestParam("id") Long id) {
        contractApplicationService.cancel(id, getLoginUserId());
        return success(true);
    }

    @PostMapping("/on-approval-outcome")
    @Operation(summary = "同步审批落账（内部/联调；生产主路径走 Flowable）")
    @PreAuthorize("@ss.hasPermission('finance:contract-application:update')")
    public CommonResult<Boolean> onApprovalOutcome(
            @Valid @RequestBody FinanceContractApplicationApprovalOutcomeReqVO reqVO) {
        contractApplicationService.onApprovalOutcome(reqVO.getApplicationId(), reqVO.getOutcome());
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得合同签约申请详情")
    @PreAuthorize("@ss.hasPermission('finance:contract-application:query')")
    public CommonResult<FinanceContractApplicationRespVO> getApplication(@RequestParam("id") Long id) {
        FinanceContractApplicationDO application = contractApplicationService.getApplication(id);
        return success(BeanUtils.toBean(application, FinanceContractApplicationRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得合同签约申请分页")
    @PreAuthorize("@ss.hasPermission('finance:contract-application:query')")
    public CommonResult<PageResult<FinanceContractApplicationRespVO>> getApplicationPage(
            @Valid FinanceContractApplicationPageReqVO pageReqVO) {
        PageResult<FinanceContractApplicationDO> page = contractApplicationService.getApplicationPage(pageReqVO);
        return success(BeanUtils.toBean(page, FinanceContractApplicationRespVO.class));
    }

    @GetMapping("/list-selectable-for-bo")
    @Operation(summary = "商务单可选合同（已通过且本人申请）")
    @PreAuthorize("@ss.hasPermission('finance:contract-application:query')")
    public CommonResult<java.util.List<FinanceContractApplicationRespVO>> listSelectableForBo() {
        return success(BeanUtils.toBean(
                contractApplicationService.listSelectableForBo(getLoginUserId()),
                FinanceContractApplicationRespVO.class));
    }

    @PostMapping("/record-seal")
    @Operation(summary = "用印节点登记扫描件")
    @PreAuthorize("@ss.hasPermission('finance:contract-application:update')")
    public CommonResult<Boolean> recordSeal(@RequestParam("id") Long id,
                                            @RequestParam("sealFileUrl") String sealFileUrl,
                                            @RequestParam(value = "actualSealerUserId", required = false) Long actualSealerUserId) {
        contractApplicationService.recordSeal(id, sealFileUrl, actualSealerUserId);
        return success(true);
    }

    @PostMapping("/record-archive")
    @Operation(summary = "归档节点确认")
    @PreAuthorize("@ss.hasPermission('finance:contract-application:update')")
    public CommonResult<Boolean> recordArchive(@RequestParam("id") Long id) {
        contractApplicationService.recordArchive(id);
        return success(true);
    }

    @PostMapping("/record-mail")
    @Operation(summary = "邮寄节点登记单号")
    @PreAuthorize("@ss.hasPermission('finance:contract-application:update')")
    public CommonResult<Boolean> recordMail(@RequestParam("id") Long id,
                                            @RequestParam("mailTrackingNo") String mailTrackingNo) {
        contractApplicationService.recordMail(id, mailTrackingNo);
        return success(true);
    }
}
