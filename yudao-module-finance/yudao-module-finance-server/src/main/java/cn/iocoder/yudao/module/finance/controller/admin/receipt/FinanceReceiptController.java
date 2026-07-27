package cn.iocoder.yudao.module.finance.controller.admin.receipt;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.excel.core.util.ExcelUtils;
import cn.iocoder.yudao.module.finance.controller.admin.receipt.vo.*;
import cn.iocoder.yudao.module.finance.dal.dataobject.receipt.FinanceReceiptDO;
import cn.iocoder.yudao.module.finance.service.receipt.FinanceReceiptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserNickname;

@Tag(name = "管理后台 - 银行到款")
@RestController
@RequestMapping("/finance/receipt")
@Validated
public class FinanceReceiptController {

    @Resource
    private FinanceReceiptService receiptService;

    @GetMapping("/get-import-template")
    @Operation(summary = "获得银行到款导入模板")
    @PreAuthorize("@ss.hasPermission('finance:receipt:import')")
    public void importTemplate(HttpServletResponse response) throws IOException {
        List<FinanceReceiptImportExcelVO> list = Arrays.asList(
                FinanceReceiptImportExcelVO.builder()
                        .bankAccount("工行基本户")
                        .transactionDate(LocalDateTime.of(2026, 7, 27, 10, 15, 0))
                        .payerName("示例付款方A")
                        .payerAccount("6222000011112222")
                        .transactionAmount(new BigDecimal("10000.00"))
                        .summary("示例摘要/附言")
                        .bankSerialNo("BANK-SERIAL-DEMO-001")
                        .build(),
                FinanceReceiptImportExcelVO.builder()
                        .bankAccount("工行基本户")
                        .transactionDate(LocalDateTime.of(2026, 7, 27, 11, 30, 0))
                        .payerName("示例付款方B")
                        .payerAccount("6222000033334444")
                        .transactionAmount(new BigDecimal("2500.50"))
                        .summary("示例摘要/附言")
                        .bankSerialNo("BANK-SERIAL-DEMO-002")
                        .build()
        );
        ExcelUtils.write(response, "银行到款导入模板.xls", "银行到款", FinanceReceiptImportExcelVO.class, list);
    }

    @PostMapping("/import")
    @Operation(summary = "导入银行到款")
    @PreAuthorize("@ss.hasPermission('finance:receipt:import')")
    public CommonResult<FinanceReceiptImportRespVO> importReceipt(@RequestParam("file") MultipartFile file) throws IOException {
        return success(receiptService.importReceiptList(ExcelUtils.read(file, FinanceReceiptImportExcelVO.class), getLoginUserId()));
    }

    @PostMapping("/create")
    @Operation(summary = "创建银行到款")
    @PreAuthorize("@ss.hasPermission('finance:receipt:create')")
    public CommonResult<Long> createReceipt(@Valid @RequestBody FinanceReceiptSaveReqVO createReqVO) {
        return success(receiptService.createReceipt(createReqVO, getLoginUserId()));
    }

    @PutMapping("/update")
    @Operation(summary = "更新银行到款")
    @PreAuthorize("@ss.hasPermission('finance:receipt:update')")
    public CommonResult<Boolean> updateReceipt(@Valid @RequestBody FinanceReceiptSaveReqVO updateReqVO) {
        receiptService.updateReceipt(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除银行到款")
    @Parameter(name = "ids", description = "编号数组", required = true)
    @PreAuthorize("@ss.hasPermission('finance:receipt:delete')")
    public CommonResult<Boolean> deleteReceipt(@RequestParam("ids") List<Long> ids) {
        receiptService.deleteReceipt(ids);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得银行到款")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('finance:receipt:query')")
    public CommonResult<FinanceReceiptRespVO> getReceipt(@RequestParam("id") Long id) {
        return success(BeanUtils.toBean(receiptService.getReceipt(id), FinanceReceiptRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得银行到款分页")
    @PreAuthorize("@ss.hasPermission('finance:receipt:query')")
    public CommonResult<PageResult<FinanceReceiptRespVO>> getReceiptPage(@Valid FinanceReceiptPageReqVO pageReqVO) {
        PageResult<FinanceReceiptDO> pageResult = receiptService.getReceiptPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, FinanceReceiptRespVO.class));
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
        receiptService.closeReceipt(reqVO.getId(), getLoginUserId(), getLoginUserNickname(), reqVO.getReason());
        return success(true);
    }

    @PutMapping("/reopen")
    @Operation(summary = "重开已关闭的银行到款")
    @PreAuthorize("@ss.hasPermission('finance:receipt:reopen')")
    public CommonResult<Boolean> reopenReceipt(@Valid @RequestBody FinanceReceiptLifecycleReqVO reqVO) {
        receiptService.reopenReceipt(reqVO.getId(), getLoginUserId(), getLoginUserNickname(), reqVO.getReason());
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
