package cn.iocoder.yudao.module.finance.controller.admin.invoice;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.finance.controller.admin.invoice.vo.*;
import cn.iocoder.yudao.module.finance.dal.dataobject.business.FinanceBusinessOrderDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.contract.FinanceContractApplicationDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.invoice.FinanceInvoiceApplicationDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.invoice.FinanceInvoiceApplicationLineDO;
import cn.iocoder.yudao.module.finance.dal.mysql.business.FinanceBusinessOrderMapper;
import cn.iocoder.yudao.module.finance.dal.mysql.contract.FinanceContractApplicationMapper;
import cn.iocoder.yudao.framework.excel.core.util.ExcelUtils;
import cn.iocoder.yudao.module.finance.service.invoice.FinanceInvoiceApplicationImportService;
import cn.iocoder.yudao.module.finance.service.invoice.FinanceInvoiceApplicationService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Arrays;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - 开票申请")
@RestController
@RequestMapping("/finance/invoice-application")
@Validated
public class FinanceInvoiceApplicationController {

    @Resource
    private FinanceInvoiceApplicationService invoiceApplicationService;
    @Resource
    private FinanceInvoiceApplicationImportService invoiceApplicationImportService;
    @Resource
    private FinanceBusinessOrderMapper businessOrderMapper;
    @Resource
    private FinanceContractApplicationMapper contractApplicationMapper;

    @GetMapping("/get-import-template")
    @Operation(summary = "获得开票申请历史导入模板")
    @PreAuthorize("@ss.hasPermission('finance:invoice-application:import')")
    public void importTemplate(HttpServletResponse response) throws IOException {
        ExcelUtils.write(response, "开票申请历史导入模板.xls", "开票申请",
                FinanceInvoiceApplicationImportExcelVO.class,
                Arrays.asList(FinanceInvoiceApplicationImportExcelVO.builder()
                        .applicationNo("INV-H-001")
                        .applicantUsername("admin")
                        .buyerName("示例客户")
                        .totalAmount(new BigDecimal("1000.00"))
                        .currency("CNY")
                        .invoiceCompany("示例主体公司")
                        .productType("广告")
                        .issueTime("2026-01-15")
                        .businessOrderNo("BO-001")
                        .invoiceNo("12345678")
                        .build()));
    }

    @PostMapping("/import")
    @Operation(summary = "导入历史开票申请（已通过且办票完成，不启流程、不占商务单）")
    @PreAuthorize("@ss.hasPermission('finance:invoice-application:import')")
    public CommonResult<FinanceInvoiceApplicationImportRespVO> importHistorical(
            @RequestParam("file") MultipartFile file) throws IOException {
        return success(invoiceApplicationImportService.importHistorical(
                ExcelUtils.read(file, FinanceInvoiceApplicationImportExcelVO.class)));
    }

    @PostMapping("/create-and-start")
    @Operation(summary = "创建开票申请并启动审批（无草稿）")
    @PreAuthorize("isAuthenticated()")
    public CommonResult<Long> createAndStart(@Valid @RequestBody FinanceInvoiceApplicationCreateAndStartReqVO reqVO) {
        return success(invoiceApplicationService.createAndStart(reqVO, getLoginUserId()));
    }

    @PutMapping("/resubmit")
    @Operation(summary = "驳回后重提开票申请（释占→换明细→再占→新流程）")
    @Parameter(name = "id", description = "申请编号", required = true)
    @PreAuthorize("@ss.hasPermission('finance:invoice-application:resubmit')")
    public CommonResult<Boolean> resubmit(@RequestParam("id") Long id,
                                          @Valid @RequestBody FinanceInvoiceApplicationResubmitReqVO reqVO) {
        invoiceApplicationService.resubmit(id, reqVO, getLoginUserId());
        return success(true);
    }

    @PostMapping("/resubmit")
    @Operation(summary = "驳回后重提开票申请（POST 别名）")
    @Parameter(name = "id", description = "申请编号", required = true)
    @PreAuthorize("@ss.hasPermission('finance:invoice-application:resubmit')")
    public CommonResult<Boolean> resubmitPost(@RequestParam("id") Long id,
                                              @Valid @RequestBody FinanceInvoiceApplicationResubmitReqVO reqVO) {
        invoiceApplicationService.resubmit(id, reqVO, getLoginUserId());
        return success(true);
    }

    @PutMapping("/complete-issue")
    @Operation(summary = "整单办票（多附件 replace，issue_status=FULL）")
    @PreAuthorize("@ss.hasPermission('finance:invoice-application:issue')")
    public CommonResult<Boolean> completeIssue(
            @Valid @RequestBody FinanceInvoiceApplicationCompleteIssueReqVO reqVO) {
        invoiceApplicationService.completeIssue(reqVO);
        return success(true);
    }

    @PostMapping("/complete-issue")
    @Operation(summary = "整单办票（POST 别名）")
    @PreAuthorize("@ss.hasPermission('finance:invoice-application:issue')")
    public CommonResult<Boolean> completeIssuePost(
            @Valid @RequestBody FinanceInvoiceApplicationCompleteIssueReqVO reqVO) {
        invoiceApplicationService.completeIssue(reqVO);
        return success(true);
    }

    @Deprecated
    @PutMapping("/update-issue-progress")
    @Operation(summary = "办票进度（一行一票，已废弃，请用 complete-issue）")
    @PreAuthorize("@ss.hasPermission('finance:invoice-application:issue')")
    public CommonResult<Boolean> updateIssueProgress(
            @Valid @RequestBody FinanceInvoiceApplicationUpdateIssueProgressReqVO reqVO) {
        invoiceApplicationService.updateIssueProgress(reqVO);
        return success(true);
    }

    @Deprecated
    @PostMapping("/update-issue-progress")
    @Operation(summary = "办票进度（POST 别名，已废弃）")
    @PreAuthorize("@ss.hasPermission('finance:invoice-application:issue')")
    public CommonResult<Boolean> updateIssueProgressPost(
            @Valid @RequestBody FinanceInvoiceApplicationUpdateIssueProgressReqVO reqVO) {
        invoiceApplicationService.updateIssueProgress(reqVO);
        return success(true);
    }

    @PostMapping("/on-approval-outcome")
    @Operation(summary = "同步审批落账（内部/联调；生产主路径走 Flowable Delegate）")
    @PreAuthorize("@ss.hasPermission('finance:invoice-application:update')")
    public CommonResult<Boolean> onApprovalOutcome(
            @Valid @RequestBody FinanceInvoiceApplicationApprovalOutcomeReqVO reqVO) {
        invoiceApplicationService.onApprovalOutcome(reqVO.getApplicationId(), reqVO.getOutcome());
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得开票申请详情")
    @Parameter(name = "id", description = "申请编号", required = true)
    @PreAuthorize("@ss.hasPermission('finance:invoice-application:query')")
    public CommonResult<FinanceInvoiceApplicationRespVO> getApplication(@RequestParam("id") Long id) {
        FinanceInvoiceApplicationDO application = invoiceApplicationService.getApplication(id);
        FinanceInvoiceApplicationRespVO respVO = BeanUtils.toBean(application, FinanceInvoiceApplicationRespVO.class);
        List<FinanceInvoiceApplicationLineDO> lines = invoiceApplicationService.getApplicationLines(id);
        respVO.setLines(BeanUtils.toBean(lines, FinanceInvoiceApplicationRespVO.Line.class));
        enrichInvoiceLines(respVO.getLines(), lines);
        respVO.setFiles(BeanUtils.toBean(
                invoiceApplicationService.getApplicationFiles(id),
                FinanceInvoiceApplicationRespVO.FileItem.class));
        return success(respVO);
    }

    @GetMapping("/page")
    @Operation(summary = "获得开票申请分页")
    @PreAuthorize("@ss.hasPermission('finance:invoice-application:query')")
    public CommonResult<PageResult<FinanceInvoiceApplicationRespVO>> getApplicationPage(
            @Valid FinanceInvoiceApplicationPageReqVO pageReqVO) {
        PageResult<FinanceInvoiceApplicationDO> page = invoiceApplicationService.getApplicationPage(pageReqVO);
        return success(BeanUtils.toBean(page, FinanceInvoiceApplicationRespVO.class));
    }

    /**
     * EXP-70 P1 #2：历史字段只读行快照；现值另开 current* 字段，禁止伪装历史。
     */
    private void enrichInvoiceLines(List<FinanceInvoiceApplicationRespVO.Line> lineVOs,
                                    List<FinanceInvoiceApplicationLineDO> lineDOs) {
        if (CollUtil.isEmpty(lineVOs) || CollUtil.isEmpty(lineDOs)) {
            return;
        }
        Set<Long> boIds = new HashSet<>();
        Set<Long> contractIds = new HashSet<>();
        for (FinanceInvoiceApplicationLineDO line : lineDOs) {
            if (line.getBusinessOrderId() != null) {
                boIds.add(line.getBusinessOrderId());
            }
            if (line.getSourceContractApplicationId() != null) {
                contractIds.add(line.getSourceContractApplicationId());
            }
        }
        Map<Long, FinanceBusinessOrderDO> boById = new HashMap<>();
        if (CollUtil.isNotEmpty(boIds)) {
            for (FinanceBusinessOrderDO order : businessOrderMapper.selectListByIds(boIds)) {
                if (order != null && order.getId() != null) {
                    boById.put(order.getId(), order);
                    if (order.getContractApplicationId() != null) {
                        contractIds.add(order.getContractApplicationId());
                    }
                }
            }
        }
        Map<Long, String> contractNoById = new HashMap<>();
        if (CollUtil.isNotEmpty(contractIds)) {
            for (FinanceContractApplicationDO contract : contractApplicationMapper.selectBatchIds(contractIds)) {
                if (contract != null && contract.getId() != null) {
                    contractNoById.put(contract.getId(), contract.getApplicationNo());
                }
            }
        }
        int size = Math.min(lineVOs.size(), lineDOs.size());
        for (int i = 0; i < size; i++) {
            FinanceInvoiceApplicationRespVO.Line lineVO = lineVOs.get(i);
            FinanceInvoiceApplicationLineDO lineDO = lineDOs.get(i);
            // —— 历史字段：只读行级快照 ——
            String lineProduct = lineDO.getProductTypeSnapshot();
            Long lineSourceContractId = lineDO.getSourceContractApplicationId();
            lineVO.setProductType(lineProduct);
            lineVO.setSourceContractApplicationId(lineSourceContractId);
            boolean productUnproven = lineProduct == null || lineProduct.isBlank();
            boolean sourceUnproven = lineSourceContractId == null;
            lineVO.setHistoryProductUnproven(productUnproven);
            lineVO.setHistorySourceContractUnproven(sourceUnproven);
            if (!sourceUnproven) {
                lineVO.setContractApplicationNo(contractNoById.get(lineSourceContractId));
            } else {
                lineVO.setContractApplicationNo(null);
            }
            // —— 当前态：明确命名，不可覆盖历史字段 ——
            FinanceBusinessOrderDO order = boById.get(lineDO.getBusinessOrderId());
            if (order != null) {
                lineVO.setBusinessOrderNo(order.getOrderNo());
                lineVO.setCurrentContractApplicationId(order.getContractApplicationId());
                if (order.getContractApplicationId() != null) {
                    lineVO.setCurrentContractApplicationNo(
                            contractNoById.get(order.getContractApplicationId()));
                }
                String currentProduct = order.getProductTypeSnapshot();
                if (currentProduct == null || currentProduct.isBlank()) {
                    // 现值展示可用 dual-read，但绝不写入历史 productType 字段
                    currentProduct = order.getProductName();
                }
                lineVO.setCurrentProductType(currentProduct);
            }
        }
    }

}
