package cn.iocoder.yudao.module.finance.service.contract;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.module.finance.controller.admin.contract.vo.FinanceContractApplicationImportExcelVO;
import cn.iocoder.yudao.module.finance.controller.admin.contract.vo.FinanceContractApplicationImportRespVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.contract.FinanceContractApplicationDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.customer.FinanceCustomerCompanyDO;
import cn.iocoder.yudao.module.finance.dal.mysql.contract.FinanceContractApplicationMapper;
import cn.iocoder.yudao.module.finance.enums.FinanceContractApprovalStatusEnum;
import cn.iocoder.yudao.module.finance.service.common.FinanceEntityCompanyResolver;
import cn.iocoder.yudao.module.finance.service.customer.FinanceCustomerCompanyService;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@Validated
public class FinanceContractApplicationImportServiceImpl implements FinanceContractApplicationImportService {

    private static final String DICT_PRODUCT_TYPE = "finance_product_type";

    private final FinanceContractApplicationMapper applicationMapper;
    private final FinanceCustomerCompanyService customerCompanyService;
    private final FinanceEntityCompanyResolver entityCompanyResolver;
    private final AdminUserApi adminUserApi;
    private final DictDataApi dictDataApi;

    public FinanceContractApplicationImportServiceImpl(FinanceContractApplicationMapper applicationMapper,
                                                       FinanceCustomerCompanyService customerCompanyService,
                                                       FinanceEntityCompanyResolver entityCompanyResolver,
                                                       AdminUserApi adminUserApi,
                                                       DictDataApi dictDataApi) {
        this.applicationMapper = applicationMapper;
        this.customerCompanyService = customerCompanyService;
        this.entityCompanyResolver = entityCompanyResolver;
        this.adminUserApi = adminUserApi;
        this.dictDataApi = dictDataApi;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FinanceContractApplicationImportRespVO importApprovedList(
            List<FinanceContractApplicationImportExcelVO> rows) {
        if (CollUtil.isEmpty(rows)) {
            throw new IllegalArgumentException("导入合同签约数据不能为空");
        }
        List<DeptRespDTO> companies = entityCompanyResolver.loadEnabledCompanies();
        List<FinanceCustomerCompanyDO> customers = customerCompanyService.getEnabledSimpleList();
        if (customers == null) {
            customers = List.of();
        }
        FinanceContractApplicationImportRespVO resp = FinanceContractApplicationImportRespVO.builder()
                .createdNos(new ArrayList<>())
                .failureRows(new LinkedHashMap<>())
                .build();
        Set<String> seenNos = new HashSet<>();
        for (int i = 0; i < rows.size(); i++) {
            int rowNumber = i + 2;
            FinanceContractApplicationImportSupport.ParsedRow[] parsed =
                    new FinanceContractApplicationImportSupport.ParsedRow[1];
            String err = FinanceContractApplicationImportSupport.validateAndParse(rows.get(i), parsed);
            if (err != null) {
                resp.getFailureRows().put(rowNumber, err);
                continue;
            }
            FinanceContractApplicationImportSupport.ParsedRow row = parsed[0];
            if (!seenNos.add(row.applicationNo())) {
                resp.getFailureRows().put(rowNumber, "本文件内合同业务单号重复");
                continue;
            }
            if (applicationMapper.selectByApplicationNo(row.applicationNo()) != null) {
                resp.getFailureRows().put(rowNumber, "合同业务单号已存在");
                continue;
            }
            AdminUserRespDTO user = adminUserApi.getUserByUsername(row.applicantUsername()).getData();
            if (user == null) {
                resp.getFailureRows().put(rowNumber, "申请人账号不存在");
                continue;
            }
            if (!Objects.equals(CommonStatusEnum.ENABLE.getStatus(), user.getStatus())) {
                resp.getFailureRows().put(rowNumber, "申请人账号已停用");
                continue;
            }
            FinanceEntityCompanyResolver.ResolvedCompany[] companyOut =
                    new FinanceEntityCompanyResolver.ResolvedCompany[1];
            String companyErr = entityCompanyResolver.matchByNameOrError(
                    row.entityCompanyName(), companyOut, companies);
            if (companyErr != null) {
                resp.getFailureRows().put(rowNumber, companyErr);
                continue;
            }
            List<FinanceCustomerCompanyDO> hits = new ArrayList<>();
            for (FinanceCustomerCompanyDO customer : customers) {
                if (customer != null && row.counterpartyName().equals(customer.getName())) {
                    hits.add(customer);
                }
            }
            if (hits.isEmpty()) {
                resp.getFailureRows().put(rowNumber, "对方客商未找到或未启用");
                continue;
            }
            if (hits.size() > 1) {
                resp.getFailureRows().put(rowNumber, "对方客商名称匹配到多家，请先改成唯一名称");
                continue;
            }
            try {
                dictDataApi.validateDictDataList(DICT_PRODUCT_TYPE, Collections.singletonList(row.productType()))
                        .checkError();
            } catch (Exception ex) {
                resp.getFailureRows().put(rowNumber, "产品类型不在启用字典中");
                continue;
            }
            FinanceCustomerCompanyDO matchedCustomer = hits.get(0);
            FinanceEntityCompanyResolver.ResolvedCompany company = companyOut[0];
            FinanceContractApplicationDO application = FinanceContractApplicationDO.builder()
                    .applicationNo(row.applicationNo())
                    .applicantUserId(user.getId())
                    .applicantDeptId(user.getDeptId())
                    .counterpartyCompanyId(matchedCustomer.getId())
                    .counterpartyName(matchedCustomer.getName())
                    .entityCompanyDeptId(company.deptId())
                    .entityCompanyName(company.name())
                    .signCompany(company.name())
                    .amountNa(row.amountNa())
                    .contractAmount(row.contractAmount())
                    .currency("CNY")
                    .fileName(row.fileName())
                    .fileType(row.fileType())
                    .productType(row.productType())
                    .rebateRatio(row.rebateRatio())
                    .settlementMethod(row.settlementMethod())
                    .copyCount(1)
                    .needMail(false)
                    .voided(false)
                    .approvalStatus(FinanceContractApprovalStatusEnum.APPROVED.getStatus())
                    .processInstanceId(null)
                    .startDate(row.startDate())
                    .endDate(row.endDate())
                    .build();
            applicationMapper.insert(application);
            resp.getCreatedNos().add(row.applicationNo());
        }
        return resp;
    }
}
