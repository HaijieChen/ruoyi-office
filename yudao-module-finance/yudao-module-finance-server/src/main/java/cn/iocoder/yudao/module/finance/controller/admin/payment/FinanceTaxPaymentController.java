package cn.iocoder.yudao.module.finance.controller.admin.payment;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.module.finance.controller.admin.payment.vo.*;
import cn.iocoder.yudao.module.finance.dal.dataobject.payment.FinancePaymentApplicationDO;
import cn.iocoder.yudao.module.finance.enums.FinancePaymentApplicationKindEnum;
import cn.iocoder.yudao.module.finance.service.payment.FinancePaymentApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.PAYMENT_APPLICATION_KIND_INVALID;

@Tag(name = "管理后台 - 税金付款申请")
@RestController
@RequestMapping("/finance/tax-payment")
@Validated
public class FinanceTaxPaymentController {

    private static final String MANAGE_ALL_PERMISSION = "finance:tax-payment:update";

    @Resource
    private FinancePaymentApplicationService paymentApplicationService;
    @Resource
    private SecurityFrameworkService securityFrameworkService;

    @PostMapping("/create-and-start")
    @Operation(summary = "创建税金付款申请并启动审批")
    @PreAuthorize("@ss.hasPermission('finance:tax-payment:create')")
    public CommonResult<Long> createAndStart(@Valid @RequestBody FinanceTaxPaymentCreateAndStartReqVO reqVO) {
        return success(paymentApplicationService.createAndStartTax(reqVO, getLoginUserId()));
    }

    @GetMapping("/get")
    @Operation(summary = "税金付款详情")
    @PreAuthorize("@ss.hasPermission('finance:tax-payment:query') or @financePaymentAccess.canTaskContextOrOwnerRead(#id)")
    public CommonResult<FinancePaymentApplicationRespVO> get(@RequestParam("id") Long id) {
        boolean manageAll = securityFrameworkService.hasPermission(MANAGE_ALL_PERMISSION);
        FinancePaymentApplicationDO app = paymentApplicationService.getApplicationForRead(
                id, getLoginUserId(), manageAll);
        assertTax(app);
        return success(toResp(app));
    }

    @GetMapping("/page")
    @Operation(summary = "税金付款分页")
    @PreAuthorize("@ss.hasPermission('finance:tax-payment:query')")
    public CommonResult<PageResult<FinancePaymentApplicationRespVO>> page(
            @Valid FinancePaymentApplicationPageReqVO pageReqVO) {
        pageReqVO.setApplicationKind(FinancePaymentApplicationKindEnum.TAX.getCode());
        boolean manageAll = securityFrameworkService.hasPermission(MANAGE_ALL_PERMISSION);
        PageResult<FinancePaymentApplicationDO> page = paymentApplicationService.getApplicationPage(
                pageReqVO, getLoginUserId(), manageAll);
        return success(BeanUtils.toBean(page, FinancePaymentApplicationRespVO.class));
    }

    @PostMapping("/record-pay")
    @Operation(summary = "出纳支付登记（复用支付底座）")
    @PreAuthorize("@ss.hasPermission('finance:tax-payment:record-pay')")
    public CommonResult<Boolean> recordPay(@Valid @RequestBody FinancePaymentRecordPayReqVO reqVO) {
        FinancePaymentApplicationDO app = paymentApplicationService.getApplication(reqVO.getId());
        assertTax(app);
        paymentApplicationService.recordPay(reqVO, getLoginUserId());
        return success(true);
    }

    @PutMapping("/cancel")
    @Operation(summary = "申请人撤回")
    @PreAuthorize("@ss.hasPermission('finance:tax-payment:create')")
    public CommonResult<Boolean> cancel(@RequestParam("id") Long id) {
        FinancePaymentApplicationDO app = paymentApplicationService.getApplication(id);
        assertTax(app);
        paymentApplicationService.cancel(id, getLoginUserId());
        return success(true);
    }

    private static void assertTax(FinancePaymentApplicationDO app) {
        if (app == null || !FinancePaymentApplicationKindEnum.TAX.getCode().equals(app.getApplicationKind())) {
            throw exception(PAYMENT_APPLICATION_KIND_INVALID);
        }
    }

    private FinancePaymentApplicationRespVO toResp(FinancePaymentApplicationDO app) {
        FinancePaymentApplicationRespVO vo = BeanUtils.toBean(app, FinancePaymentApplicationRespVO.class);
        vo.setPaidLineSum(paymentApplicationService.sumPayLines(app.getId()));
        vo.setPayLines(paymentApplicationService.listPayLines(app.getId()));
        vo.setTaxLines(paymentApplicationService.listTaxLines(app.getId()));
        return vo;
    }

}
