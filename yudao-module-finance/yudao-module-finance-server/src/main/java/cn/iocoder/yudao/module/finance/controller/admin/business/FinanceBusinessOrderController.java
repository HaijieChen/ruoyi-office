package cn.iocoder.yudao.module.finance.controller.admin.business;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.collection.MapUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.excel.core.util.ExcelUtils;
import cn.iocoder.yudao.module.finance.controller.admin.business.vo.FinanceBusinessOrderExportExcelVO;
import cn.iocoder.yudao.module.finance.controller.admin.business.vo.FinanceBusinessOrderImportExcelVO;
import cn.iocoder.yudao.module.finance.controller.admin.business.vo.FinanceBusinessOrderImportRespVO;
import cn.iocoder.yudao.module.finance.controller.admin.business.vo.FinanceBusinessOrderPageReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.business.vo.FinanceBusinessOrderRespVO;
import cn.iocoder.yudao.module.finance.controller.admin.business.vo.FinanceBusinessOrderSaveReqVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.business.FinanceBusinessOrderDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.contract.FinanceContractApplicationDO;
import cn.iocoder.yudao.module.finance.dal.mysql.contract.FinanceContractApplicationMapper;
import cn.iocoder.yudao.module.finance.service.business.FinanceBusinessOrderService;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
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
    @Resource
    private FinanceContractApplicationMapper contractApplicationMapper;

    @GetMapping("/get-import-template")
    @Operation(summary = "获得商务签单导入模板")
    @PreAuthorize("@ss.hasPermission('finance:business-order:import')")
    public void importTemplate(HttpServletResponse response) throws IOException {
        List<FinanceBusinessOrderImportExcelVO> list = Arrays.asList(
                FinanceBusinessOrderImportExcelVO.builder()
                        .entityCompanyName("示例主体公司")
                        .applicantUsername("yunai")
                        .contractApplicationNo("HT-DEMO-001")
                        .orderDate(LocalDate.of(2026, 7, 20))
                        .productName("示例产品A")
                        .contactPerson("张三")
                        .executionStartDate(LocalDate.of(2026, 7, 20))
                        .executionEndDate(LocalDate.of(2026, 8, 20))
                        .payerName("示例付款方A")
                        .signedExecutionAmount(new BigDecimal("10000.00"))
                        .discountRate(new BigDecimal("0.10"))
                        .summary("示例摘要/附言")
                        .build(),
                FinanceBusinessOrderImportExcelVO.builder()
                        .entityCompanyName("示例主体公司")
                        .applicantUsername("yunai")
                        .contractApplicationNo("HT-DEMO-002")
                        .orderDate(LocalDate.of(2026, 7, 21))
                        .productName("示例产品B")
                        .contactPerson("李四")
                        .executionStartDate(LocalDate.of(2026, 7, 21))
                        .executionEndDate(LocalDate.of(2026, 9, 21))
                        .payerName("")
                        .signedExecutionAmount(new BigDecimal("5000.00"))
                        .discountRate(BigDecimal.ZERO)
                        .summary("折扣率可留空，按 0 处理")
                        .build()
        );
        ExcelUtils.write(response, "商务签单导入模板.xls", "商务签单", FinanceBusinessOrderImportExcelVO.class, list);
    }

    @PostMapping("/import")
    @Operation(summary = "导入商务签单")
    @PreAuthorize("@ss.hasPermission('finance:business-order:import')")
    public CommonResult<FinanceBusinessOrderImportRespVO> importBusinessOrder(
            @RequestParam("file") MultipartFile file) throws IOException {
        return success(businessOrderService.importBusinessOrderList(
                ExcelUtils.read(file, FinanceBusinessOrderImportExcelVO.class), getLoginUserId()));
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
    public CommonResult<cn.iocoder.yudao.module.finance.controller.admin.common.vo.FinanceBatchDeleteRespVO> deleteBusinessOrder(
            @RequestParam("ids") List<Long> ids) {
        return success(businessOrderService.deleteList(ids));
    }

    @PostMapping("/delete-query")
    @Operation(summary = "按当前筛选删除商务签单")
    @PreAuthorize("@ss.hasPermission('finance:business-order:delete')")
    public CommonResult<cn.iocoder.yudao.module.finance.controller.admin.common.vo.FinanceBatchDeleteRespVO> deleteQuery(
            @Valid @RequestBody FinanceBusinessOrderPageReqVO reqVO) {
        return success(businessOrderService.deleteByQuery(reqVO));
    }

    @GetMapping("/export-excel")
    @Operation(summary = "按当前筛选导出商务签单 Excel")
    @PreAuthorize("@ss.hasPermission('finance:business-order:export')")
    public void exportExcel(@Valid FinanceBusinessOrderPageReqVO reqVO, HttpServletResponse response)
            throws IOException {
        List<FinanceBusinessOrderDO> orders = businessOrderService.listForExport(reqVO);
        java.util.Set<Long> userIds = new java.util.HashSet<>();
        for (FinanceBusinessOrderDO order : orders) {
            if (order.getApplicantUserId() != null) {
                userIds.add(order.getApplicantUserId());
            }
        }
        Map<Long, AdminUserRespDTO> userMap = userIds.isEmpty()
                ? java.util.Collections.emptyMap() : adminUserApi.getUserMap(userIds);
        List<FinanceBusinessOrderExportExcelVO> rows = new java.util.ArrayList<>();
        for (FinanceBusinessOrderDO order : orders) {
            FinanceBusinessOrderExportExcelVO vo = BeanUtils.toBean(order, FinanceBusinessOrderExportExcelVO.class);
            MapUtils.findAndThen(userMap, order.getApplicantUserId(),
                    user -> vo.setApplicantName(user.getNickname()));
            rows.add(vo);
        }
        ExcelUtils.write(response, "商务签单.xls", "商务签单", FinanceBusinessOrderExportExcelVO.class, rows);
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
        PageResult<FinanceBusinessOrderRespVO> voPage =
                BeanUtils.toBean(pageResult, FinanceBusinessOrderRespVO.class, this::fillUserNames);
        List<FinanceBusinessOrderDO> orders = pageResult.getList();
        List<FinanceBusinessOrderRespVO> vos = voPage.getList();
        if (orders != null && vos != null) {
            int n = Math.min(orders.size(), vos.size());
            for (int i = 0; i < n; i++) {
                enrichProductType(vos.get(i), orders.get(i));
            }
        }
        fillContractApplicationNos(voPage.getList());
        return success(voPage);
    }

    private FinanceBusinessOrderRespVO buildBusinessOrderVO(FinanceBusinessOrderDO businessOrder) {
        if (businessOrder == null) {
            return null;
        }
        FinanceBusinessOrderRespVO respVO = BeanUtils.toBean(businessOrder, FinanceBusinessOrderRespVO.class);
        enrichProductType(respVO, businessOrder);
        enrichRemainingBalance(respVO);
        fillContractApplicationNos(List.of(respVO));
        fillUserNames(respVO);
        return respVO;
    }

    private void fillUserNames(FinanceBusinessOrderRespVO respVO) {
        enrichRemainingBalance(respVO);
        // 分页：BeanUtils 不带 productTypeSnapshot；blank-aware 回退 productName（与 Mapper 一致）
        if (respVO.getProductType() == null || respVO.getProductType().isBlank()) {
            if (respVO.getProductName() != null && !respVO.getProductName().isBlank()) {
                respVO.setProductType(respVO.getProductName().trim());
            }
        }
        java.util.Set<Long> ids = new java.util.HashSet<>();
        if (respVO.getImporterId() != null) {
            ids.add(respVO.getImporterId());
        }
        if (respVO.getApplicantUserId() != null) {
            ids.add(respVO.getApplicantUserId());
        }
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, AdminUserRespDTO> userMap = adminUserApi.getUserMap(ids);
        MapUtils.findAndThen(userMap, respVO.getImporterId(),
                importer -> respVO.setImporterName(importer.getNickname()));
        MapUtils.findAndThen(userMap, respVO.getApplicantUserId(),
                applicant -> respVO.setApplicantName(applicant.getNickname()));
    }

    /** 列表/详情展示正式合同业务单号（application_no） */
    private void fillContractApplicationNos(List<FinanceBusinessOrderRespVO> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        Collection<Long> ids = list.stream()
                .map(FinanceBusinessOrderRespVO::getContractApplicationId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, String> noById = new HashMap<>();
        for (FinanceContractApplicationDO contract : contractApplicationMapper.selectBatchIds(ids)) {
            if (contract != null && contract.getId() != null) {
                noById.put(contract.getId(), contract.getApplicationNo());
            }
        }
        for (FinanceBusinessOrderRespVO respVO : list) {
            if (respVO.getContractApplicationId() != null) {
                respVO.setContractApplicationNo(noById.get(respVO.getContractApplicationId()));
            }
        }
    }

    private void enrichRemainingBalance(FinanceBusinessOrderRespVO respVO) {
        BigDecimal settlement = respVO.getSettlementAmount();
        BigDecimal claimed = respVO.getConfirmedClaimedAmount();
        if (settlement != null && claimed != null) {
            respVO.setRemainingBalance(settlement.subtract(claimed));
        }
        // 可开余额 = 结算 - 开票占用（与 CAS increaseInvoicedOccupiedAmount 口径一致）
        if (settlement != null) {
            BigDecimal occupied = respVO.getInvoicedOccupiedAmount() != null
                    ? respVO.getInvoicedOccupiedAmount() : BigDecimal.ZERO;
            respVO.setInvoiceOpenableAmount(settlement.subtract(occupied));
        }
    }

    /**
     * EXP-70：规范字段 productType 与筛选口径一致（blank-aware）。
     * {@code COALESCE(NULLIF(TRIM(snapshot),''), product_name)}
     */
    private static void enrichProductType(FinanceBusinessOrderRespVO respVO, FinanceBusinessOrderDO order) {
        if (respVO == null || order == null) {
            return;
        }
        respVO.setProductType(resolveEffectiveProductType(
                order.getProductTypeSnapshot(), order.getProductName()));
    }

    /** 与 Mapper 产品筛选 blank-aware 口径一致，便于单测断言 */
    public static String resolveEffectiveProductType(String productTypeSnapshot, String productName) {
        if (productTypeSnapshot != null && !productTypeSnapshot.isBlank()) {
            return productTypeSnapshot.trim();
        }
        if (productName != null && !productName.isBlank()) {
            return productName.trim();
        }
        return productName;
    }

}
