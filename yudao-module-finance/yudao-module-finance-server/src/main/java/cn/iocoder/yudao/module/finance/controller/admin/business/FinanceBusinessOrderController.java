package cn.iocoder.yudao.module.finance.controller.admin.business;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.collection.MapUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.excel.core.util.ExcelUtils;
import cn.iocoder.yudao.module.finance.controller.admin.business.vo.FinanceBusinessOrderImportExcelVO;
import cn.iocoder.yudao.module.finance.controller.admin.business.vo.FinanceBusinessOrderImportRespVO;
import cn.iocoder.yudao.module.finance.controller.admin.business.vo.FinanceBusinessOrderPageReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.business.vo.FinanceBusinessOrderRespVO;
import cn.iocoder.yudao.module.finance.controller.admin.business.vo.FinanceBusinessOrderSaveReqVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.business.FinanceBusinessOrderDO;
import cn.iocoder.yudao.module.finance.service.business.FinanceBusinessOrderService;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertSet;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - 商务签单")
@RestController
@RequestMapping("/finance/business-order")
@Validated
public class FinanceBusinessOrderController {

    @Resource
    private FinanceBusinessOrderService businessOrderService;
    @Resource
    private AdminUserApi adminUserApi;

    @PostMapping("/import")
    @Operation(summary = "导入商务签单")
    @PreAuthorize("@ss.hasPermission('finance:business-order:import')")
    public CommonResult<FinanceBusinessOrderImportRespVO> importBusinessOrder(
            @RequestParam("file") MultipartFile file,
            @RequestParam("bankAccount") String bankAccount) throws IOException {
        return success(businessOrderService.importBusinessOrderList(
                ExcelUtils.read(file, FinanceBusinessOrderImportExcelVO.class), getLoginUserId(), bankAccount));
    }

    @PostMapping("/create")
    @Operation(summary = "创建商务签单")
    @PreAuthorize("@ss.hasPermission('finance:business-order:create')")
    public CommonResult<Long> createBusinessOrder(@Valid @RequestBody FinanceBusinessOrderSaveReqVO createReqVO) {
        return success(businessOrderService.createBusinessOrder(createReqVO, getLoginUserId()));
    }

    @PutMapping("/update")
    @Operation(summary = "更新商务签单")
    @PreAuthorize("@ss.hasPermission('finance:business-order:update')")
    public CommonResult<Boolean> updateBusinessOrder(@Valid @RequestBody FinanceBusinessOrderSaveReqVO updateReqVO) {
        businessOrderService.updateBusinessOrder(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除商务签单")
    @Parameter(name = "ids", description = "编号数组", required = true)
    @PreAuthorize("@ss.hasPermission('finance:business-order:delete')")
    public CommonResult<Boolean> deleteBusinessOrder(@RequestParam("ids") List<Long> ids) {
        businessOrderService.deleteBusinessOrder(ids);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得商务签单")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('finance:business-order:query')")
    public CommonResult<FinanceBusinessOrderRespVO> getBusinessOrder(@RequestParam("id") Long id) {
        FinanceBusinessOrderDO businessOrder = businessOrderService.getBusinessOrder(id);
        return success(buildBusinessOrderVO(businessOrder));
    }

    @GetMapping("/page")
    @Operation(summary = "获得商务签单分页")
    @PreAuthorize("@ss.hasPermission('finance:business-order:query')")
    public CommonResult<PageResult<FinanceBusinessOrderRespVO>> getBusinessOrderPage(@Valid FinanceBusinessOrderPageReqVO pageReqVO) {
        PageResult<FinanceBusinessOrderDO> pageResult = businessOrderService.getBusinessOrderPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, FinanceBusinessOrderRespVO.class, this::fillImporterNames));
    }

    private FinanceBusinessOrderRespVO buildBusinessOrderVO(FinanceBusinessOrderDO businessOrder) {
        if (businessOrder == null) {
            return null;
        }
        FinanceBusinessOrderRespVO respVO = BeanUtils.toBean(businessOrder, FinanceBusinessOrderRespVO.class);
        enrichRemainingBalance(respVO);
        if (businessOrder.getImporterId() == null) {
            return respVO;
        }
        AdminUserRespDTO importer = adminUserApi.getUser(businessOrder.getImporterId()).getCheckedData();
        if (importer != null) {
            respVO.setImporterName(importer.getNickname());
        }
        return respVO;
    }

    private void fillImporterNames(FinanceBusinessOrderRespVO respVO) {
        enrichRemainingBalance(respVO);
        if (respVO.getImporterId() == null) {
            return;
        }
        Map<Long, AdminUserRespDTO> userMap = adminUserApi.getUserMap(
                convertSet(List.of(respVO), FinanceBusinessOrderRespVO::getImporterId));
        MapUtils.findAndThen(userMap, respVO.getImporterId(),
                importer -> respVO.setImporterName(importer.getNickname()));
    }

    private void enrichRemainingBalance(FinanceBusinessOrderRespVO respVO) {
        BigDecimal settlement = respVO.getSettlementAmount();
        BigDecimal claimed = respVO.getConfirmedClaimedAmount();
        if (settlement != null && claimed != null) {
            respVO.setRemainingBalance(settlement.subtract(claimed));
        }
    }

}
