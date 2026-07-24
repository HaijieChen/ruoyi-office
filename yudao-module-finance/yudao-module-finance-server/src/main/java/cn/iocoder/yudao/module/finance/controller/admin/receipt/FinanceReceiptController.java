package cn.iocoder.yudao.module.finance.controller.admin.receipt;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.excel.core.util.ExcelUtils;
import cn.iocoder.yudao.module.finance.controller.admin.receipt.vo.*;
import cn.iocoder.yudao.module.finance.dal.dataobject.receipt.FinanceReceiptDO;
import cn.iocoder.yudao.module.finance.service.receipt.FinanceReceiptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - 银行到款")
@RestController
@RequestMapping("/finance/receipt")
@Validated
public class FinanceReceiptController {

    @Resource
    private FinanceReceiptService receiptService;

    @PostMapping("/import")
    @Operation(summary = "导入银行到款")
    @PreAuthorize("@ss.hasPermission('finance:receipt:import')")
    public CommonResult<FinanceReceiptImportRespVO> importReceipt(@RequestParam("file") MultipartFile file) throws IOException {
        return success(receiptService.importReceiptList(ExcelUtils.read(file, FinanceReceiptImportExcelVO.class), getLoginUserId()));
    }

    @GetMapping("/unclaimed-page")
    @Operation(summary = "获得待认领银行到款分页")
    @PreAuthorize("@ss.hasPermission('finance:receipt:query')")
    public CommonResult<PageResult<FinanceReceiptRespVO>> getUnclaimedReceiptPage(@Valid FinanceReceiptPageReqVO pageReqVO) {
        PageResult<FinanceReceiptDO> pageResult = receiptService.getUnclaimedReceiptPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, FinanceReceiptRespVO.class));
    }

    @PutMapping("/close")
    @Operation(summary = "关闭仍有未认领金额的银行到款")
    @PreAuthorize("@ss.hasPermission('finance:receipt:close')")
    public CommonResult<Boolean> closeReceipt(@Valid @RequestBody FinanceReceiptLifecycleReqVO reqVO) {
        receiptService.closeReceipt(reqVO.getId(), getLoginUserId(), reqVO.getReason());
        return success(true);
    }

    @PutMapping("/reopen")
    @Operation(summary = "重开已关闭的银行到款")
    @PreAuthorize("@ss.hasPermission('finance:receipt:reopen')")
    public CommonResult<Boolean> reopenReceipt(@Valid @RequestBody FinanceReceiptLifecycleReqVO reqVO) {
        receiptService.reopenReceipt(reqVO.getId(), getLoginUserId(), reqVO.getReason());
        return success(true);
    }

    @GetMapping("/lifecycle-audit-list")
    @Operation(summary = "获得银行到款关闭和重开审计列表")
    @PreAuthorize("@ss.hasPermission('finance:receipt:audit-query')")
    public CommonResult<List<FinanceReceiptLifecycleAuditRespVO>> getLifecycleAuditList(
            @RequestParam("receiptId") Long receiptId) {
        return success(BeanUtils.toBean(receiptService.getLifecycleAuditList(receiptId),
                FinanceReceiptLifecycleAuditRespVO.class));
    }

}
