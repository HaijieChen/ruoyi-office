package cn.iocoder.yudao.module.finance.controller.admin.feepayment;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.excel.core.util.ExcelUtils;
import cn.iocoder.yudao.module.finance.controller.admin.feepayment.vo.FinanceHandlingFeePaymentImportExcelVO;
import cn.iocoder.yudao.module.finance.controller.admin.feepayment.vo.FinanceHandlingFeePaymentImportRespVO;
import cn.iocoder.yudao.module.finance.controller.admin.feepayment.vo.FinanceHandlingFeePaymentPageReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.feepayment.vo.FinanceHandlingFeePaymentRespVO;
import cn.iocoder.yudao.module.finance.controller.admin.feepayment.vo.FinanceHandlingFeePaymentSaveReqVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.feepayment.FinanceHandlingFeePaymentDO;
import cn.iocoder.yudao.module.finance.service.feepayment.FinanceHandlingFeePaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 手续费付款")
@RestController
@RequestMapping("/finance/handling-fee-payment")
@Validated
public class FinanceHandlingFeePaymentController {

    private final FinanceHandlingFeePaymentService handlingFeePaymentService;

    public FinanceHandlingFeePaymentController(FinanceHandlingFeePaymentService handlingFeePaymentService) {
        this.handlingFeePaymentService = handlingFeePaymentService;
    }

    @PostMapping("/create")
    @Operation(summary = "创建手续费付款")
    @PreAuthorize("@ss.hasPermission('finance:handling-fee-payment:create')")
    public CommonResult<Long> create(@Valid @RequestBody FinanceHandlingFeePaymentSaveReqVO reqVO) {
        return success(handlingFeePaymentService.create(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新手续费付款")
    @PreAuthorize("@ss.hasPermission('finance:handling-fee-payment:update')")
    public CommonResult<Boolean> update(@Valid @RequestBody FinanceHandlingFeePaymentSaveReqVO reqVO) {
        handlingFeePaymentService.update(reqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除手续费付款")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('finance:handling-fee-payment:delete')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        handlingFeePaymentService.delete(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得手续费付款")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('finance:handling-fee-payment:query')")
    public CommonResult<FinanceHandlingFeePaymentRespVO> get(@RequestParam("id") Long id) {
        return success(BeanUtils.toBean(handlingFeePaymentService.get(id), FinanceHandlingFeePaymentRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "手续费付款分页")
    @PreAuthorize("@ss.hasPermission('finance:handling-fee-payment:query')")
    public CommonResult<PageResult<FinanceHandlingFeePaymentRespVO>> page(
            @Valid FinanceHandlingFeePaymentPageReqVO pageReqVO) {
        PageResult<FinanceHandlingFeePaymentDO> page = handlingFeePaymentService.getPage(pageReqVO);
        return success(BeanUtils.toBean(page, FinanceHandlingFeePaymentRespVO.class));
    }

    @GetMapping("/get-import-template")
    @Operation(summary = "获得手续费付款导入模板")
    @PreAuthorize("@ss.hasPermission('finance:handling-fee-payment:import')")
    public void importTemplate(HttpServletResponse response) throws IOException {
        ExcelUtils.write(response, "手续费付款导入模板.xls", "手续费付款",
                FinanceHandlingFeePaymentImportExcelVO.class,
                Arrays.asList(FinanceHandlingFeePaymentImportExcelVO.builder()
                        .feeDate(LocalDate.of(2026, 8, 1))
                        .amount(new BigDecimal("10.00"))
                        .currency("CNY")
                        .entityCompanyName("示例主体公司")
                        .accountNo("6222000011112222")
                        .build()));
    }

    @PostMapping("/import")
    @Operation(summary = "导入手续费付款")
    @PreAuthorize("@ss.hasPermission('finance:handling-fee-payment:import')")
    public CommonResult<FinanceHandlingFeePaymentImportRespVO> importExcel(
            @RequestParam("file") MultipartFile file) throws IOException {
        return success(handlingFeePaymentService.importExcel(
                ExcelUtils.read(file, FinanceHandlingFeePaymentImportExcelVO.class)));
    }

}
