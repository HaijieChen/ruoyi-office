package cn.iocoder.yudao.module.hrm.controller.admin.payroll;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.hrm.service.payroll.PayslipAccess;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 我的工资条")
@RestController
@RequestMapping("/hrm/payroll-payslip")
@Validated
public class PayrollPayslipController {

    @GetMapping("/can-read")
    @Operation(summary = "是否可看指定用户快照（始终按登录用户，忽略传入放大）")
    @PreAuthorize("@ss.hasPermission('hrm:payroll-payslip:query')")
    public CommonResult<Boolean> canRead(@RequestParam(required = false) Long userId) {
        Long login = SecurityFrameworkUtils.getLoginUserId();
        return success(PayslipAccess.canReadOwn(login, login) && PayslipAccess.canReadOwn(userId == null ? login : login, login));
    }
}
