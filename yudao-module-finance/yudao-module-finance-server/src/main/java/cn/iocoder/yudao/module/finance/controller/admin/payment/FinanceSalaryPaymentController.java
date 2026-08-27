package cn.iocoder.yudao.module.finance.controller.admin.payment;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.module.finance.controller.admin.payment.vo.*;
import cn.iocoder.yudao.module.finance.dal.dataobject.payment.FinancePaymentApplicationDO;
import cn.iocoder.yudao.module.finance.enums.FinancePaymentApplicationKindEnum;
import cn.iocoder.yudao.module.finance.enums.FinancePaymentApplicationStatusEnum;
import cn.iocoder.yudao.module.finance.service.payment.FinancePaymentApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.PAYMENT_APPLICATION_KIND_INVALID;

@Tag(name = "管理后台 - 薪资付款申请")
@RestController
@RequestMapping("/finance/salary-payment")
@Validated
public class FinanceSalaryPaymentController {

    private static final String MANAGE_ALL_PERMISSION = "finance:salary-payment:update";

    @Resource
    private FinancePaymentApplicationService paymentApplicationService;
    @Resource
    private SecurityFrameworkService securityFrameworkService;

    @PostMapping("/create-and-start")
    @Operation(summary = "创建薪资付款申请并启动审批")
    @PreAuthorize("isAuthenticated()")
    public CommonResult<Long> createAndStart(@Valid @RequestBody FinanceSalaryPaymentCreateAndStartReqVO reqVO) {
        return success(paymentApplicationService.createAndStartSalary(reqVO, getLoginUserId()));
    }

    @PutMapping("/resubmit")
    @Operation(summary = "驳回后重提薪资付款（明细重写 + 新流程）")
    @PreAuthorize("@ss.hasPermission('finance:salary-payment:resubmit')")
    public CommonResult<Boolean> resubmit(@RequestParam("id") Long id,
                                          @Valid @RequestBody FinanceSalaryPaymentCreateAndStartReqVO reqVO) {
        paymentApplicationService.resubmitSalary(id, reqVO, getLoginUserId());
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "薪资付款详情")
    @PreAuthorize("@ss.hasPermission('finance:salary-payment:query') or @financePaymentAccess.canTaskContextOrOwnerRead(#id)")
    public CommonResult<FinancePaymentApplicationRespVO> get(@RequestParam("id") Long id) {
        boolean manageAll = securityFrameworkService.hasPermission(MANAGE_ALL_PERMISSION);
        FinancePaymentApplicationDO app = paymentApplicationService.getApplicationForRead(
                id, getLoginUserId(), manageAll);
        assertSalary(app);
        return success(toResp(app));
    }

    @GetMapping("/page")
    @Operation(summary = "薪资付款分页")
    @PreAuthorize("@ss.hasPermission('finance:salary-payment:query')")
    public CommonResult<PageResult<FinancePaymentApplicationRespVO>> page(
            @Valid FinancePaymentApplicationPageReqVO pageReqVO) {
        pageReqVO.setApplicationKind(FinancePaymentApplicationKindEnum.SALARY.getCode());
        boolean manageAll = securityFrameworkService.hasPermission(MANAGE_ALL_PERMISSION);
        PageResult<FinancePaymentApplicationDO> page = paymentApplicationService.getApplicationPage(
                pageReqVO, getLoginUserId(), manageAll);
        return success(BeanUtils.toBean(page, FinancePaymentApplicationRespVO.class));
    }

    @PostMapping("/record-pay")
    @Operation(summary = "出纳支付登记（复用支付底座）")
    @PreAuthorize("@ss.hasPermission('finance:salary-payment:record-pay')")
    public CommonResult<Boolean> recordPay(@Valid @RequestBody FinancePaymentRecordPayReqVO reqVO) {
        FinancePaymentApplicationDO app = paymentApplicationService.getApplication(reqVO.getId());
        assertSalary(app);
        paymentApplicationService.recordPay(reqVO, getLoginUserId());
        return success(true);
    }

    @PutMapping("/update-accounting-subject")
    @Operation(summary = "财务主管节点写入会计科目（薪资）")
    @PreAuthorize("@ss.hasPermission('finance:salary-payment:query') or @financePaymentAccess.canTaskContextOrOwnerRead(#id)")
    public CommonResult<Boolean> updateAccountingSubject(@RequestParam("id") @NotNull Long id,
                                                         @RequestParam("taskId") @NotEmpty String taskId,
                                                         @RequestParam("accountingSubject") @NotEmpty String accountingSubject) {
        FinancePaymentApplicationDO app = paymentApplicationService.getApplication(id);
        assertSalary(app);
        paymentApplicationService.updateAccountingSubject(id, accountingSubject, taskId, getLoginUserId());
        return success(true);
    }

    @PutMapping("/cancel")
    @Operation(summary = "申请人撤回")
    @PreAuthorize("isAuthenticated()")
    public CommonResult<Boolean> cancel(@RequestParam("id") Long id) {
        FinancePaymentApplicationDO app = paymentApplicationService.getApplication(id);
        assertSalary(app);
        paymentApplicationService.cancel(id, getLoginUserId());
        return success(true);
    }

    private static void assertSalary(FinancePaymentApplicationDO app) {
        if (app == null || !FinancePaymentApplicationKindEnum.SALARY.getCode().equals(app.getApplicationKind())) {
            throw exception(PAYMENT_APPLICATION_KIND_INVALID);
        }
    }

    private FinancePaymentApplicationRespVO toResp(FinancePaymentApplicationDO app) {
        FinancePaymentApplicationRespVO vo = BeanUtils.toBean(app, FinancePaymentApplicationRespVO.class);
        vo.setPaidLineSum(paymentApplicationService.sumPayLines(app.getId()));
        vo.setPayLines(paymentApplicationService.listPayLines(app.getId()));
        vo.setSalaryLines(paymentApplicationService.listSalaryLines(app.getId()));
        if (app.getApplyAmount() != null
                && !FinancePaymentApplicationStatusEnum.PAID.getStatus().equals(app.getStatus())) {
            // no-op cumulative for salary
        }
        return vo;
    }

}
