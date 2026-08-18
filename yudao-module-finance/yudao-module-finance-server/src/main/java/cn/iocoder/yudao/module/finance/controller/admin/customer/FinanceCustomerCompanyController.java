package cn.iocoder.yudao.module.finance.controller.admin.customer;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.excel.core.util.ExcelUtils;
import cn.iocoder.yudao.module.finance.controller.admin.customer.vo.FinanceCustomerCompanyImportExcelVO;
import cn.iocoder.yudao.module.finance.controller.admin.customer.vo.FinanceCustomerCompanyImportRespVO;
import cn.iocoder.yudao.module.finance.controller.admin.customer.vo.FinanceCustomerCompanyPageReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.customer.vo.FinanceCustomerCompanyRespVO;
import cn.iocoder.yudao.module.finance.controller.admin.customer.vo.FinanceCustomerCompanySaveReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.customer.vo.FinanceCustomerCompanyUpdateStatusReqVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.customer.FinanceCustomerCompanyDO;
import cn.iocoder.yudao.module.finance.service.customer.FinanceCustomerCompanyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 财务客户公司")
@RestController
@RequestMapping("/finance/customer-company")
@Validated
public class FinanceCustomerCompanyController {

    private final FinanceCustomerCompanyService customerCompanyService;

    public FinanceCustomerCompanyController(FinanceCustomerCompanyService customerCompanyService) {
        this.customerCompanyService = customerCompanyService;
    }

    @GetMapping("/get-import-template")
    @Operation(summary = "获得客户公司导入模板")
    @PreAuthorize("@ss.hasPermission('finance:customer-company:import')")
    public void importTemplate(HttpServletResponse response) throws IOException {
        List<FinanceCustomerCompanyImportExcelVO> list = Arrays.asList(
                FinanceCustomerCompanyImportExcelVO.builder()
                        .name("示例客户公司")
                        .taxNo("91110000DEMO001")
                        .isCustomerText("是")
                        .isSupplierText("否")
                        .bankName("")
                        .bankAccount("")
                        .address("示例地址")
                        .phone("13800000000")
                        .contactName("张三")
                        .email("demo@example.com")
                        .build()
        );
        ExcelUtils.write(response, "客户公司导入模板.xls", "客户公司",
                FinanceCustomerCompanyImportExcelVO.class, list);
    }

    @PostMapping("/import")
    @Operation(summary = "导入客户公司")
    @PreAuthorize("@ss.hasPermission('finance:customer-company:import')")
    public CommonResult<FinanceCustomerCompanyImportRespVO> importCustomerCompany(
            @RequestParam("file") MultipartFile file) throws IOException {
        return success(customerCompanyService.importCustomerCompanyList(
                ExcelUtils.read(file, FinanceCustomerCompanyImportExcelVO.class)));
    }

    @PostMapping("/create")
    @Operation(summary = "创建客户公司")
    @PreAuthorize("@ss.hasPermission('finance:customer-company:create')")
    public CommonResult<Long> createCustomerCompany(@Valid @RequestBody FinanceCustomerCompanySaveReqVO createReqVO) {
        return success(customerCompanyService.createCustomerCompany(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新客户公司")
    @PreAuthorize("@ss.hasPermission('finance:customer-company:update')")
    public CommonResult<Boolean> updateCustomerCompany(@Valid @RequestBody FinanceCustomerCompanySaveReqVO updateReqVO) {
        customerCompanyService.updateCustomerCompany(updateReqVO);
        return success(true);
    }

    @PutMapping("/update-status")
    @Operation(summary = "启用/停用客户公司")
    @PreAuthorize("@ss.hasPermission('finance:customer-company:update')")
    public CommonResult<Boolean> updateStatus(@Valid @RequestBody FinanceCustomerCompanyUpdateStatusReqVO reqVO) {
        customerCompanyService.updateStatus(reqVO.getId(), reqVO.getStatus());
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得客户公司")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('finance:customer-company:query')")
    public CommonResult<FinanceCustomerCompanyRespVO> getCustomerCompany(@RequestParam("id") Long id) {
        FinanceCustomerCompanyDO company = customerCompanyService.getCustomerCompany(id);
        return success(BeanUtils.toBean(company, FinanceCustomerCompanyRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得客户公司分页")
    @PreAuthorize("@ss.hasPermission('finance:customer-company:query')")
    public CommonResult<PageResult<FinanceCustomerCompanyRespVO>> getCustomerCompanyPage(
            @Valid FinanceCustomerCompanyPageReqVO pageReqVO) {
        PageResult<FinanceCustomerCompanyDO> page = customerCompanyService.getCustomerCompanyPage(pageReqVO);
        return success(BeanUtils.toBean(page, FinanceCustomerCompanyRespVO.class));
    }

    @GetMapping("/simple-list")
    @Operation(summary = "启用中的客商精简列表；role=CUSTOMER（默认，开票/合同）或 SUPPLIER（付款收款方，须银行齐全）")
    @Parameter(name = "role", description = "CUSTOMER | SUPPLIER，默认 CUSTOMER")
    @PreAuthorize("@ss.hasPermission('finance:customer-company:simple-list')")
    public CommonResult<List<FinanceCustomerCompanyRespVO>> getEnabledSimpleList(
            @RequestParam(value = "role", required = false) String role) {
        List<FinanceCustomerCompanyDO> list = customerCompanyService.getEnabledSimpleList(role);
        return success(BeanUtils.toBean(list, FinanceCustomerCompanyRespVO.class));
    }

}
