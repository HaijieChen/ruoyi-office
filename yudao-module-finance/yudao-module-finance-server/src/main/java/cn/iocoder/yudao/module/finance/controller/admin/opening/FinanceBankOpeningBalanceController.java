package cn.iocoder.yudao.module.finance.controller.admin.opening;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.finance.controller.admin.opening.vo.FinanceBankOpeningBalancePageReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.opening.vo.FinanceBankOpeningBalanceRespVO;
import cn.iocoder.yudao.module.finance.controller.admin.opening.vo.FinanceBankOpeningBalanceSaveReqVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.opening.FinanceBankOpeningBalanceDO;
import cn.iocoder.yudao.module.finance.service.opening.FinanceBankOpeningBalanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 银行期初余额")
@RestController
@RequestMapping("/finance/bank-opening-balance")
@Validated
public class FinanceBankOpeningBalanceController {

    private final FinanceBankOpeningBalanceService openingBalanceService;

    public FinanceBankOpeningBalanceController(FinanceBankOpeningBalanceService openingBalanceService) {
        this.openingBalanceService = openingBalanceService;
    }

    @PostMapping("/create")
    @Operation(summary = "登记银行期初余额（按账户 upsert）")
    @PreAuthorize("@ss.hasPermission('finance:bank-opening:update')")
    public CommonResult<Long> create(@Valid @RequestBody FinanceBankOpeningBalanceSaveReqVO reqVO) {
        return success(openingBalanceService.upsert(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新银行期初余额（按账户 upsert）")
    @PreAuthorize("@ss.hasPermission('finance:bank-opening:update')")
    public CommonResult<Boolean> update(@Valid @RequestBody FinanceBankOpeningBalanceSaveReqVO reqVO) {
        openingBalanceService.upsert(reqVO);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "按账户查询期初余额（无记录返回空，不写入）")
    @Parameter(name = "accountId", description = "公司银行账户编号", required = true)
    @PreAuthorize("@ss.hasPermission('finance:report-bank-balance:query')")
    public CommonResult<FinanceBankOpeningBalanceRespVO> get(@RequestParam("accountId") Long accountId) {
        return success(BeanUtils.toBean(openingBalanceService.getByAccountId(accountId),
                FinanceBankOpeningBalanceRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "银行期初余额分页")
    @PreAuthorize("@ss.hasPermission('finance:report-bank-balance:query')")
    public CommonResult<PageResult<FinanceBankOpeningBalanceRespVO>> page(
            @Valid FinanceBankOpeningBalancePageReqVO pageReqVO) {
        PageResult<FinanceBankOpeningBalanceDO> page = openingBalanceService.getPage(pageReqVO);
        return success(BeanUtils.toBean(page, FinanceBankOpeningBalanceRespVO.class));
    }

}
