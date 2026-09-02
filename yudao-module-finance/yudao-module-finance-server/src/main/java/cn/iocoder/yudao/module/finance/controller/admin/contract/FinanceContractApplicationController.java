package cn.iocoder.yudao.module.finance.controller.admin.contract;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.excel.core.util.ExcelUtils;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.module.finance.controller.admin.contract.vo.*;
import cn.iocoder.yudao.module.finance.dal.dataobject.contract.FinanceContractApplicationDO;
import cn.iocoder.yudao.module.finance.service.contract.FinanceContractApplicationImportService;
import cn.iocoder.yudao.module.finance.service.contract.FinanceContractApplicationService;
import cn.iocoder.yudao.module.finance.service.invoice.FinanceInvoiceApplicationService;
import cn.iocoder.yudao.module.finance.service.payment.FinancePaymentPredocService;
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
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - 合同签约申请")
@RestController
@RequestMapping("/finance/contract-application")
@Validated
public class FinanceContractApplicationController {

    /** FA 持有 update 权限时可查全量；BS 仅本人（CS-F3）。 */
    private static final String MANAGE_ALL_PERMISSION = "finance:contract-application:update";

    @Resource
    private FinanceContractApplicationService contractApplicationService;
    @Resource
    private FinanceContractApplicationImportService contractApplicationImportService;
    @Resource
    private FinancePaymentPredocService paymentPredocService;
    @Resource
    private FinanceInvoiceApplicationService invoiceApplicationService;
    @Resource
    private SecurityFrameworkService securityFrameworkService;

    @GetMapping("/get-import-template")
    @Operation(summary = "获得合同签约导入模板")
    @PreAuthorize("@ss.hasPermission('finance:contract-application:import')")
    public void importTemplate(HttpServletResponse response) throws IOException {
        List<FinanceContractApplicationImportExcelVO> list = Arrays.asList(
                FinanceContractApplicationImportExcelVO.builder()
                        .applicationNo("HT-DEMO-001")
                        .applicantUsername("admin")
                        .entityCompanyName("示例主体")
                        .counterpartyName("示例客户")
                        .fileType("销售合同")
                        .productType("软件")
                        .amountApplicableText("是")
                        .contractAmount(new BigDecimal("1000.00"))
                        .rebateRatio("10%")
                        .settlementMethod("月结")
                        .fileName("销售合同.pdf")
                        .startDate(LocalDate.of(2026, 1, 1))
                        .endDate(LocalDate.of(2026, 12, 31))
                        .build()
        );
        ExcelUtils.write(response, "合同签约导入模板.xls", "合同签约",
                FinanceContractApplicationImportExcelVO.class, list);
    }

    @PostMapping("/import")
    @Operation(summary = "导入已通过合同签约（不启动审批）")
    @PreAuthorize("@ss.hasPermission('finance:contract-application:import')")
    public CommonResult<FinanceContractApplicationImportRespVO> importApproved(
            @RequestParam("file") MultipartFile file) throws IOException {
        return success(contractApplicationImportService.importApprovedList(
                ExcelUtils.read(file, FinanceContractApplicationImportExcelVO.class)));
    }

    @PostMapping("/create-and-start")
    @Operation(summary = "创建合同签约申请并启动审批（无草稿）")
    @PreAuthorize("isAuthenticated()")
    public CommonResult<Long> createAndStart(@Valid @RequestBody FinanceContractApplicationCreateAndStartReqVO reqVO) {
        return success(contractApplicationService.createAndStart(reqVO, getLoginUserId()));
    }

    @PutMapping("/resubmit")
    @Operation(summary = "驳回后重提（整链重批）")
    @Parameter(name = "id", description = "申请编号", required = true)
    @PreAuthorize("@ss.hasPermission('finance:contract-application:resubmit')")
    public CommonResult<Boolean> resubmit(@RequestParam("id") Long id,
                                          @Valid @RequestBody FinanceContractApplicationResubmitReqVO reqVO) {
        contractApplicationService.resubmit(id, reqVO, getLoginUserId());
        return success(true);
    }

    @PostMapping("/resubmit")
    @Operation(summary = "驳回后重提（POST 别名）")
    @PreAuthorize("@ss.hasPermission('finance:contract-application:resubmit')")
    public CommonResult<Boolean> resubmitPost(@RequestParam("id") Long id,
                                              @Valid @RequestBody FinanceContractApplicationResubmitReqVO reqVO) {
        contractApplicationService.resubmit(id, reqVO, getLoginUserId());
        return success(true);
    }

    @PutMapping("/update")
    @Operation(summary = "编辑合同签约台账（不重启审批）")
    @Parameter(name = "id", description = "申请编号", required = true)
    @PreAuthorize("@ss.hasAnyPermissions('finance:contract-application:update', 'finance:contract-application:import')")
    public CommonResult<Boolean> update(@RequestParam("id") Long id,
                                        @Valid @RequestBody FinanceContractApplicationCreateAndStartReqVO reqVO) {
        contractApplicationService.update(id, reqVO);
        return success(true);
    }

    @PostMapping("/cancel")
    @Operation(summary = "申请人撤回（仅用印前；同步取消 Flowable）")
    @Parameter(name = "id", description = "申请编号", required = true)
    @PreAuthorize("isAuthenticated()")
    public CommonResult<Boolean> cancel(@RequestParam("id") Long id) {
        contractApplicationService.cancel(id, getLoginUserId());
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除合同签约单据（逻辑删除；审批中请撤回）")
    @PreAuthorize("@ss.hasAnyPermissions('finance:contract-application:update', 'finance:contract-application:import')")
    public CommonResult<cn.iocoder.yudao.module.finance.controller.admin.common.vo.FinanceBatchDeleteRespVO> delete(
            @RequestParam(value = "id", required = false) Long id,
            @RequestParam(value = "ids", required = false) List<Long> ids) {
        java.util.ArrayList<Long> all = new java.util.ArrayList<>();
        if (id != null) {
            all.add(id);
        }
        if (ids != null) {
            all.addAll(ids);
        }
        return success(contractApplicationService.deleteList(all));
    }

    @PostMapping("/delete-query")
    @Operation(summary = "按当前筛选条件删除合同签约")
    @PreAuthorize("@ss.hasAnyPermissions('finance:contract-application:update', 'finance:contract-application:import')")
    public CommonResult<cn.iocoder.yudao.module.finance.controller.admin.common.vo.FinanceBatchDeleteRespVO> deleteQuery(
            @Valid @RequestBody FinanceContractApplicationPageReqVO reqVO) {
        return success(contractApplicationService.deleteByQuery(reqVO));
    }

    @GetMapping("/export-excel")
    @Operation(summary = "按当前筛选导出合同签约 Excel")
    @PreAuthorize("@ss.hasPermission('finance:contract-application:query')")
    public void exportExcel(@Valid FinanceContractApplicationPageReqVO reqVO, HttpServletResponse response)
            throws IOException {
        java.util.List<FinanceContractApplicationExportExcelVO> rows =
                BeanUtils.toBean(contractApplicationService.listForExport(reqVO),
                        FinanceContractApplicationExportExcelVO.class);
        ExcelUtils.write(response, "合同签约.xls", "合同签约", FinanceContractApplicationExportExcelVO.class, rows);
    }

    // CS-F1：已移除用户可调用的 on-approval-outcome HTTP 旁路；终态仅由 BPM 回调写入。

    @GetMapping("/get")
    @Operation(summary = "获得合同签约申请详情")
    // C30 / CS-R5：静态 query 或 本人/任务语境（转办后无 query 的办理人）
    @PreAuthorize("@ss.hasPermission('finance:contract-application:query') or @financeContractAccess.canTaskContextOrOwnerRead(#id)")
    public CommonResult<FinanceContractApplicationRespVO> getApplication(@RequestParam("id") Long id) {
        FinanceContractApplicationDO application = contractApplicationService.getApplication(
                id, getLoginUserId(), manageAll());
        FinanceContractApplicationRespVO vo = BeanUtils.toBean(application, FinanceContractApplicationRespVO.class);
        fillInvoiceOpenable(List.of(vo));
        return success(vo);
    }

    @GetMapping("/page")
    @Operation(summary = "获得合同签约申请分页")
    @PreAuthorize("@ss.hasPermission('finance:contract-application:query')")
    public CommonResult<PageResult<FinanceContractApplicationRespVO>> getApplicationPage(
            @Valid FinanceContractApplicationPageReqVO pageReqVO) {
        PageResult<FinanceContractApplicationDO> page = contractApplicationService.getApplicationPage(
                pageReqVO, getLoginUserId(), manageAll());
        PageResult<FinanceContractApplicationRespVO> voPage = BeanUtils.toBean(page, FinanceContractApplicationRespVO.class);
        fillInvoiceOpenable(voPage.getList());
        return success(voPage);
    }

    @GetMapping("/list-selectable-for-bo")
    @Operation(summary = "商务单可选合同（已通过且本人申请）")
    @PreAuthorize("@ss.hasAnyPermissions('finance:contract-application:query', 'finance:business-order:create', 'finance:business-order:query')")
    public CommonResult<java.util.List<FinanceContractApplicationRespVO>> listSelectableForBo() {
        return success(BeanUtils.toBean(
                contractApplicationService.listSelectableForBo(getLoginUserId()),
                FinanceContractApplicationRespVO.class));
    }

    @GetMapping("/list-selectable-for-business-payment")
    @Operation(summary = "付款可选付款业务合同（已通过 · fileType=付款业务合同 · 本人申请）")
    @PreAuthorize("@ss.hasAnyPermissions('finance:payment-application:create', 'finance:payment-application:query', 'finance:contract-application:query')")
    public CommonResult<java.util.List<FinanceContractApplicationRespVO>> listSelectableForBusinessPayment() {
        return success(BeanUtils.toBean(
                contractApplicationService.listSelectableForBusinessPayment(getLoginUserId()),
                FinanceContractApplicationRespVO.class));
    }

    @GetMapping("/list-selectable-for-lease-payment")
    @Operation(summary = "付款可选租赁合同（已通过 · fileType=租赁合同 · 本人申请）")
    @PreAuthorize("@ss.hasPermission('finance:contract-application:query')")
    public CommonResult<java.util.List<FinanceContractApplicationRespVO>> listSelectableForLeasePayment() {
        return success(BeanUtils.toBean(
                paymentPredocService.listSelectableLeaseContracts(getLoginUserId()),
                FinanceContractApplicationRespVO.class));
    }

    @PostMapping("/record-seal")
    @Operation(summary = "用印节点登记扫描件并 complete 任务")
    @PreAuthorize("@ss.hasPermission('finance:contract-application:record-seal')")
    public CommonResult<Boolean> recordSeal(@RequestParam("id") Long id,
                                            @RequestParam("taskId") String taskId,
                                            @RequestParam("sealFileUrl") String sealFileUrl) {
        contractApplicationService.recordSeal(id, taskId, sealFileUrl, getLoginUserId());
        return success(true);
    }

    @PostMapping("/record-archive")
    @Operation(summary = "合同列表归档：必填多份资料，只写台账")
    @PreAuthorize("@ss.hasPermission('finance:contract-application:record-seal')")
    public CommonResult<Boolean> recordArchive(@RequestParam("id") Long id,
                                               @RequestBody List<String> archiveFileUrls) {
        contractApplicationService.recordArchive(id, archiveFileUrls, getLoginUserId());
        return success(true);
    }

    @PostMapping("/record-mail")
    @Operation(summary = "邮寄节点登记单号并 complete 任务")
    @PreAuthorize("@ss.hasPermission('finance:contract-application:record-mail')")
    public CommonResult<Boolean> recordMail(@RequestParam("id") Long id,
                                            @RequestParam("taskId") String taskId,
                                            @RequestParam("mailTrackingNo") String mailTrackingNo) {
        contractApplicationService.recordMail(id, taskId, mailTrackingNo, getLoginUserId());
        return success(true);
    }

    private void fillInvoiceOpenable(List<FinanceContractApplicationRespVO> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        for (FinanceContractApplicationRespVO vo : list) {
            if (vo.getId() == null || Boolean.TRUE.equals(vo.getAmountNa())
                    || vo.getContractAmount() == null
                    || vo.getContractAmount().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal occupied = invoiceApplicationService.occupiedInvoiceAmount(vo.getId(), null);
            vo.setInvoiceOccupiedAmount(occupied);
            vo.setInvoiceOpenableAmount(vo.getContractAmount().subtract(occupied));
        }
    }

    private boolean manageAll() {
        return securityFrameworkService.hasPermission(MANAGE_ALL_PERMISSION);
    }
}
