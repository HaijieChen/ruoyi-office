package cn.iocoder.yudao.module.finance.controller.admin.expense;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.module.finance.controller.admin.expense.vo.FinanceExpenseApproveReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.expense.vo.FinanceExpenseRecordPayReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.expense.vo.FinanceExpenseReimbursementCreateReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.expense.vo.FinanceExpenseReimbursementPageReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.expense.vo.FinanceExpenseReimbursementRespVO;
import cn.iocoder.yudao.module.finance.framework.ocr.FinanceInvoiceOcrClient;
import cn.iocoder.yudao.module.finance.service.expense.FinanceExpenseReimbursementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.module.finance.service.expense.FinanceExpenseReimbursementService.QUERY_PERMISSION;

@Tag(name = "管理后台 - 费用报销")
@RestController
@RequestMapping("/finance/expense-reimbursement")
@Validated
public class FinanceExpenseReimbursementController {

    @Resource
    private FinanceExpenseReimbursementService expenseReimbursementService;
    @Resource
    private SecurityFrameworkService securityFrameworkService;
    @Resource
    private FinanceInvoiceOcrClient invoiceOcrClient;

    @PostMapping("/create")
    @Operation(summary = "创建并发起费用报销")
    public CommonResult<Long> create(@Valid @RequestBody FinanceExpenseReimbursementCreateReqVO reqVO) {
        return success(expenseReimbursementService.create(reqVO, getLoginUserId()));
    }

    @PostMapping("/create-no-invoice")
    @Operation(summary = "创建并发起无票费用报销")
    public CommonResult<Long> createNoInvoice(@Valid @RequestBody FinanceExpenseReimbursementCreateReqVO reqVO) {
        return success(expenseReimbursementService.createNoInvoice(reqVO, getLoginUserId()));
    }

    @PostMapping("/ocr-invoice")
    @Operation(summary = "识别发票日期、金额、专票税额与票种（失败返回空字段）")
    public CommonResult<FinanceInvoiceOcrClient.Result> ocrInvoice(
            @RequestParam(value = "fileUrl", required = false) String fileUrl,
            @RequestParam(value = "file", required = false) MultipartFile file) throws Exception {
        FinanceInvoiceOcrClient.Result result = file != null && !file.isEmpty()
                ? invoiceOcrClient.recognizeBytes(file.getBytes())
                : invoiceOcrClient.recognize(fileUrl);
        if (result != null && result.invoiceNo() != null
                && expenseReimbursementService.invoiceNoUsed(result.invoiceNo())) {
            result = result.withUsed(true);
        }
        return success(result);
    }

    @GetMapping("/occupied-predoc-ids")
    @Operation(summary = "当前占用中的出差/外出单流程实例 ID")
    public CommonResult<List<String>> occupiedPredocIds() {
        return success(expenseReimbursementService.listOccupiedPredocProcessInstanceIds());
    }

    @GetMapping("/get")
    @Operation(summary = "获得费用报销")
    public CommonResult<FinanceExpenseReimbursementRespVO> get(@RequestParam("id") Long id) {
        boolean all = securityFrameworkService.hasPermission(QUERY_PERMISSION);
        return success(expenseReimbursementService.get(id, getLoginUserId(), all));
    }

    @GetMapping("/page")
    @Operation(summary = "费用报销分页")
    public CommonResult<PageResult<FinanceExpenseReimbursementRespVO>> page(
            @Valid FinanceExpenseReimbursementPageReqVO reqVO) {
        boolean all = securityFrameworkService.hasPermission(QUERY_PERMISSION);
        return success(expenseReimbursementService.getPage(reqVO, getLoginUserId(), all));
    }

    @PutMapping("/approve")
    @Operation(summary = "财务填写实报金额")
    public CommonResult<Boolean> approve(@Valid @RequestBody FinanceExpenseApproveReqVO reqVO) {
        expenseReimbursementService.approve(reqVO, getLoginUserId());
        return success(true);
    }

    @PutMapping("/record-pay")
    @Operation(summary = "出纳登记支付")
    @PreAuthorize("@ss.hasPermission('finance:expense:record-pay')")
    public CommonResult<Boolean> recordPay(@Valid @RequestBody FinanceExpenseRecordPayReqVO reqVO) {
        expenseReimbursementService.recordPay(reqVO, getLoginUserId());
        return success(true);
    }
}
