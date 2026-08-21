package cn.iocoder.yudao.module.hrm.controller.admin.payroll;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.hrm.dal.dataobject.payroll.PayrollLineDO;
import cn.iocoder.yudao.module.hrm.service.payroll.PayrollBatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 我的工资条")
@RestController
@RequestMapping("/hrm/payroll-payslip")
@Validated
public class PayrollPayslipController {

    @Resource
    private PayrollBatchService payrollBatchService;

    @GetMapping("/list")
    @Operation(summary = "我的已下发工资条")
    @PreAuthorize("@ss.hasPermission('hrm:payroll-payslip:query')")
    public CommonResult<List<PayrollLineDO>> list() {
        Long login = SecurityFrameworkUtils.getLoginUserId();
        return success(payrollBatchService.listMyPayslips(login));
    }
}
