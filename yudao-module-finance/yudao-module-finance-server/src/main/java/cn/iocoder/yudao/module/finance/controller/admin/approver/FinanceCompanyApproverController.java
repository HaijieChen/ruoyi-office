package cn.iocoder.yudao.module.finance.controller.admin.approver;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.finance.controller.admin.approver.vo.FinanceCompanyApproverRespVO;
import cn.iocoder.yudao.module.finance.controller.admin.approver.vo.FinanceCompanyApproverSaveReqVO;
import cn.iocoder.yudao.module.finance.service.approver.FinanceCompanyApproverService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 公司财务审批人")
@RestController
@RequestMapping("/finance/company-approver")
@Validated
public class FinanceCompanyApproverController {

    private final FinanceCompanyApproverService companyApproverService;

    public FinanceCompanyApproverController(FinanceCompanyApproverService companyApproverService) {
        this.companyApproverService = companyApproverService;
    }

    @GetMapping("/list")
    @Operation(summary = "公司财务审批人列表")
    @PreAuthorize("@ss.hasPermission('finance:company-approver:query')")
    public CommonResult<List<FinanceCompanyApproverRespVO>> list() {
        return success(companyApproverService.list());
    }

    @GetMapping("/get")
    @Operation(summary = "获得某公司财务审批人")
    @Parameter(name = "entityCompanyDeptId", required = true)
    @PreAuthorize("@ss.hasPermission('finance:company-approver:query')")
    public CommonResult<FinanceCompanyApproverRespVO> get(
            @RequestParam("entityCompanyDeptId") Long entityCompanyDeptId) {
        return success(companyApproverService.getByCompany(entityCompanyDeptId));
    }

    @PutMapping("/save")
    @Operation(summary = "保存某公司财务审批人")
    @PreAuthorize("@ss.hasPermission('finance:company-approver:update')")
    public CommonResult<Boolean> save(@Valid @RequestBody FinanceCompanyApproverSaveReqVO reqVO) {
        companyApproverService.save(reqVO);
        return success(true);
    }
}
