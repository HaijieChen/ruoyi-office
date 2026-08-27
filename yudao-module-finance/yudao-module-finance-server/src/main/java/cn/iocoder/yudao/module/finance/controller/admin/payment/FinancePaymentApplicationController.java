package cn.iocoder.yudao.module.finance.controller.admin.payment;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.module.finance.controller.admin.payment.vo.*;
import cn.iocoder.yudao.module.finance.dal.dataobject.payment.FinancePaymentApplicationDO;
import cn.iocoder.yudao.module.finance.enums.FinancePaymentApplicationStatusEnum;
import cn.iocoder.yudao.module.finance.service.payment.FinancePaymentApplicationService;
import cn.iocoder.yudao.module.finance.service.payment.FinancePaymentPredocService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - 付款申请")
@RestController
@RequestMapping("/finance/payment-application")
@Validated
public class FinancePaymentApplicationController {

    private static final String MANAGE_ALL_PERMISSION = "finance:payment-application:update";

    @Resource
    private FinancePaymentApplicationService paymentApplicationService;
    @Resource
    private FinancePaymentPredocService paymentPredocService;
    @Resource
    private SecurityFrameworkService securityFrameworkService;

    @PostMapping("/create-and-start")
    @Operation(summary = "创建付款申请并启动审批")
    @PreAuthorize("isAuthenticated()")
    public CommonResult<Long> createAndStart(@Valid @RequestBody FinancePaymentApplicationCreateAndStartReqVO reqVO) {
        return success(paymentApplicationService.createAndStart(reqVO, getLoginUserId()));
    }

    @PutMapping("/resubmit")
    @Operation(summary = "驳回后重提（整链重批）")
    @PreAuthorize("@ss.hasPermission('finance:payment-application:resubmit')")
    public CommonResult<Boolean> resubmit(@RequestParam("id") Long id,
                                          @Valid @RequestBody FinancePaymentApplicationResubmitReqVO reqVO) {
        paymentApplicationService.resubmit(id, reqVO, getLoginUserId());
        return success(true);
    }

    @PutMapping("/cancel")
    @Operation(summary = "申请人撤回付款（同步落 CANCELLED）")
    @PreAuthorize("isAuthenticated()")
    public CommonResult<Boolean> cancel(@RequestParam("id") Long id) {
        paymentApplicationService.cancel(id, getLoginUserId());
        return success(true);
    }

    @PostMapping("/replay-outcome")
    @Operation(summary = "重放终态 REJECTED/CANCELLED（须流程已结束且状态匹配，禁止 PAID）")
    // PAY-R16：仅专用权限；update 不再隐含 replay（FA 全量读仍用 update）
    @PreAuthorize("@ss.hasPermission('finance:payment-application:replay-outcome')")
    public CommonResult<Boolean> replayOutcome(@RequestParam("id") @NotNull Long id,
                                               @RequestParam("outcome") @NotEmpty String outcome,
                                               @RequestParam("processInstanceId") @NotEmpty String processInstanceId) {
        paymentApplicationService.replayTerminalOutcome(id, outcome, processInstanceId);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "付款申请详情（本人 / FA / 当前任务办理人；仅 ORDINARY）")
    @PreAuthorize("@ss.hasPermission('finance:payment-application:query') or @financePaymentAccess.canTaskContextOrOwnerRead(#id)")
    public CommonResult<FinancePaymentApplicationRespVO> get(@RequestParam("id") Long id) {
        boolean manageAll = securityFrameworkService.hasPermission(MANAGE_ALL_PERMISSION);
        FinancePaymentApplicationDO app = paymentApplicationService.getOrdinaryApplicationForRead(
                id, getLoginUserId(), manageAll);
        return success(toResp(app));
    }

    @GetMapping("/page")
    @Operation(summary = "付款申请分页（仅普通付款 ORDINARY）")
    @PreAuthorize("@ss.hasPermission('finance:payment-application:query')")
    public CommonResult<PageResult<FinancePaymentApplicationRespVO>> page(
            @Valid FinancePaymentApplicationPageReqVO pageReqVO) {
        // EXP-87 类型闭合：普通入口强制 ORDINARY，拒绝 SALARY/TAX 穿透
        if (pageReqVO.getApplicationKind() != null
                && !pageReqVO.getApplicationKind().isBlank()
                && !"ORDINARY".equals(pageReqVO.getApplicationKind())) {
            throw cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception(
                    cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.PAYMENT_APPLICATION_KIND_INVALID);
        }
        pageReqVO.setApplicationKind("ORDINARY");
        boolean manageAll = securityFrameworkService.hasPermission(MANAGE_ALL_PERMISSION);
        PageResult<FinancePaymentApplicationDO> page = paymentApplicationService.getApplicationPage(
                pageReqVO, getLoginUserId(), manageAll);
        return success(BeanUtils.toBean(page, FinancePaymentApplicationRespVO.class));
    }

    @GetMapping("/cumulative-paid")
    @Operation(summary = "同收款方已支付累计")
    @PreAuthorize("@ss.hasPermission('finance:payment-application:query')")
    public CommonResult<Map<String, BigDecimal>> cumulativePaid(@RequestParam("payeeCompanyId") Long payeeCompanyId) {
        BigDecimal paid = paymentApplicationService.sumPaidByPayee(payeeCompanyId);
        return success(Map.of("paidSum", paid));
    }

    @PostMapping("/record-pay")
    @Operation(summary = "出纳支付登记并 complete 任务（仅 ORDINARY）")
    @PreAuthorize("@ss.hasPermission('finance:payment-application:record-pay')")
    public CommonResult<Boolean> recordPay(@Valid @RequestBody FinancePaymentRecordPayReqVO reqVO) {
        // 类型闭合：禁止对薪资/税金单走普通 record-pay
        paymentApplicationService.getOrdinaryApplicationForRead(reqVO.getId(), getLoginUserId(), true);
        paymentApplicationService.recordPay(reqVO, getLoginUserId());
        return success(true);
    }

    @PostMapping("/confirm-materials")
    @Operation(summary = "出纳确认补票完成并办结出纳任务")
    @PreAuthorize("@ss.hasPermission('finance:payment-application:record-pay')")
    public CommonResult<Boolean> confirmMaterials(@RequestParam("id") Long id,
                                                  @RequestParam("taskId") String taskId) {
        paymentApplicationService.confirmMaterials(id, taskId, getLoginUserId());
        return success(true);
    }

    @PutMapping("/update-accounting-subject")
    @Operation(summary = "财务主管节点写入会计科目（F4；仅 ORDINARY）")
    @PreAuthorize("@ss.hasPermission('finance:payment-application:query') or @financePaymentAccess.canTaskContextOrOwnerRead(#id)")
    public CommonResult<Boolean> updateAccountingSubject(@RequestParam("id") @NotNull Long id,
                                                         @RequestParam("taskId") @NotEmpty String taskId,
                                                         @RequestParam("accountingSubject") @NotEmpty String accountingSubject) {
        paymentApplicationService.getOrdinaryApplicationForRead(id, getLoginUserId(), true);
        paymentApplicationService.updateAccountingSubject(id, accountingSubject, taskId, getLoginUserId());
        return success(true);
    }

    @GetMapping("/list-selectable-purchase-instances")
    @Operation(summary = "可引用采购流程实例")
    @PreAuthorize("@ss.hasPermission('finance:payment-application:query')")
    public CommonResult<List<FinancePurchaseInstanceRespVO>> listSelectablePurchaseInstances() {
        return success(paymentPredocService.listSelectablePurchaseInstances(getLoginUserId()));
    }

    private FinancePaymentApplicationRespVO toResp(FinancePaymentApplicationDO app) {
        FinancePaymentApplicationRespVO vo = BeanUtils.toBean(app, FinancePaymentApplicationRespVO.class);
        if (app.getPayeeCompanyId() != null) {
            BigDecimal paid = paymentApplicationService.sumPaidByPayee(app.getPayeeCompanyId());
            vo.setCumulativePaid(paid);
            // PAID 时 paid 已含本单；PENDING/WAIT_PAY 再加本次
            if (app.getApplyAmount() != null) {
                if (FinancePaymentApplicationStatusEnum.PAID.getStatus().equals(app.getStatus())) {
                    vo.setCumulativeAfter(paid);
                } else {
                    vo.setCumulativeAfter(paid.add(app.getApplyAmount()));
                }
            }
        }
        vo.setPaidLineSum(paymentApplicationService.sumPayLines(app.getId()));
        vo.setPayLines(paymentApplicationService.listPayLines(app.getId()));
        if ("SALARY".equals(app.getApplicationKind())) {
            vo.setSalaryLines(paymentApplicationService.listSalaryLines(app.getId()));
        }
        if ("TAX".equals(app.getApplicationKind())) {
            vo.setTaxLines(paymentApplicationService.listTaxLines(app.getId()));
        }
        return vo;
    }

}
