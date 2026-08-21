package cn.iocoder.yudao.module.hrm.controller.admin.payroll;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.hrm.controller.admin.payroll.vo.MinWageCreateReqVO;
import cn.iocoder.yudao.module.hrm.dal.dataobject.payroll.MinWageDO;
import cn.iocoder.yudao.module.hrm.service.payroll.MinWageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 最低工资")
@RestController
@RequestMapping("/hrm/min-wage")
@Validated
public class MinWageController {

    @Resource
    private MinWageService minWageService;

    @GetMapping("/history")
    @Operation(summary = "最低工资历史")
    @PreAuthorize("@ss.hasPermission('hrm:min-wage:query')")
    public CommonResult<List<MinWageDO>> history() {
        return success(minWageService.history());
    }

    @PostMapping("/create")
    @Operation(summary = "新增最低工资")
    @PreAuthorize("@ss.hasPermission('hrm:min-wage:update')")
    public CommonResult<Boolean> create(@Valid @RequestBody MinWageCreateReqVO reqVO) {
        LocalDate today = LocalDate.now();
        int current = today.getYear() * 100 + today.getMonthValue();
        minWageService.create(reqVO.getAmount(), Boolean.TRUE.equals(reqVO.getNextMonth()), current);
        return success(true);
    }
}
