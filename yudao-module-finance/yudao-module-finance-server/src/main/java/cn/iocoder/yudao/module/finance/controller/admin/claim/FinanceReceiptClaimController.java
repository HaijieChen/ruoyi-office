package cn.iocoder.yudao.module.finance.controller.admin.claim;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.finance.controller.admin.business.vo.FinanceBusinessOrderPageReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.claim.vo.*;
import cn.iocoder.yudao.module.finance.controller.admin.invoice.vo.FinanceInvoiceApplicationPageReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.invoice.vo.FinanceInvoiceApplicationRespVO;
import cn.iocoder.yudao.module.finance.controller.admin.receipt.vo.FinanceReceiptPageReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.receipt.vo.FinanceReceiptRespVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.business.FinanceBusinessOrderDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.claim.FinanceReceiptClaimDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.claim.FinanceReceiptClaimRevokeAuditDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.invoice.FinanceInvoiceApplicationDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.receipt.FinanceReceiptDO;
import cn.iocoder.yudao.module.finance.service.business.FinanceBusinessOrderService;
import cn.iocoder.yudao.module.finance.service.claim.FinanceReceiptClaimDetail;
import cn.iocoder.yudao.module.finance.service.claim.FinanceReceiptClaimService;
import cn.iocoder.yudao.module.finance.service.invoice.FinanceInvoiceApplicationService;
import cn.iocoder.yudao.module.finance.service.receipt.FinanceReceiptService;
import cn.iocoder.yudao.module.finance.enums.FinanceInvoiceApprovalStatusEnum;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserNickname;

@Tag(name = "管理后台 - 到款认领")
@RestController
@RequestMapping("/finance/receipt-claim")
@Validated
public class FinanceReceiptClaimController {

    @Resource
    private FinanceReceiptClaimService claimService;
    @Resource
    private FinanceReceiptService receiptService;
    @Resource
    private FinanceBusinessOrderService businessOrderService;
    @Resource
    private FinanceInvoiceApplicationService invoiceApplicationService;

    @PostMapping("/create")
    @Operation(summary = "创建到款认领单")
    @PreAuthorize("@ss.hasPermission('finance:receipt-claim:create')")
    public CommonResult<Long> createClaim(@Valid @RequestBody FinanceReceiptClaimSaveReqVO reqVO) {
        return success(claimService.createClaim(reqVO, getLoginUserId()));
    }

    @PutMapping("/update")
    @Operation(summary = "修改本人待确认或已驳回的到款认领单")
    @PreAuthorize("@ss.hasPermission('finance:receipt-claim:update')")
    public CommonResult<Boolean> updateClaim(@Valid @RequestBody FinanceReceiptClaimSaveReqVO reqVO) {
        claimService.updateClaim(reqVO, getLoginUserId());
        return success(true);
    }

    @GetMapping("/my-page")
    @Operation(summary = "获得我的到款认领分页")
    @PreAuthorize("@ss.hasPermission('finance:receipt-claim:query')")
    public CommonResult<PageResult<FinanceReceiptClaimRespVO>> getMyClaimPage(
            @Valid FinanceReceiptClaimPageReqVO pageReqVO) {
        PageResult<FinanceReceiptClaimDO> page = claimService.getMyClaimPage(pageReqVO, getLoginUserId());
        return success(BeanUtils.toBean(page, FinanceReceiptClaimRespVO.class));
    }

    @GetMapping("/review-page")
    @Operation(summary = "获得财务复核到款认领分页")
    @PreAuthorize("@ss.hasPermission('finance:receipt-claim:review')")
    public CommonResult<PageResult<FinanceReceiptClaimRespVO>> getReviewClaimPage(
            @Valid FinanceReceiptClaimPageReqVO pageReqVO) {
        return success(BeanUtils.toBean(claimService.getReviewClaimPage(pageReqVO),
                FinanceReceiptClaimRespVO.class));
    }

    @GetMapping("/get")
    @Operation(summary = "获得本人到款认领详情")
    @Parameter(name = "id", description = "认领单编号", required = true)
    @PreAuthorize("@ss.hasPermission('finance:receipt-claim:query')")
    public CommonResult<FinanceReceiptClaimRespVO> getMyClaim(@RequestParam("id") Long id) {
        return success(buildClaimDetail(claimService.getClaimDetail(id, getLoginUserId(), false)));
    }

    @GetMapping("/review-get")
    @Operation(summary = "获得财务复核到款认领详情")
    @Parameter(name = "id", description = "认领单编号", required = true)
    @PreAuthorize("@ss.hasPermission('finance:receipt-claim:review')")
    public CommonResult<FinanceReceiptClaimRespVO> getReviewClaim(@RequestParam("id") Long id) {
        return success(buildClaimDetail(claimService.getClaimDetail(id, getLoginUserId(), true)));
    }

    @PutMapping("/confirm")
    @Operation(summary = "确认到款认领单")
    @PreAuthorize("@ss.hasPermission('finance:receipt-claim:confirm')")
    public CommonResult<Boolean> confirmClaim(@RequestParam("id") Long id) {
        claimService.confirmClaim(id, getLoginUserId());
        return success(true);
    }

    @PutMapping("/reject")
    @Operation(summary = "驳回到款认领单")
    @PreAuthorize("@ss.hasPermission('finance:receipt-claim:reject')")
    public CommonResult<Boolean> rejectClaim(@Valid @RequestBody FinanceReceiptClaimRejectReqVO reqVO) {
        claimService.rejectClaim(reqVO.getId(), getLoginUserId(), reqVO.getReason());
        return success(true);
    }

    @PutMapping("/revoke")
    @Operation(summary = "撤销已确认的到款认领单")
    @PreAuthorize("@ss.hasPermission('finance:receipt-claim:revoke')")
    public CommonResult<Boolean> revokeClaim(@Valid @RequestBody FinanceReceiptClaimRevokeReqVO reqVO) {
        claimService.revokeClaim(reqVO.getId(), getLoginUserId(), getLoginUserNickname(), reqVO.getReason());
        return success(true);
    }

    @PutMapping("/resubmit")
    @Operation(summary = "重新提交本人已驳回的到款认领单")
    @PreAuthorize("@ss.hasPermission('finance:receipt-claim:resubmit')")
    public CommonResult<Boolean> resubmitClaim(@RequestParam("id") Long id) {
        claimService.resubmitClaim(id, getLoginUserId());
        return success(true);
    }

    @GetMapping("/revoke-audit-list")
    @Operation(summary = "获得到款认领撤销审计列表")
    @Parameter(name = "claimId", description = "认领单编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('finance:receipt-claim:review')")
    public CommonResult<List<FinanceReceiptClaimRevokeAuditRespVO>> getRevokeAuditList(
            @RequestParam("claimId") Long claimId) {
        List<FinanceReceiptClaimRevokeAuditDO> audits = claimService.getRevokeAuditList(claimId);
        return success(BeanUtils.toBean(audits, FinanceReceiptClaimRevokeAuditRespVO.class, this::fillRevokeAuditDisplay));
    }

    private void fillRevokeAuditDisplay(FinanceReceiptClaimRevokeAuditRespVO respVO) {
        // 兼容前端 AuditLog：operatorId/operatorName/reason/createTime/action
        respVO.setOperatorId(respVO.getReviewerId());
        respVO.setOperatorName(respVO.getReviewerName());
        respVO.setReason(respVO.getRevokeReason());
        respVO.setCreateTime(respVO.getRevokeTime());
        respVO.setAction("撤销");
    }

    @GetMapping("/source-receipt-page")
    @Operation(summary = "获得可认领银行到款分页（仅业务款）")
    @PreAuthorize("@ss.hasPermission('finance:receipt-claim:query')")
    public CommonResult<PageResult<FinanceReceiptRespVO>> getSourceReceiptPage(
            @Valid FinanceReceiptPageReqVO pageReqVO) {
        // 产品：认领单只可选业务款；强制过滤，忽略调用方传入的 businessFund
        pageReqVO.setBusinessFund(Boolean.TRUE);
        PageResult<FinanceReceiptDO> page = receiptService.getUnclaimedReceiptPage(pageReqVO);
        // 保留 claimable=0 行并返回真实 total；FE 对 0 禁用（编辑可并入已选源）
        List<FinanceReceiptRespVO> list = new ArrayList<>();
        for (FinanceReceiptDO receipt : page.getList()) {
            FinanceReceiptRespVO vo = BeanUtils.toBean(receipt, FinanceReceiptRespVO.class);
            vo.setClaimableAmount(calcReceiptClaimable(receipt));
            list.add(vo);
        }
        return success(new PageResult<>(list, page.getTotal()));
    }

    @GetMapping("/source-business-order-page")
    @Operation(summary = "【历史】本人可认领商务单分页（只读；新写禁止 LEGACY）")
    @PreAuthorize("@ss.hasPermission('finance:receipt-claim:query')")
    public CommonResult<PageResult<FinanceReceiptClaimBusinessOrderSourceRespVO>> getSourceBusinessOrderPage(
            @Valid FinanceBusinessOrderPageReqVO pageReqVO) {
        PageResult<FinanceBusinessOrderDO> page = businessOrderService
                .getClaimableBusinessOrderPage(pageReqVO, getLoginUserId());
        return success(BeanUtils.toBean(page, FinanceReceiptClaimBusinessOrderSourceRespVO.class));
    }

    @GetMapping("/source-invoice-application-page")
    @Operation(summary = "获得可认领开票申请分页（批过未出票也可选；服务端 claimAllowed）")
    @PreAuthorize("@ss.hasPermission('finance:receipt-claim:query')")
    public CommonResult<PageResult<FinanceInvoiceApplicationRespVO>> getSourceInvoiceApplicationPage(
            @Valid FinanceInvoiceApplicationPageReqVO pageReqVO) {
        // 列表层过滤 APPROVED；对象权限与金额在 create 再强校验
        if (pageReqVO.getApprovalStatus() == null) {
            pageReqVO.setApprovalStatus(FinanceInvoiceApprovalStatusEnum.APPROVED.getStatus());
        }
        PageResult<FinanceInvoiceApplicationDO> page =
                invoiceApplicationService.getClaimableSourcePage(pageReqVO, getLoginUserId());
        // 保留 claimable=0；total 用分页真实命中数
        List<FinanceInvoiceApplicationRespVO> list = new ArrayList<>();
        for (FinanceInvoiceApplicationDO app : page.getList()) {
            FinanceInvoiceApplicationRespVO vo = BeanUtils.toBean(app, FinanceInvoiceApplicationRespVO.class);
            vo.setClaimableAmount(calcInvoiceClaimable(app));
            list.add(vo);
        }
        return success(new PageResult<>(list, page.getTotal()));
    }

    private static BigDecimal calcReceiptClaimable(FinanceReceiptDO receipt) {
        BigDecimal unclaimed = defaultZero(receipt.getUnclaimedAmount());
        BigDecimal pending = defaultZero(receipt.getPendingClaimedAmount());
        BigDecimal claimable = unclaimed.subtract(pending);
        return claimable.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : claimable;
    }

    private static BigDecimal calcInvoiceClaimable(FinanceInvoiceApplicationDO app) {
        BigDecimal total = defaultZero(app.getTotalAmount());
        BigDecimal confirmed = defaultZero(app.getConfirmedClaimedAmount());
        BigDecimal pending = defaultZero(app.getPendingClaimedAmount());
        BigDecimal claimable = total.subtract(confirmed).subtract(pending);
        return claimable.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : claimable;
    }

    private static BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static FinanceReceiptClaimRespVO buildClaimDetail(FinanceReceiptClaimDetail detail) {
        FinanceReceiptClaimRespVO response = BeanUtils.toBean(detail.claim(), FinanceReceiptClaimRespVO.class);
        List<FinanceReceiptClaimRespVO.Item> items = detail.items().stream().map(item -> {
            FinanceReceiptClaimRespVO.Item itemResponse = BeanUtils.toBean(item, FinanceReceiptClaimRespVO.Item.class);
            FinanceReceiptDO receipt = detail.receipts().get(item.getReceiptId());
            if (receipt != null) {
                itemResponse.setReceiptNo(receipt.getReceiptNo());
                itemResponse.setPayerName(receipt.getPayerName());
                itemResponse.setBankSerialNo(receipt.getBankSerialNo());
            }
            if (item.getInvoiceApplicationId() != null && detail.invoiceApplications() != null) {
                FinanceInvoiceApplicationDO app = detail.invoiceApplications().get(item.getInvoiceApplicationId());
                if (app != null) {
                    itemResponse.setInvoiceApplicationNo(app.getApplicationNo());
                    itemResponse.setBuyerName(app.getBuyerName());
                    itemResponse.setCustomerCompanyId(app.getCustomerCompanyId());
                }
            }
            if (item.getBusinessOrderId() != null) {
                FinanceBusinessOrderDO order = detail.businessOrders().get(item.getBusinessOrderId());
                if (order != null) {
                    itemResponse.setBusinessOrderNo(order.getOrderNo());
                    itemResponse.setProductName(order.getProductName());
                }
            }
            return itemResponse;
        }).toList();
        response.setItems(items);
        return response;
    }

}
