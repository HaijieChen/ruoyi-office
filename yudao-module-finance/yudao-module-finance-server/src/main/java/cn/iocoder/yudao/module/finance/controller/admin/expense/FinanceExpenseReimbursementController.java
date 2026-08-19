package cn.iocoder.yudao.module.finance.controller.admin.expense;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.module.finance.controller.admin.expense.vo.FinanceExpenseReimbursementCreateReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.expense.vo.FinanceExpenseReimbursementPageReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.expense.vo.FinanceExpenseReimbursementRespVO;
import cn.iocoder.yudao.module.finance.service.expense.FinanceExpenseReimbursementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    @PostMapping("/create")
    @Operation(summary = "创建并发起费用报销")
    public CommonResult<Long> create(@Valid @RequestBody FinanceExpenseReimbursementCreateReqVO reqVO) {
        return success(expenseReimbursementService.create(reqVO, getLoginUserId()));
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
}
