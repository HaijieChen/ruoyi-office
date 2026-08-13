package cn.iocoder.yudao.module.finance.controller.admin.companyaccount;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.finance.controller.admin.companyaccount.vo.FinanceCompanyBankAccountPageReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.companyaccount.vo.FinanceCompanyBankAccountRespVO;
import cn.iocoder.yudao.module.finance.controller.admin.companyaccount.vo.FinanceCompanyBankAccountSaveReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.companyaccount.vo.FinanceCompanyBankAccountUpdateStatusReqVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.companyaccount.FinanceCompanyBankAccountDO;
import cn.iocoder.yudao.module.finance.service.common.FinanceEntityCompanyResolver;
import cn.iocoder.yudao.module.finance.service.companyaccount.FinanceCompanyBankAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.module.finance.service.companyaccount.FinanceCompanyBankAccountService.maskAccountNo;

@Tag(name = "管理后台 - 公司银行账户")
@RestController
@RequestMapping("/finance/company-bank-account")
@Validated
public class FinanceCompanyBankAccountController {

    private final FinanceCompanyBankAccountService bankAccountService;
    private final FinanceEntityCompanyResolver entityCompanyResolver;

    public FinanceCompanyBankAccountController(FinanceCompanyBankAccountService bankAccountService,
                                               FinanceEntityCompanyResolver entityCompanyResolver) {
        this.bankAccountService = bankAccountService;
        this.entityCompanyResolver = entityCompanyResolver;
    }

    @PostMapping("/create")
    @Operation(summary = "创建公司银行账户")
    @PreAuthorize("@ss.hasPermission('finance:company-bank-account:create')")
    @ApiAccessLog(sanitizeKeys = {"accountNo", "account_no"})
    public CommonResult<Long> create(@Valid @RequestBody FinanceCompanyBankAccountSaveReqVO reqVO) {
        return success(bankAccountService.create(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新公司银行账户")
    @PreAuthorize("@ss.hasPermission('finance:company-bank-account:update')")
    @ApiAccessLog(sanitizeKeys = {"accountNo", "account_no"})
    public CommonResult<Boolean> update(@Valid @RequestBody FinanceCompanyBankAccountSaveReqVO reqVO) {
        bankAccountService.update(reqVO);
        return success(true);
    }

    @PutMapping("/update-status")
    @Operation(summary = "启用/停用公司银行账户")
    @PreAuthorize("@ss.hasPermission('finance:company-bank-account:update')")
    public CommonResult<Boolean> updateStatus(@Valid @RequestBody FinanceCompanyBankAccountUpdateStatusReqVO reqVO) {
        bankAccountService.updateStatus(reqVO.getId(), reqVO.getStatus());
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得公司银行账户（维护视图含完整账号）")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('finance:company-bank-account:query')")
    public CommonResult<FinanceCompanyBankAccountRespVO> get(@RequestParam("id") Long id) {
        return success(toResp(bankAccountService.get(id), false));
    }

    @GetMapping("/page")
    @Operation(summary = "公司银行账户分页")
    @PreAuthorize("@ss.hasPermission('finance:company-bank-account:query')")
    public CommonResult<PageResult<FinanceCompanyBankAccountRespVO>> page(
            @Valid FinanceCompanyBankAccountPageReqVO pageReqVO) {
        PageResult<FinanceCompanyBankAccountDO> page = bankAccountService.getPage(pageReqVO);
        List<FinanceCompanyBankAccountRespVO> list = new ArrayList<>();
        for (FinanceCompanyBankAccountDO row : page.getList()) {
            list.add(toResp(row, false));
        }
        return success(new PageResult<>(list, page.getTotal()));
    }

    @GetMapping("/simple-list")
    @Operation(summary = "指定主体公司下启用账户（出纳只读选；账号脱敏）")
    @Parameter(name = "entityCompanyDeptId", description = "主体公司组织部门编号", required = true)
    @PreAuthorize("@ss.hasPermission('finance:company-bank-account:simple-list')")
    public CommonResult<List<FinanceCompanyBankAccountRespVO>> simpleList(
            @RequestParam("entityCompanyDeptId") Long entityCompanyDeptId) {
        List<FinanceCompanyBankAccountDO> rows = bankAccountService.listEnabledByEntityCompany(entityCompanyDeptId);
        List<FinanceCompanyBankAccountRespVO> list = new ArrayList<>();
        for (FinanceCompanyBankAccountDO row : rows) {
            list.add(toResp(row, true));
        }
        return success(list);
    }

    private FinanceCompanyBankAccountRespVO toResp(FinanceCompanyBankAccountDO row, boolean maskAccount) {
        if (row == null) {
            return null;
        }
        FinanceCompanyBankAccountRespVO vo = BeanUtils.toBean(row, FinanceCompanyBankAccountRespVO.class);
        vo.setAccountNoMasked(maskAccountNo(row.getAccountNo()));
        if (maskAccount) {
            vo.setAccountNo(null);
        }
        if (row.getEntityCompanyDeptId() != null) {
            try {
                var company = entityCompanyResolver.requireByDeptId(row.getEntityCompanyDeptId());
                vo.setEntityCompanyName(company.name());
            } catch (Exception ignored) {
                // 历史/停用主体：列表仍可展示账户，名称可空
            }
        }
        return vo;
    }

}
