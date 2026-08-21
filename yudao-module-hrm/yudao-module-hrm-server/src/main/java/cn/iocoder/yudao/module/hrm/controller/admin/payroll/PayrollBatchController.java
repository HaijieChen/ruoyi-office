package cn.iocoder.yudao.module.hrm.controller.admin.payroll;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.hrm.service.payroll.PayrollBatchRules;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 月度工资核算")
@RestController
@RequestMapping("/hrm/payroll-batch")
@Validated
public class PayrollBatchController {

    @GetMapping("/status")
    @Operation(summary = "批次状态规则")
    @PreAuthorize("@ss.hasPermission('hrm:payroll-batch:query')")
    public CommonResult<Boolean> canEdit(@RequestParam(defaultValue = "DRAFT") String status) {
        return success(PayrollBatchRules.canEdit(status));
    }
}
