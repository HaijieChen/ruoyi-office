package cn.iocoder.yudao.module.finance.controller.admin.contract;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.module.finance.controller.admin.contract.vo.*;
import cn.iocoder.yudao.module.finance.dal.dataobject.contract.FinanceContractApplicationDO;
import cn.iocoder.yudao.module.finance.service.contract.FinanceContractApplicationService;
import cn.iocoder.yudao.module.finance.service.payment.FinancePaymentPredocService;
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

    /** FA 持有 update 权限时可查全量；BS 仅本人（CS-F3）。 */
    private static final String MANAGE_ALL_PERMISSION = "finance:contract-application:update";

    @Resource
    private FinanceContractApplicationService contractApplicationService;
    @Resource
    private FinancePaymentPredocService paymentPredocService;
    @Resource
    private SecurityFrameworkService securityFrameworkService;

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
    @Operation(summary = "申请人撤回（仅用印前；同步取消 Flowable）")
    @Parameter(name = "id", description = "申请编号", required = true)
    @PreAuthorize("@ss.hasPermission('finance:contract-application:create')")
    public CommonResult<Boolean> cancel(@RequestParam("id") Long id) {
        contractApplicationService.cancel(id, getLoginUserId());
        return success(true);
    }

    // CS-F1：已移除用户可调用的 on-approval-outcome HTTP 旁路；终态仅由 BPM 回调写入。

    @GetMapping("/get")
    @Operation(summary = "获得合同签约申请详情")
    // C30 / CS-R5：静态 query 或 本人/任务语境（转办后无 query 的办理人）
    @PreAuthorize("@ss.hasPermission('finance:contract-application:query') or @financeContractAccess.canTaskContextOrOwnerRead(#id)")
    public CommonResult<FinanceContractApplicationRespVO> getApplication(@RequestParam("id") Long id) {
        FinanceContractApplicationDO application = contractApplicationService.getApplication(
                id, getLoginUserId(), manageAll());
        return success(BeanUtils.toBean(application, FinanceContractApplicationRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得合同签约申请分页")
    @PreAuthorize("@ss.hasPermission('finance:contract-application:query')")
    public CommonResult<PageResult<FinanceContractApplicationRespVO>> getApplicationPage(
            @Valid FinanceContractApplicationPageReqVO pageReqVO) {
        PageResult<FinanceContractApplicationDO> page = contractApplicationService.getApplicationPage(
                pageReqVO, getLoginUserId(), manageAll());
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

    @GetMapping("/list-selectable-for-lease-payment")
    @Operation(summary = "付款可选租赁合同（已通过 · fileType=租赁合同 · 本人申请）")
    @PreAuthorize("@ss.hasPermission('finance:contract-application:query')")
    public CommonResult<java.util.List<FinanceContractApplicationRespVO>> listSelectableForLeasePayment() {
        return success(BeanUtils.toBean(
                paymentPredocService.listSelectableLeaseContracts(getLoginUserId()),
                FinanceContractApplicationRespVO.class));
    }

    @PostMapping("/record-seal")
    @Operation(summary = "用印节点登记扫描件并 complete 任务")
    @PreAuthorize("@ss.hasPermission('finance:contract-application:record-seal')")
    public CommonResult<Boolean> recordSeal(@RequestParam("id") Long id,
                                            @RequestParam("taskId") String taskId,
                                            @RequestParam("sealFileUrl") String sealFileUrl) {
        contractApplicationService.recordSeal(id, taskId, sealFileUrl, getLoginUserId());
        return success(true);
    }

    @PostMapping("/record-archive")
    @Operation(summary = "归档节点确认并 complete 任务")
    @PreAuthorize("@ss.hasPermission('finance:contract-application:record-seal')")
    public CommonResult<Boolean> recordArchive(@RequestParam("id") Long id,
                                               @RequestParam("taskId") String taskId) {
        contractApplicationService.recordArchive(id, taskId, getLoginUserId());
        return success(true);
    }

    @PostMapping("/record-mail")
    @Operation(summary = "邮寄节点登记单号并 complete 任务")
    @PreAuthorize("@ss.hasPermission('finance:contract-application:record-mail')")
    public CommonResult<Boolean> recordMail(@RequestParam("id") Long id,
                                            @RequestParam("taskId") String taskId,
                                            @RequestParam("mailTrackingNo") String mailTrackingNo) {
        contractApplicationService.recordMail(id, taskId, mailTrackingNo, getLoginUserId());
        return success(true);
    }

    private boolean manageAll() {
        return securityFrameworkService.hasPermission(MANAGE_ALL_PERMISSION);
    }
}
