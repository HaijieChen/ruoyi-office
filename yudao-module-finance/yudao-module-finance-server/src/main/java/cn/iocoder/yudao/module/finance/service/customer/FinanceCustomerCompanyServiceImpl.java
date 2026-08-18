package cn.iocoder.yudao.module.finance.service.customer;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.finance.controller.admin.customer.vo.FinanceCustomerCompanyImportExcelVO;
import cn.iocoder.yudao.module.finance.controller.admin.customer.vo.FinanceCustomerCompanyImportRespVO;
import cn.iocoder.yudao.module.finance.controller.admin.customer.vo.FinanceCustomerCompanyPageReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.customer.vo.FinanceCustomerCompanySaveReqVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.customer.FinanceCustomerCompanyDO;
import cn.iocoder.yudao.module.finance.dal.mysql.customer.FinanceCustomerCompanyMapper;
import cn.iocoder.yudao.module.finance.dal.redis.no.FinanceCustomerCompanyNoRedisDAO;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.*;

@Service
@Validated
public class FinanceCustomerCompanyServiceImpl implements FinanceCustomerCompanyService {

    private final FinanceCustomerCompanyMapper customerCompanyMapper;
    private final FinanceCustomerCompanyNoRedisDAO customerCompanyNoRedisDAO;

    public FinanceCustomerCompanyServiceImpl(FinanceCustomerCompanyMapper customerCompanyMapper,
                                             FinanceCustomerCompanyNoRedisDAO customerCompanyNoRedisDAO) {
        this.customerCompanyMapper = customerCompanyMapper;
        this.customerCompanyNoRedisDAO = customerCompanyNoRedisDAO;
    }

    @Override
    public Long createCustomerCompany(FinanceCustomerCompanySaveReqVO createReqVO) {
        String name = requireName(createReqVO.getName());
        String taxNo = requireTaxNo(createReqVO.getTaxNo());
        validateTaxNoUnique(taxNo, null);

        Integer status = createReqVO.getStatus() != null
                ? createReqVO.getStatus()
                : FinanceCustomerCompanyDO.STATUS_ENABLE;
        validateStatus(status);

        boolean isCustomer = resolveIsCustomer(createReqVO.getIsCustomer(), createReqVO.getIsSupplier());
        boolean isSupplier = resolveIsSupplier(createReqVO.getIsCustomer(), createReqVO.getIsSupplier());
        validateRoles(isCustomer, isSupplier);
        String bankName = trimToNull(createReqVO.getBankName());
        String bankAccount = trimToNull(createReqVO.getBankAccount());
        if (status == FinanceCustomerCompanyDO.STATUS_ENABLE) {
            validateSupplierBankIfNeeded(isSupplier, bankName, bankAccount);
        }

        FinanceCustomerCompanyDO company = buildFromSave(createReqVO, name, taxNo, bankName, bankAccount);
        company.setCode(customerCompanyNoRedisDAO.generate(LocalDate.now()));
        company.setIsCustomer(isCustomer);
        company.setIsSupplier(isSupplier);
        company.setPartyType(derivePartyType(isCustomer, isSupplier));
        company.setStatus(status);
        try {
            customerCompanyMapper.insert(company);
        } catch (DuplicateKeyException ex) {
            throw mapDuplicateKey(ex);
        }
        return company.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FinanceCustomerCompanyImportRespVO importCustomerCompanyList(
            List<FinanceCustomerCompanyImportExcelVO> rows) {
        if (CollUtil.isEmpty(rows)) {
            throw new IllegalArgumentException("导入客户公司数据不能为空");
        }
        FinanceCustomerCompanyImportRespVO resp = FinanceCustomerCompanyImportRespVO.builder()
                .createdCodes(new ArrayList<>())
                .failureRows(new LinkedHashMap<>())
                .build();
        Set<String> seenTaxNos = new HashSet<>();
        for (int i = 0; i < rows.size(); i++) {
            int rowNumber = i + 2;
            FinanceCustomerCompanyImportSupport.ParsedRow[] parsed = new FinanceCustomerCompanyImportSupport.ParsedRow[1];
            String err = FinanceCustomerCompanyImportSupport.validateAndParse(rows.get(i), parsed);
            if (err != null) {
                resp.getFailureRows().put(rowNumber, err);
                continue;
            }
            String taxNo = parsed[0].taxNo();
            if (!seenTaxNos.add(taxNo)) {
                resp.getFailureRows().put(rowNumber, "本文件内纳税人识别号重复");
                continue;
            }
            if (customerCompanyMapper.selectByTaxNo(taxNo) != null) {
                resp.getFailureRows().put(rowNumber, "纳税人识别号已存在");
                continue;
            }
            try {
                Long id = createCustomerCompany(parsed[0].saveReq());
                FinanceCustomerCompanyDO created = customerCompanyMapper.selectById(id);
                resp.getCreatedCodes().add(created.getCode());
            } catch (ServiceException ex) {
                resp.getFailureRows().put(rowNumber, ex.getMessage());
            }
        }
        return resp;
    }

    @Override
    public void updateCustomerCompany(FinanceCustomerCompanySaveReqVO updateReqVO) {
        if (updateReqVO.getId() == null) {
            throw exception(CUSTOMER_COMPANY_NOT_EXISTS);
        }
        FinanceCustomerCompanyDO existing = validateExists(updateReqVO.getId());
        String name = requireName(updateReqVO.getName());
        String taxNo = requireTaxNo(updateReqVO.getTaxNo());
        validateTaxNoUnique(taxNo, existing.getId());

        Integer status = updateReqVO.getStatus() != null ? updateReqVO.getStatus() : existing.getStatus();
        validateStatus(status);

        Boolean reqCustomer = updateReqVO.getIsCustomer();
        Boolean reqSupplier = updateReqVO.getIsSupplier();
        boolean isCustomer = reqCustomer != null || reqSupplier != null
                ? resolveIsCustomer(reqCustomer, reqSupplier)
                : Boolean.TRUE.equals(existing.getIsCustomer());
        boolean isSupplier = reqCustomer != null || reqSupplier != null
                ? resolveIsSupplier(reqCustomer, reqSupplier)
                : Boolean.TRUE.equals(existing.getIsSupplier());
        // 若请求未带角色字段，沿用库中；仍须至少一个
        if (reqCustomer == null && reqSupplier == null) {
            isCustomer = Boolean.TRUE.equals(existing.getIsCustomer());
            isSupplier = Boolean.TRUE.equals(existing.getIsSupplier());
            // 历史无列时 isCustomer 可能 null → 视为仅客户
            if (!isCustomer && !isSupplier) {
                isCustomer = true;
            }
        }
        validateRoles(isCustomer, isSupplier);

        String bankName = trimToNull(updateReqVO.getBankName());
        String bankAccount = trimToNull(updateReqVO.getBankAccount());
        if (status == FinanceCustomerCompanyDO.STATUS_ENABLE) {
            validateSupplierBankIfNeeded(isSupplier, bankName, bankAccount);
        }

        try {
            customerCompanyMapper.update(null, new UpdateWrapper<FinanceCustomerCompanyDO>()
                    .eq("id", existing.getId())
                    .set("name", name)
                    .set("tax_no", taxNo)
                    .set("bank_name", bankName)
                    .set("bank_account", bankAccount)
                    .set("address", trimToNull(updateReqVO.getAddress()))
                    .set("phone", trimToNull(updateReqVO.getPhone()))
                    .set("contact_name", trimToNull(updateReqVO.getContactName()))
                    .set("email", trimToNull(updateReqVO.getEmail()))
                    .set("is_customer", isCustomer)
                    .set("is_supplier", isSupplier)
                    .set("party_type", derivePartyType(isCustomer, isSupplier))
                    .set("status", status));
        } catch (DuplicateKeyException ex) {
            throw mapDuplicateKey(ex);
        }
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        FinanceCustomerCompanyDO current = validateExists(id);
        validateStatus(status);
        if (status == FinanceCustomerCompanyDO.STATUS_ENABLE) {
            requireName(current.getName());
            requireTaxNo(current.getTaxNo());
            boolean isSupplier = Boolean.TRUE.equals(current.getIsSupplier());
            validateSupplierBankIfNeeded(isSupplier, current.getBankName(), current.getBankAccount());
        }
        FinanceCustomerCompanyDO update = new FinanceCustomerCompanyDO();
        update.setId(id);
        update.setStatus(status);
        customerCompanyMapper.updateById(update);
    }

    @Override
    public FinanceCustomerCompanyDO getCustomerCompany(Long id) {
        return customerCompanyMapper.selectById(id);
    }

    @Override
    public FinanceCustomerCompanyDO getEnabledCustomerCompany(Long id) {
        if (id == null) {
            throw exception(INVOICE_APPLICATION_CUSTOMER_COMPANY_REQUIRED);
        }
        FinanceCustomerCompanyDO company = customerCompanyMapper.selectById(id);
        if (company == null) {
            throw exception(CUSTOMER_COMPANY_NOT_EXISTS);
        }
        if (!Objects.equals(company.getStatus(), FinanceCustomerCompanyDO.STATUS_ENABLE)) {
            throw exception(INVOICE_APPLICATION_CUSTOMER_COMPANY_DISABLED);
        }
        // 历史 null 视为客户；显式 false 拒绝
        if (Boolean.FALSE.equals(company.getIsCustomer())) {
            throw exception(CUSTOMER_COMPANY_NOT_CUSTOMER_ROLE);
        }
        return company;
    }

    @Override
    public FinanceCustomerCompanyDO getEnabledSupplierCompany(Long id) {
        if (id == null) {
            throw exception(CUSTOMER_COMPANY_NOT_EXISTS);
        }
        FinanceCustomerCompanyDO company = customerCompanyMapper.selectById(id);
        if (company == null) {
            throw exception(CUSTOMER_COMPANY_NOT_EXISTS);
        }
        if (!Objects.equals(company.getStatus(), FinanceCustomerCompanyDO.STATUS_ENABLE)
                || !Boolean.TRUE.equals(company.getIsSupplier())
                || StrUtil.isBlank(company.getBankName())
                || StrUtil.isBlank(company.getBankAccount())) {
            throw exception(CUSTOMER_COMPANY_NOT_SUPPLIER_ROLE);
        }
        return company;
    }

    @Override
    public PageResult<FinanceCustomerCompanyDO> getCustomerCompanyPage(FinanceCustomerCompanyPageReqVO pageReqVO) {
        return customerCompanyMapper.selectPage(pageReqVO);
    }

    @Override
    public List<FinanceCustomerCompanyDO> getEnabledSimpleList() {
        return getEnabledSimpleList(FinanceCustomerCompanyDO.ROLE_CUSTOMER);
    }

    @Override
    public List<FinanceCustomerCompanyDO> getEnabledSimpleList(String role) {
        String normalized = normalizeRole(role);
        if (FinanceCustomerCompanyDO.ROLE_SUPPLIER.equals(normalized)) {
            return customerCompanyMapper.selectEnabledSupplierList();
        }
        // 默认 CUSTOMER：开票/合同兼容
        return customerCompanyMapper.selectEnabledCustomerList();
    }

    private FinanceCustomerCompanyDO validateExists(Long id) {
        FinanceCustomerCompanyDO company = customerCompanyMapper.selectById(id);
        if (company == null) {
            throw exception(CUSTOMER_COMPANY_NOT_EXISTS);
        }
        return company;
    }

    private void validateTaxNoUnique(String taxNo, Long excludeId) {
        FinanceCustomerCompanyDO existing = customerCompanyMapper.selectByTaxNo(taxNo);
        if (existing != null && (excludeId == null || !Objects.equals(existing.getId(), excludeId))) {
            throw exception(CUSTOMER_COMPANY_TAX_NO_EXISTS);
        }
    }

    private static RuntimeException mapDuplicateKey(DuplicateKeyException ex) {
        String msg = ex.getMessage() != null ? ex.getMessage() : "";
        String lower = msg.toLowerCase();
        if (lower.contains("tax_no") || lower.contains("uk_tax_no")) {
            return exception(CUSTOMER_COMPANY_TAX_NO_EXISTS);
        }
        return exception(CUSTOMER_COMPANY_TAX_NO_EXISTS);
    }

    private static void validateStatus(Integer status) {
        if (status == null
                || (status != FinanceCustomerCompanyDO.STATUS_ENABLE
                && status != FinanceCustomerCompanyDO.STATUS_DISABLE)) {
            throw exception(CUSTOMER_COMPANY_STATUS_INVALID);
        }
    }

    /**
     * 创建时：未传角色默认仅客户；若任一侧显式传入则按布尔解析（null→false）。
     */
    private static boolean resolveIsCustomer(Boolean isCustomer, Boolean isSupplier) {
        if (isCustomer == null && isSupplier == null) {
            return true;
        }
        return Boolean.TRUE.equals(isCustomer);
    }

    private static boolean resolveIsSupplier(Boolean isCustomer, Boolean isSupplier) {
        if (isCustomer == null && isSupplier == null) {
            return false;
        }
        return Boolean.TRUE.equals(isSupplier);
    }

    private static void validateRoles(boolean isCustomer, boolean isSupplier) {
        if (!isCustomer && !isSupplier) {
            throw exception(CUSTOMER_COMPANY_ROLE_REQUIRED);
        }
    }

    private static void validateSupplierBankIfNeeded(boolean isSupplier, String bankName, String bankAccount) {
        if (!isSupplier) {
            return;
        }
        if (StrUtil.isBlank(bankName) || StrUtil.isBlank(bankAccount)) {
            throw exception(CUSTOMER_COMPANY_SUPPLIER_BANK_REQUIRED);
        }
    }

    private static String derivePartyType(boolean isCustomer, boolean isSupplier) {
        if (isCustomer && isSupplier) {
            return FinanceCustomerCompanyDO.PARTY_TYPE_BOTH;
        }
        if (isSupplier) {
            return FinanceCustomerCompanyDO.PARTY_TYPE_SUPPLIER;
        }
        return FinanceCustomerCompanyDO.PARTY_TYPE_CUSTOMER;
    }

    private static String normalizeRole(String role) {
        if (StrUtil.isBlank(role)) {
            return FinanceCustomerCompanyDO.ROLE_CUSTOMER;
        }
        String r = role.trim().toUpperCase();
        if (FinanceCustomerCompanyDO.ROLE_SUPPLIER.equals(r)) {
            return FinanceCustomerCompanyDO.ROLE_SUPPLIER;
        }
        return FinanceCustomerCompanyDO.ROLE_CUSTOMER;
    }

    private static String requireName(String name) {
        if (StrUtil.isBlank(name)) {
            throw exception(CUSTOMER_COMPANY_NAME_REQUIRED);
        }
        return name.trim();
    }

    private static String requireTaxNo(String taxNo) {
        if (StrUtil.isBlank(taxNo)) {
            throw exception(CUSTOMER_COMPANY_TAX_NO_REQUIRED);
        }
        return taxNo.trim();
    }

    private static FinanceCustomerCompanyDO buildFromSave(FinanceCustomerCompanySaveReqVO reqVO,
                                                          String name, String taxNo,
                                                          String bankName, String bankAccount) {
        return FinanceCustomerCompanyDO.builder()
                .name(name)
                .taxNo(taxNo)
                .bankName(bankName)
                .bankAccount(bankAccount)
                .address(trimToNull(reqVO.getAddress()))
                .phone(trimToNull(reqVO.getPhone()))
                .contactName(trimToNull(reqVO.getContactName()))
                .email(trimToNull(reqVO.getEmail()))
                .build();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }

}
