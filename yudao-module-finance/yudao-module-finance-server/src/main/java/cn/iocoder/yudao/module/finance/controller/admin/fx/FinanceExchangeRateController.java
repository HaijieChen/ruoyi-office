package cn.iocoder.yudao.module.finance.controller.admin.fx;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.finance.controller.admin.fx.vo.FinanceExchangeRatePageReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.fx.vo.FinanceExchangeRateRespVO;
import cn.iocoder.yudao.module.finance.controller.admin.fx.vo.FinanceExchangeRateSaveReqVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.fx.FinanceExchangeRateDO;
import cn.iocoder.yudao.module.finance.service.fx.FinanceExchangeRateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 汇率维护")
@RestController
@RequestMapping("/finance/exchange-rate")
@Validated
public class FinanceExchangeRateController {
    private final FinanceExchangeRateService service;
    public FinanceExchangeRateController(FinanceExchangeRateService service) { this.service = service; }

    @PostMapping("/save")
    @Operation(summary = "保存月度汇率")
    @PreAuthorize("@ss.hasPermission('finance:exchange-rate:update')")
    public CommonResult<Long> save(@Valid @RequestBody FinanceExchangeRateSaveReqVO reqVO) {
        return success(service.save(reqVO));
    }

    @GetMapping("/page")
    @Operation(summary = "汇率分页")
    @PreAuthorize("@ss.hasPermission('finance:exchange-rate:query')")
    public CommonResult<PageResult<FinanceExchangeRateRespVO>> page(@Valid FinanceExchangeRatePageReqVO reqVO) {
        PageResult<FinanceExchangeRateDO> page = service.getPage(reqVO);
        return success(BeanUtils.toBean(page, FinanceExchangeRateRespVO.class));
    }
}
